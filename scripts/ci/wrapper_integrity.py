"""Verify current wrapper inputs and exercise real cold, rejected and warm downloads.

The live gate uses isolated, dependency-free Java projects. It proves wrapper/cache
behavior, not product release or Minecraft acceptance. It never edits source wrappers.
"""
from __future__ import annotations

import argparse
from functools import partial
import hashlib
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import tempfile
import threading
import time
import zipfile

ROOT = Path(__file__).resolve().parents[2]
MANIFEST = ROOT/'src/main/resources/dev/copperbench/generator/gradle-integrity.properties'
SOURCES = {'official': 'https://services.gradle.org/distributions/',
           'mirror': 'https://mirrors.huaweicloud.com/gradle/'}


def properties(path: Path):
    return dict(line.split('=', 1) for line in path.read_text(encoding='utf-8').splitlines()
                if line.strip() and not line.lstrip().startswith('#') and '=' in line)


def digest(path: Path):
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def inventory(root=ROOT, manifest=MANIFEST):
    trusted = properties(manifest)
    wrappers = [root/'gradle/wrapper/gradle-wrapper.properties']
    wrappers += sorted((root/'plugins').glob('*/**/workspacebase/gradle/wrapper/gradle-wrapper.properties'))
    wrappers += sorted((root/'examples/agent-native').glob('*/gradle/wrapper/gradle-wrapper.properties'))
    rows = []
    for path in wrappers:
        config = properties(path)
        url = config['distributionUrl'].replace('\\:', ':')
        archive = url.rsplit('/', 1)[-1]
        expected = trusted.get('distribution.' + archive)
        if not expected or not re.fullmatch('[0-9a-f]{64}', expected):
            raise ValueError(f'Unreviewed distribution: {path}')
        if config.get('distributionSha256Sum') != expected:
            raise ValueError(f'Missing or incorrect distribution checksum: {path}')
        if url not in [base+archive for base in SOURCES.values()]:
            raise ValueError(f'Unreviewed distribution source: {path}')
        jar = path.with_name('gradle-wrapper.jar')
        jar_hash = digest(jar)
        jar_versions = [key.removeprefix('wrapper.') for key, value in trusted.items()
                        if key.startswith('wrapper.') and value == jar_hash]
        if not jar_versions:
            raise ValueError(f'Unreviewed wrapper JAR: {jar}')
        rows.append({'path': path.relative_to(root).as_posix(), 'archive': archive, 'url': url,
                     'sha256': expected, 'jarSha256': jar_hash, 'jarVersion': jar_versions[0]})
    return rows


def prepare(project: Path, jar: Path):
    (project/'gradle/wrapper').mkdir(parents=True)
    shutil.copy2(jar, project/'gradle/wrapper/gradle-wrapper.jar')
    (project/'settings.gradle').write_text("rootProject.name = 'wrapper-cache-gate'\n", encoding='utf-8')
    (project/'build.gradle').write_text("""plugins { id 'java' }
tasks.register('verifyGate', JavaExec) {
    dependsOn classes
    classpath = sourceSets.main.runtimeClasspath
    mainClass = 'CacheGate'
}
tasks.named('check') { dependsOn 'verifyGate' }
""", encoding='utf-8')
    source = project/'src/main/java/CacheGate.java'
    source.parent.mkdir(parents=True)
    source.write_text('public class CacheGate { public static void main(String[] args) { '
                      'if (6 * 7 != 42) throw new AssertionError(); '
                      'System.out.println("WRAPPER_CACHE_GATE_EXECUTED"); } }\n', encoding='utf-8')


def execute(project: Path, home: Path, url: str, expected: str, log: Path, *, cold: bool, rejected=False):
    if cold and home.exists():
        raise ValueError('Cold cache directory already exists: ' + str(home))
    properties_file = project/'gradle/wrapper/gradle-wrapper.properties'
    properties_file.write_text(
        f'distributionUrl={url}\ndistributionSha256Sum={expected}\n'
        'distributionBase=GRADLE_USER_HOME\ndistributionPath=wrapper/dists\n'
        'zipStoreBase=GRADLE_USER_HOME\nzipStorePath=wrapper/dists\nnetworkTimeout=60000\n',
        encoding='utf-8')
    executable = Path(os.environ['JAVA_HOME'])/'bin'/('java.exe' if os.name == 'nt' else 'java')
    argv = [str(executable), '-classpath', str(project/'gradle/wrapper/gradle-wrapper.jar'),
            'org.gradle.wrapper.GradleWrapperMain', '--no-daemon', '--no-build-cache',
            '--console=plain', 'clean', 'build']
    if not cold:
        argv.append('--offline')
    env = {**os.environ, 'GRADLE_USER_HOME': str(home)}
    started = time.monotonic()
    timed_out = False
    with log.open('wb') as output:
        try:
            code = subprocess.run(argv, cwd=project, env=env, stdout=output,
                                  stderr=subprocess.STDOUT, timeout=600).returncode
        except subprocess.TimeoutExpired:
            timed_out, code = True, 124
    content = log.read_text(encoding='utf-8', errors='replace')
    artifact = project/'build/libs/wrapper-cache-gate.jar'
    if rejected:
        passed = (code != 0 and not timed_out
                  and 'Verification of Gradle distribution failed' in content
                  and expected in content and 'WRAPPER_CACHE_GATE_EXECUTED' not in content
                  and not artifact.exists())
    else:
        passed = code == 0 and 'WRAPPER_CACHE_GATE_EXECUTED' in content and artifact.is_file()
        if passed:
            with zipfile.ZipFile(artifact) as jar:
                passed = 'CacheGate.class' in jar.namelist()
    return {'status': 'passed' if passed else 'failed', 'exitCode': code, 'timedOut': timed_out,
            'coldCache': cold, 'offline': not cold, 'source': url, 'expectedSha256': expected,
            'command': argv, 'gradleUserHome': str(home), 'elapsedSeconds': round(time.monotonic()-started, 3),
            'log': log.parent.name+'/'+log.name, 'logSha256': digest(log),
            'artifactSha256': digest(artifact) if artifact.is_file() else None}


class QuietHandler(SimpleHTTPRequestHandler):
    def log_message(self, *_):
        pass


def live(output: Path):
    rows = inventory()
    output = output.resolve()
    output.mkdir(parents=True, exist_ok=True)
    run = Path(tempfile.mkdtemp(prefix='cache-run-', dir=output))
    combinations = {(row['archive'], row['jarSha256']): row for row in rows}
    results = []
    # A deliberately altered payload over loopback exercises the real wrapper's
    # digest rejection without depending on a second public download failure.
    served = run/'tampered'
    served.mkdir()
    server = ThreadingHTTPServer(('127.0.0.1', 0), partial(QuietHandler, directory=str(served)))
    worker = threading.Thread(target=server.serve_forever, daemon=True)
    worker.start()
    try:
        for index, row in enumerate(combinations.values()):
            case = run/f"{index}-{row['archive'].removesuffix('-bin.zip')}-wrapper-{row['jarVersion']}"
            case.mkdir()
            jar = (ROOT/row['path']).with_name('gradle-wrapper.jar')
            for source, base in SOURCES.items():
                project = case/source
                prepare(project, jar)
                result = execute(project, case/(source+'-home'), base+row['archive'], row['sha256'],
                                 case/(source+'.log'), cold=True)
                results.append({'case': 'cold-'+source, **row, **result})
                if source == 'official' and result['status'] == 'passed':
                    warm = execute(project, case/(source+'-home'), base+row['archive'], row['sha256'],
                                   case/'warm.log', cold=False)
                    results.append({'case': 'warm-offline-build', **row, **warm})
                elif source == 'official':
                    results.append({'case': 'warm-offline-build', **row, 'status': 'failed',
                                    'reason': 'Cold official download/build failed; warm build was not attempted.'})
            tampered = served/f"{index}-{row['archive']}"
            with zipfile.ZipFile(tampered, 'w') as archive:
                archive.writestr('deliberately-altered.txt', 'digest must reject this payload')
            bad_project = case/'wrong-digest'
            prepare(bad_project, jar)
            url = f'http://127.0.0.1:{server.server_port}/'+tampered.name
            result = execute(bad_project, case/'wrong-digest-home', url, row['sha256'],
                             case/'wrong-digest.log', cold=True, rejected=True)
            results.append({'case': 'wrong-digest', 'payload': 'deliberately altered loopback ZIP',
                            'payloadPath': tampered.relative_to(run).as_posix(),
                            'payloadSha256': digest(tampered), **row, **result})
            save_results(run, results)
    finally:
        server.shutdown()
        server.server_close()
        worker.join(timeout=5)
        save_results(run, results)
    expected_cases = len(combinations)*4
    passed = len(results) == expected_cases and all(row['status'] == 'passed' for row in results)
    receipt = {'schemaVersion': 1, 'status': 'passed' if passed else 'failed',
               'expectedCases': expected_cases, 'observedCases': len(results),
               'scope': 'wrapper distribution/cache verification, not a product release build',
               'results': str(run/'results.json')}
    (output/'result.json').write_text(json.dumps(receipt, indent=2)+'\n', encoding='utf-8')
    print(json.dumps(receipt, indent=2))
    return 0 if passed else 1


def save_results(run, results):
    (run/'results.json').write_text(json.dumps(results, indent=2)+'\n', encoding='utf-8')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    actions = parser.add_subparsers(dest='action', required=True)
    actions.add_parser('check')
    command = actions.add_parser('live')
    command.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    if args.action == 'check':
        print(json.dumps({'schemaVersion': 1, 'wrappers': inventory()}, indent=2))
        return 0
    return live(args.output)


if __name__ == '__main__':
    raise SystemExit(main())
