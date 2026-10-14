"""Run a frozen portable product through cold/warm Native and offline acceptance.

This is an explicitly authorized integration fixture, including signed task grant
issuance through the installed code. It does not exercise desktop grant issuance,
install a deb/NSIS package, or claim Minecraft player acceptance.
"""
from __future__ import annotations

import argparse
import json
import os
from pathlib import Path
import platform as host_platform
import shutil
import subprocess
import sys
import tarfile
import time
import traceback
import uuid
import zipfile

import candidate_evidence as ce

ROOT = Path(__file__).resolve().parents[2]


def sha(path):
    return ce.sha(Path(path))


def host_identity(platform):
    identity = {'system': host_platform.system(), 'release': host_platform.release(),
                'machine': host_platform.machine(), 'python': host_platform.python_version()}
    ce.require(identity['machine'].lower() in ('amd64', 'x86_64'), 'Expected an x86_64 host')
    if platform == 'windows':
        ce.require(identity['system'] == 'Windows', 'Windows 11 is required for this cell')
        version=sys.getwindowsversion()
        ce.require(version.build >= 22000 and version.product_type == 1,
                   'Windows 11 workstation is required; Windows Server does not satisfy this cell')
        identity.update(build=version.build,productType=version.product_type)
    else:
        ce.require(identity['system'] == 'Linux', 'Ubuntu acceptance must run on Linux')
        release = host_platform.freedesktop_os_release()
        ce.require(release.get('ID') == 'ubuntu' and release.get('VERSION_ID') == '24.04',
                   'This cell requires Ubuntu 24.04')
        identity['osRelease'] = {key: release.get(key) for key in ('ID', 'VERSION_ID', 'PRETTY_NAME')}
    return identity


def extract_product(archive, destination, platform):
    """Reject traversal, duplicate members and escaping links before accepting a package."""
    ce.require(not destination.exists(), 'Choose a new installation directory')
    seen, roots = set(), set()

    def check(name, size):
        name = name.rstrip('/')
        parts = name.split('/')
        ce.require(name and '\\' not in name and ':' not in name
                   and all(part not in ('', '.', '..') for part in parts), 'Invalid package member')
        key = name.casefold() if platform == 'windows' else name
        ce.require(key not in seen, 'Duplicate package member')
        seen.add(key); roots.add(parts[0])
        ce.require(len(seen) <= 40000 and size >= 0, 'Package member limit exceeded')
        return parts

    destination.mkdir(parents=True)
    if platform == 'windows':
        with zipfile.ZipFile(archive) as bundle:
            members = bundle.infolist()
            ce.require(sum(item.file_size for item in members) <= 6 * 1024**3, 'Package size limit exceeded')
            for item in members:
                check(item.filename, item.file_size)
                ce.require((item.external_attr >> 16) & 0o170000 != 0o120000, 'Linked ZIP member')
            ce.require(len(roots) == 1, 'Expected one product root')
            bundle.extractall(destination)
    else:
        with tarfile.open(archive, 'r:gz') as bundle:
            members = bundle.getmembers()
            ce.require(sum(item.size for item in members) <= 6 * 1024**3, 'Package size limit exceeded')
            for item in members:
                check(item.name, item.size)
                ce.require(item.isfile() or item.isdir() or item.issym() or item.islnk(), 'Special TAR member')
                # Python's data filter rejects link targets outside destination.
                tarfile.data_filter(item, str(destination))
            ce.require(len(roots) == 1, 'Expected one product root')
            bundle.extractall(destination, filter='data')
    product = destination / next(iter(roots))
    ce.require(product.is_dir(), 'Product root is not a directory')
    return product, len(seen)


def prepare(candidate, destination, record_path, platform):
    metadata = ce.verify(candidate)
    archives = [a for a in metadata['assets'] if a['role'] == 'portable_package' and a['platform'] == platform]
    ce.require(len(archives) == 1, 'Candidate must contain the selected platform package')
    archive = candidate / archives[0]['path']; before = sha(archive); started = time.monotonic()
    product, count = extract_product(archive, destination, platform)
    ce.require(sha(archive) == before, 'Package changed during extraction')
    observed, asset_files, levels = {}, {}, {}
    for asset in metadata['assets']:
        role = asset['role']
        if role in ('package', 'portable_package', 'sample'):
            path = candidate / asset['path']
        elif role == 'application': path = product / 'lib/copperbench.jar'
        elif role == 'launcher': path = product / ('copperbench.exe' if platform == 'windows' else 'copperbench.sh')
        elif role == 'python_native': path = product / 'sdk/python/copperbench_native.py'
        elif role == 'python_mcp': path = product / 'sdk/python/copperbench.py'
        elif role == 'typescript': path = product / 'sdk/typescript/copperbench.ts'
        elif role == 'generator': path = product / 'plugins' / ('generator-' + asset['generatorId'] + '.zip')
        else: raise ValueError('Unknown candidate role: ' + role)
        ce.require(path.is_file() and path.stat().st_size == asset['bytes'] and sha(path) == asset['sha256'],
                   'Installed asset mismatch: ' + asset['id'])
        if role == 'sample':
            loader = asset['generatorId'].split('-')[0]
            scope_levels = {ce.scope_parts(scope)[1] for scope in metadata['scopes']
                            if ce.scope_parts(scope)[0] == loader
                            and (ce.scope_parts(scope)[2] == 'windows') == (platform == 'windows')}
            ce.require(len(scope_levels) == 1, 'Sample needs exactly one selected qualification level')
            level = next(iter(scope_levels)); levels[asset['generatorId']] = level
            inventory = ce.read(path)
            ce.require(inventory['requiredCaseManifestId'] == ('camp-kit-native-v1' if level == 'G1' else 'camp-kit-scoped-v1'),
                       'Sample manifest does not match the selected qualification level')
            sample_root = product / ('examples/camp-kit-native' if level == 'G1' else 'examples/camp-kit-scoped') / asset['generatorId']
            for item in inventory['files']:
                payload = ce.member(sample_root, item['path'])
                ce.require(payload.stat().st_size == item['bytes'] and sha(payload) == item['sha256'],
                           'Installed sample mismatch: ' + item['path'])
        observed[asset['id']] = asset['sha256']; asset_files[asset['id']] = str(path)
    executables = [('launcher', product / ('copperbench.exe' if platform == 'windows' else 'copperbench.sh')),
                   ('java', product / 'jdk/bin' / ('java.exe' if platform == 'windows' else 'java'))]
    for label, path in executables:
        ce.require(path.is_file() and os.access(path, os.X_OK), 'Missing executable: ' + label)
    record = {'status': 'prepared', 'scope': 'portable_package_extraction_and_hashes_only',
              'platform': platform, 'candidateId': metadata['candidateId'], 'productRoot': str(product),
              'observedArtifacts': observed, 'assetFiles': asset_files, 'levels': levels, 'candidatePath': str(candidate),
              'packageSha256': before, 'files': count, 'elapsedSeconds': round(time.monotonic() - started, 3),
              'installedApiAcceptance': 'not_run', 'clientAcceptance': 'not_run'}
    ce.write(record_path, record)
    return record


def successful_cold_receipt(path, prepared, generator, platform):
    prior = ce.read(path)
    ce.require(prior.get('status') == 'passed' and prior.get('cacheMode') == 'cold'
               and prior.get('initialCacheFiles') == 0 and prior.get('authorityRevoked') is True
               and prior.get('nativeResult', {}).get('status') == 'passed', 'Warm run requires a successful cold run')
    ce.require(prior.get('candidateId') == prepared['candidateId'] and prior.get('generatorId') == generator
               and prior.get('platform') == platform, 'Cold run belongs to another candidate, generator or platform')
    return prior


def verify_retained_cache(cache, inventory_path, expected_sha):
    ce.require(sha(inventory_path) == expected_sha, 'Cache inventory changed')
    inventory = ce.read(inventory_path)
    ce.require(isinstance(inventory, list) and inventory, 'Cold run has no retained cache payloads')
    seen = set()
    for item in inventory:
        ce.require(item['path'] not in seen, 'Duplicate retained cache payload')
        seen.add(item['path']); path = ce.member(cache, item['path'])
        ce.require(path.stat().st_size == item['bytes'] and sha(path) == item['sha256'],
                   'Retained cache payload changed: ' + item['path'])
    return len(inventory)


def run_environment(product, home, cache, run_root, platform, proxy_host=None, proxy_port=None):
    env = os.environ.copy()
    # Host JVM injections and home directories must not leak into the isolated run.
    for key in ('JAVA_TOOL_OPTIONS', '_JAVA_OPTIONS', 'JDK_JAVA_OPTIONS', 'JAVA_OPTS', 'GRADLE_OPTS'):
        env.pop(key, None)
    env.update(COPPERBENCH_HOME=str(home), COPPERBENCH_GRADLE_USER_HOME=str(cache), GRADLE_USER_HOME=str(cache),
               COPPERBENCH_GRADLE_REUSE_EXTERNAL='true', PYTHONPATH=str(product / 'sdk/python'))
    jvm_home = run_root / 'isolated-jvm-home'; jvm_home.mkdir()
    # The shipped Windows launcher crashes on double quotes in JVM option
    # environment variables. JVM single-quote syntax also preserves spaces.
    quote = "'" if platform == 'windows' else '"'
    ce.require(quote not in str(jvm_home), 'Choose a work root without ' + repr(quote) + ' characters')
    env['JAVA_TOOL_OPTIONS'] = '-Duser.home=' + quote + str(jvm_home) + quote
    if platform == 'ubuntu':
        env['HOME'] = str(jvm_home)
        for key, folder in [('XDG_DATA_HOME', 'data'), ('XDG_CONFIG_HOME', 'config'), ('XDG_CACHE_HOME', 'cache'),
                            ('XDG_STATE_HOME', 'state'), ('XDG_RUNTIME_DIR', 'runtime')]:
            path = jvm_home / folder; path.mkdir(mode=0o700); env[key] = str(path)
    if proxy_host is not None:
        ce.require(proxy_host and all(c.isalnum() or c in '.-' for c in proxy_host)
                   and isinstance(proxy_port, int) and 1 <= proxy_port <= 65535, 'Invalid explicit JVM proxy')
        env['JAVA_TOOL_OPTIONS'] += (f' -Dhttp.proxyHost={proxy_host} -Dhttp.proxyPort={proxy_port}'
                                    f' -Dhttps.proxyHost={proxy_host} -Dhttps.proxyPort={proxy_port}'
                                    ' -Dhttp.nonProxyHosts=localhost|127.*|[::1]')
    return env


def invoke_json(label, argv, product, env, evidence, timeout=180):
    """Keep the process failure and raw streams when no JSON receipt arrives."""
    process = {'arguments': argv}
    try:
        with (evidence / (label + '.stdout')).open('wb') as stdout, \
             (evidence / (label + '.stderr')).open('wb') as stderr:
            result = subprocess.run(argv, cwd=product, env=env, stdout=stdout, stderr=stderr, timeout=timeout)
        process.update(exitCode=result.returncode, exitHex=hex(result.returncode & 0xffffffff))
    except (OSError, subprocess.TimeoutExpired) as error:
        process.update(errorType=type(error).__name__, error=str(error))
        raise
    finally:
        ce.write(evidence / (label + '-process.json'), process)
    try:
        body = json.loads((evidence / (label + '.stdout')).read_text(encoding='utf-8-sig'))
    except (ValueError, UnicodeError) as error:
        raise RuntimeError(f'{label} returned no valid JSON receipt; exit {result.returncode} '
                           f'({process["exitHex"]}); see {evidence / (label + ".stderr")}') from error
    ce.require(isinstance(body, dict), label + ' returned a non-object receipt')
    return result.returncode, body


def snapshot_harness(candidate, evidence):
    """Record external tooling separately from the immutable product candidate."""
    frozen = {row['path']: row.get('sha256') for row in ce.read(candidate / 'source-files.json')['files']}
    directory = evidence / 'external-harness'; directory.mkdir()
    rows = []
    for name in ('tools/candidate-evidence/installed_acceptance.py',
                 'tools/candidate-evidence/candidate_evidence.py',
                 'tools/portable-evidence/copperbench_evidence.py',
                 'ui-core/scripts/validate-required-cases-evidence.mjs'):
        source = ROOT / name; target = directory / source.name; shutil.copy2(source, target)
        digest = sha(target)
        rows.append({'sourcePath': name, 'path': target.relative_to(evidence).as_posix(), 'sha256': digest,
                     'frozenSourceSha256': frozen.get(name), 'matchesFrozenSource': digest == frozen.get(name)})
    record = {'scope': 'External acceptance harness; product assets remain bound to the frozen candidate', 'files': rows}
    ce.write(evidence / 'external-harness.json', record)
    return record


def cleanup_run(record, grant, invoke, launcher, product, evidence, run_root, home, cache, platform):
    """Always collect logs and preserve an already pending primary exception."""
    primary_failed = record.get('status') == 'failed'
    failure = None
    if grant and grant.get('id'):
        try:
            code, revoked = invoke('authority-cleanup', [str(launcher), 'bootstrap', 'revoke-authorization',
                                                        '--authorization-id', grant['id']])
            ce.write(evidence / 'authority-cleanup.json', revoked)
            ce.require(code == 0, 'Installed task authorization could not be revoked')
            record['authorityRevoked'] = True
        except BaseException as error:
            failure = error
            record.update(status='failed', cleanupError=str(error))
            # Revocation only reduces this already-issued test authority. Keep a
            # launcher failure as a failed run even if the installed API recovers.
            recovery = evidence / 'RevokeGrant.java'
            recovery.write_text('''import java.nio.file.*; import java.time.*;
import dev.copperbench.automation.security.TaskAuthorizationStore;
import dev.copperbench.core.contract.UiCore;
class RevokeGrant { public static void main(String[] a) throws Exception {
  var grant = TaskAuthorizationStore.productDefault(Clock.systemUTC()).revoke(a[0], Path.of(a[1]));
  System.out.println(UiCore.wireGson().toJson(grant));
} }
''', encoding='utf-8')
            try:
                code, recovered = invoke('authority-recovery', [str(product / 'jdk/bin' / ('java.exe' if platform == 'windows' else 'java')),
                    '-cp', str(product / 'lib/*'), str(recovery), grant['id'], str(run_root / 'workspaces')])
                ce.write(evidence / 'authority-recovery.json', recovered)
                ce.require(code == 0 and recovered.get('revoked') is True and recovered.get('active') is False,
                           'Installed API revocation recovery failed')
                record.update(authorityRevoked=True, authorityRecovery='installed_api_after_launcher_failure')
            except BaseException as recovery_error:
                record['authorityRecoveryError'] = str(recovery_error)
    try:
        collect_raw_logs(evidence, run_root, home, cache, product)
    except BaseException as error:
        failure = failure or error
        record.update(status='failed', rawLogCollectionError=str(error))
    if failure is not None and not primary_failed:
        raise failure


def collect_raw_logs(evidence, run_root, home, cache, product):
    raw = evidence / 'raw-logs'; raw.mkdir(exist_ok=True)
    paths = list((home / 'logs').glob('*.log')) + list((cache / 'daemon').rglob('*.log'))
    paths += list((run_root / 'workspaces').rglob('console_output.txt')) + list(product.glob('hs_err*'))
    rows = []
    for source in paths:
        if source.is_relative_to(cache): relative = Path('gradle-cache') / source.relative_to(cache)
        else: relative = source.relative_to(run_root) if source.is_relative_to(run_root) else Path('external-log') / source.name
        target = raw / relative; target.parent.mkdir(parents=True, exist_ok=True); shutil.copy2(source, target)
        rows.append({'path': target.relative_to(raw).as_posix(), 'bytes': target.stat().st_size, 'sha256': sha(target)})
    ce.write(evidence / 'raw-log-inventory.json', rows)


def run_installed(prepared_path, generator, mode, evidence, run_root, platform, cold_receipt=None,
                  proxy_host=None, proxy_port=None, cache_root=None):
    repo=ROOT; prepared=ce.read(prepared_path); product=Path(prepared['productRoot'])
    level=prepared.get('levels',{}).get(generator,'G1')
    ce.require(level in ('G1','G2'), 'Unknown prepared qualification level')
    ce.require(generator in ('fabric-26.3','neoforge-26.3') and mode in ('cold','warm'), 'Unknown run target')
    ce.require(prepared['platform']==platform, 'Prepared product belongs to another platform')
    ce.require(not evidence.exists() and not run_root.exists(), 'Choose fresh run and evidence directories')
    identity=host_identity(platform)
    prior=successful_cold_receipt(cold_receipt,prepared,generator,platform) if mode=='warm' else None
    evidence.mkdir(parents=True);run_root.mkdir(parents=True)
    workspaces=run_root/'workspaces';workspaces.mkdir();home=run_root/'product-home'
    cache=(Path(cache_root).resolve() if cache_root else run_root/'gradle-cache') if mode=='cold' else Path(prior['gradleHome']).resolve()
    ce.require(not run_root.is_relative_to(cache) and not evidence.is_relative_to(cache)
               and not cache.is_relative_to(evidence), 'Keep the dependency cache separate from evidence and run parents')
    ce.require(mode!='warm' or cache_root is None or Path(cache_root).resolve()==cache,
               'Warm cache must be the retained successful cold cache')
    ce.require(not cache.exists() if mode=='cold' else cache.is_dir(), 'Invalid initial cache directory')
    cache.mkdir(parents=True,exist_ok=True)
    record={'status':'running','kind':'p263-installed-native-run','candidateId':prepared['candidateId'],'generatorId':generator,'cacheMode':mode,
            'productRoot':str(product),'workRoot':str(run_root),'gradleHome':str(cache),'evidence':str(evidence),
            'observedArtifacts':prepared['observedArtifacts'],'preparedProduct':str(prepared_path),'initialCacheFiles':sum(1 for p in cache.rglob('*') if p.is_file()),
            'authorityScope':'Human-authorized task, isolated signed grant fixture using installed code; desktop issuance UI is not exercised.',
            'networkCondition':('Explicit task JVM proxy '+str(proxy_host)+':'+str(proxy_port) if proxy_host else 'No JVM proxy override; runner network and inherited transport proxy conditions are recorded separately')}
    assert mode!='cold' or record['initialCacheFiles']==0
    record.update(platform=platform,host=identity,authorityRevoked=False,
                  transportProxyVariablesPresent=[key for key in ('HTTP_PROXY','HTTPS_PROXY','ALL_PROXY') if os.environ.get(key)])
    started=time.monotonic();grant=None
    launcher=product/('copperbench.exe' if platform=='windows' else 'copperbench.sh')
    env=run_environment(product,home,cache,run_root,platform,proxy_host,proxy_port)
    record.update(distributionPolicy='Reuse frozen bundled Gradle distributions; dependency cache starts empty',
                  isolatedJvmHome=str(run_root/'isolated-jvm-home'),jvmProxySettingsApplied=proxy_host is not None)
    def save(name,value):
        (evidence/(name+'.json')).write_text(json.dumps(value,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    def invoke(label,argv,timeout=180):
        return invoke_json(label,argv,product,env,evidence,timeout)
    def source_inventory(root):
        paths=[p for base in ('src','elements','gradle') for p in (root/base).rglob('*') if p.is_file()]
        paths.extend(p for p in root.iterdir() if p.is_file() and (p.suffix in ('.mcreator','.gradle','.properties') or p.name in ('gradlew','gradlew.bat')))
        return {p.relative_to(root).as_posix():sha(p) for p in sorted(paths)}
    try:
        record['externalHarness']=snapshot_harness(Path(prepared['candidatePath']),evidence)
        for identifier,path in prepared['assetFiles'].items():
            assert sha(path)==record['observedArtifacts'][identifier],identifier
            if identifier.endswith('-sample'):
                sample_generator=identifier.removesuffix('-sample')
                sample_level=prepared.get('levels',{}).get(sample_generator,'G1')
                sample_root='examples/camp-kit-native' if sample_level=='G1' else 'examples/camp-kit-scoped'
                for item in json.loads(Path(path).read_text(encoding='utf-8'))['files']:
                    payload=product/sample_root/sample_generator/item['path']
                    assert payload.stat().st_size==item['bytes'] and sha(payload)==item['sha256']
        if prior:
            inventory_path=Path(prior['cacheInventory'])
            verified=verify_retained_cache(cache,inventory_path,prior['cacheInventorySha256'])
            record.update(successfulColdReceipt=str(cold_receipt),successfulColdReceiptSha256=sha(cold_receipt),retainedPayloadsVerified=verified)
        save('result',record)
        print(json.dumps({'event':'started','evidence':str(evidence),'workRoot':str(run_root),'cacheMode':mode}),flush=True)
        code,discovery=invoke('discovery',[str(launcher),'bootstrap','list-generators']); assert code==0 and discovery['status']=='succeeded'; save('discovery',discovery)
        issuer=evidence/'IssueGrant.java'
        issuer.write_text('''import java.nio.file.*; import java.time.*; import java.util.*;
    import dev.copperbench.automation.security.TaskAuthorizationStore;
    import dev.copperbench.core.contract.UiCore;
    class IssueGrant { public static void main(String[] a) throws Exception {
      var store=TaskAuthorizationStore.productDefault(Clock.systemUTC());
      var grant=store.issue(UiCore.Actor.UI,true,"P263 installed isolated acceptance",Path.of(a[0]),List.of("create","edit","build","test"),7200,false);
      System.out.println(UiCore.wireGson().toJson(grant));
    } }
    ''',encoding='utf-8')
        code,grant=invoke('authority-fixture',[str(product/'jdk/bin'/('java.exe' if platform=='windows' else 'java')),'-cp',str(product/'lib/*'),str(issuer),str(workspaces)])
        assert code==0 and grant.get('id'),grant
        base=[str(launcher),'bootstrap','create-workspace','--generator-id','resourcepack-1.21.1','--mod-name','P263 Bootstrap','--mod-id','p263_bootstrap',
              '--workspace-folder',str(workspaces/'bootstrap'),'--no-prompt','true']
        code,denied=invoke('default-create-rejected',base); assert code==3 and denied['code']=='USER_APPROVAL_REQUIRED',denied
        code,seed=invoke('bootstrap-create',base+['--task-authorization',grant['id']],timeout=600); assert code==0 and seed['status']=='committed',seed
        # Only this empty session fixture is prepared externally. The Camp Kit target
        # is still created by the installed Native API, from an empty dependency cache.
        seed_template=json.loads(Path(seed['data']['workspaceFile']).read_text(encoding='utf-8'))
        seed_template['workspaceSettings'].update(currentGenerator=generator,modElementsPackage='dev.copperbench.seed')
        seed_template['metadata']={'files':[]}
        seed_template['dev.copperbench']={'schemaVersion':1,'workspaceId':str(uuid.uuid4()),'revision':0}
        seed_file=workspaces/'native-seed/seed.mcreator'; seed_file.parent.mkdir()
        seed_file.write_text(json.dumps(seed_template,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
        save('native-seed-fixture',{'file':str(seed_file),'sha256':sha(seed_file),'scope':'Empty external Native session fixture; no elements, generated sources, dependency artifacts or acceptance records are copied'})
        seed['data']['workspaceFile']=str(seed_file)
        sys.path.insert(0,str(product/'sdk/python')); import copperbench_native
        assert Path(copperbench_native.__file__).resolve()==product/'sdk/python/copperbench_native.py'
        old=os.environ.copy()
        os.environ.clear();os.environ.update(env)
        try:
            with copperbench_native.Workspace.open(seed['data']['workspaceFile'],launcher=launcher,cwd=product,task_authorization_id=grant['id'],startup_timeout=180,request_timeout=1800) as workspace:
                record['nativeCreationPid']=workspace._process.pid
                print(json.dumps({'event':'native-create','pid':workspace._process.pid}),flush=True)
                catalog=workspace.preview('catalog'); save('preview-catalog',catalog)
                assert any(target['generatorId']==generator and target['previewRequired'] is True
                           for target in catalog['targets']), 'Selected generator is missing from preview discovery'
                destination=workspaces/'camp-kit'
                workspace.preview('opt_in',generator_id=generator,workspace_folder_path=str(destination))
                form={'generatorId':generator,'workspaceFolderPath':str(destination),'modName':'Copperbench Camp Kit','modId':'copperbench_camp_kit','packageName':'dev.copperbench.campkit','version':'1.0.0'}
                workspace.task_authorization_id=None
                try: workspace.preview('command',generator_id=generator,command='create_workspace',payload=form)
                except copperbench_native.NativeApiError as error:
                    assert error.code=='USER_APPROVAL_REQUIRED',error.code; save('native-create-rejected',{'code':error.code,'details':error.details})
                else: raise AssertionError('Unapproved Native creation succeeded')
                workspace.task_authorization_id=grant['id']
                created=workspace.preview('command',generator_id=generator,command='create_workspace',payload=form)['core']; save('native-create',created)
                assert created['status']=='committed',created
                workspace_file=Path(created['data']['workspaceFile'])
            assert workspace._process.poll()==0
            fixture=product/('examples/camp-kit-native' if level=='G1' else 'examples/camp-kit-scoped')/generator
            for path in sorted(fixture.rglob('*')):
                if path.is_file():
                    target=destination/path.relative_to(fixture); target.parent.mkdir(parents=True,exist_ok=True); assert not target.exists(); shutil.copy2(path,target)
            if level=='G1':
                entry=destination/'src/main/java/dev/copperbench/campkit/CopperbenchCampKitMod.java'
                raw=entry.read_bytes(); marker=b'// Start of user code block mod init'; assert raw.count(marker)==1
                call=b'CampKitContent.initialize();' if generator.startswith('fabric') else b'CampKitContent.initialize(modBus);'
                entry.write_bytes(raw.replace(marker,marker+b'\n        '+call))
                save('manual-native-fixture-preparation',{'source':str(fixture),'entry':str(entry),'entrySha256':sha(entry),'scope':'External native sample authoring before the protected SDK edit/build workflow'})
            else:
                save('structured-fixture-preparation',{'source':str(fixture),'scope':'Independent host test sources only; all five mod element kinds are authored by the installed Native SDK'})
            with copperbench_native.Workspace.open(workspace_file,launcher=launcher,cwd=product,task_authorization_id=grant['id'],startup_timeout=180,request_timeout=180) as workspace:
                workspace.task_authorization_id=None
                before_read_only=source_inventory(destination); before_revision=workspace.revision
                observed=workspace.query('get_workspace_environment'); save('environment',observed)
                data=observed['data']; application=data['application']; gradle=data['execution']['gradle']
                assert application['sourceState']=='packaged_binary' and application['applicationSha256']==prepared['observedArtifacts'][platform+'-application'],application
                assert Path(gradle['userHome']).resolve()==cache and gradle['userHomeSource']=='COPPERBENCH_GRADLE_USER_HOME' and gradle['reuseExternalDistributions'] is True,gradle
                save('doctor',workspace.query('get_workspace_doctor'))
                after_read_only=source_inventory(destination)
                assert before_read_only==after_read_only and workspace.revision==before_revision
                save('read-only-check',{'status':'passed','revisionBefore':before_revision,'revisionAfter':workspace.revision,
                     'queries':['get_workspace_environment','get_workspace_doctor'],'taskAuthorizationOnQueries':None,
                     'sourceBefore':before_read_only,'sourceAfter':after_read_only})
                record['effectiveCacheVerified']=True
            assert workspace._process.poll()==0
        except BaseException:
            if 'workspace' in locals() and workspace._process is not None:
                record['nativeStageExitCode']=workspace._process.poll()
            raise
        finally:
            os.environ.clear();os.environ.update(old)
        driver=product/'sdk/python/native_263_acceptance.py'; text=driver.read_text(encoding='utf-8')
        marker='cwd=config["cwd"], capture_output=True'
        assert text.count(marker)==1
        active=evidence/'native_263_acceptance.py'; active.write_text(text.replace(marker,'cwd=config["schemaValidatorRoot"], capture_output=True'),encoding='utf-8')
        record.update(bundledHarnessSha256=sha(driver),activeHarnessSha256=sha(active),externalSchemaValidationRoot=str(repo),workspace=str(workspace_file))
        config={'workspace':str(workspace_file),'generatorId':generator,'level':level,'evidence':str(evidence),'cwd':str(product),'launcher':[str(launcher)],'taskAuthorizationId':grant['id'],'schemaValidatorRoot':str(repo)}
        config_path=evidence/'config.json'; config_path.write_text(json.dumps(config,indent=2)+'\n',encoding='utf-8')
        save('installed-run',record)
        with (evidence/'native-driver.log').open('wb') as log:
            completed=subprocess.run([sys.executable,str(active),str(config_path)],cwd=product,env=env,stdout=log,stderr=subprocess.STDOUT,timeout=3600)
        record['nativeDriverExitCode']=completed.returncode
        native=json.loads((evidence/'result.json').read_text(encoding='utf-8'))
        assert completed.returncode==0 and native['status']=='passed',native
        record['nativeResult']=native; record['status']='passed'
        selected_current=json.loads((evidence/'verified-export.json').read_text(encoding='utf-8'))['data']['task']['verifiedExport']
        selected_historical=json.loads((evidence/'explicit-historical.json').read_text(encoding='utf-8'))['data']['task']['verifiedExport']
        assert all(selected_current[key]==selected_historical[key] for key in ('taskId','artifactSha256','sourceSha256'))
        selected_artifacts={key:value for key,value in prepared['observedArtifacts'].items() if not key.endswith(('-generator','-sample')) or key.startswith(generator+'-')}
        save('run-binding',{'candidateId':prepared['candidateId'],'cell':'install:'+platform+':'+generator.split('-')[0],
             'generatorId':generator,'level':level,'observedArtifacts':selected_artifacts,'artifactSha256':selected_current['artifactSha256'],
             'selectedAcceptanceTaskId':selected_current['taskId'],'cacheMode':mode,'nativeResultSha256':sha(evidence/'result.json')})
        retained=[]
        for relative in ('wrapper/dists','caches/modules-2/files-2.1','caches/fabric-loom/26.3','caches/fabric-loom/minecraftMaven','caches/neoformruntime'):
            for path in sorted((cache/relative).rglob('*')):
                if path.is_file() and path.suffix not in ('.lck','.part'):
                    retained.append({'path':path.relative_to(cache).as_posix(),'bytes':path.stat().st_size,'sha256':sha(path)})
        save('cache-inventory',retained)
        record.update(cacheInventory=str(evidence/'cache-inventory.json'),cacheInventorySha256=sha(evidence/'cache-inventory.json'),retainedPayloadCount=len(retained))
    except BaseException as error:
        record.update(status='failed',error=str(error),traceback=traceback.format_exc())
        if hasattr(error,'code'): record.update(errorCode=error.code,errorDetails=error.details)
        raise
    finally:
        try:
            cleanup_run(record,grant,invoke,launcher,product,evidence,run_root,home,cache,platform)
        finally:
            record['elapsedSeconds']=round(time.monotonic()-started,3); save('installed-run',record)
        print(json.dumps({key:record[key] for key in ('status','generatorId','cacheMode','gradleHome','evidence','elapsedSeconds')},indent=2))

    return evidence


def collect_portable(run):
    record=ce.read(run/'installed-run.json')
    ce.require(record['status']=='passed' and record['authorityRevoked'] is True,'Cannot collect a failed run')
    product=Path(record['productRoot']); output=run/'portable-check'; output.mkdir()
    clean=Path(record['workRoot'])/'offline-review'; clean.mkdir()
    source=product/'tools/portable-evidence/copperbench_evidence.py'; verifier=clean/'copperbench_evidence.py'; shutil.copy2(source,verifier)
    assert sha(source)==sha(verifier)
    env=os.environ.copy();env.pop('PYTHONPATH',None)
    commands=[]
    def execute(label,args,expected):
        command=[sys.executable,str(verifier),*args]
        result=subprocess.run(command,cwd=clean,env=env,capture_output=True,timeout=60)
        (output/(label+'.stdout')).write_bytes(result.stdout);(output/(label+'.stderr')).write_bytes(result.stderr)
        value=json.loads(result.stdout.decode('utf-8-sig'));commands.append({'label':label,'arguments':command,'cwd':str(clean),'exitCode':result.returncode,'result':value})
        assert result.returncode==expected,(label,result.returncode,value)
        return value
    result={'status':'running','verifierSha256':sha(verifier),'verifierSource':str(source),'cleanDirectory':str(clean),'commands':commands}
    try:
        identities=[]
        for kind,label,scope in (('current','verified-export','current_at_export'),('historical','explicit-historical','historical_at_export')):
            original=json.loads((run/(label+'.json')).read_text(encoding='utf-8'))['data']['task']['verifiedExport']
            bundle=clean/('portable-'+kind)
            execute('collect-'+kind,['collect',original['exportDirectory'],str(bundle)],0)
            archive=clean/('portable-'+kind+'.zip')
            with zipfile.ZipFile(archive,'w',compression=zipfile.ZIP_DEFLATED) as stream:
                for path in sorted(bundle.iterdir()):assert path.is_file();stream.write(path,path.name)
            verified=execute('verify-'+kind,['verify',str(archive)],0)
            assert verified['status']=='integrity_verified' and verified['identity']['inputScope']==scope
            identities.append((verified['identity']['taskId'],verified['identity']['sourceSha256']))
            shutil.copy2(archive,output/archive.name)
        assert identities[0]==identities[1]
        negative=clean/'tampered.zip'
        with zipfile.ZipFile(clean/'portable-current.zip') as original,zipfile.ZipFile(negative,'w',compression=zipfile.ZIP_DEFLATED) as changed:
            for name in original.namelist():changed.writestr(name,original.read(name)+(b'controlled tamper' if name=='verified-mod.jar' else b''))
        rejected=execute('verify-tampered',['verify',str(negative)],2)
        assert rejected['status']=='failed'
        shutil.copy2(negative,output/negative.name)
        result['status']='passed'
    finally:
        (output/'offline-result.json').write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    print(json.dumps({'status':result['status'],'path':str(output),'selectedTaskId':identities[0][0]}))


def verify_native_reconnect(native, level):
    ce.require(level in ('G1','G2'), 'Unknown Native qualification level')
    expected = 3 if level == 'G1' else 4
    sessions = native['sessions']
    ce.require(len(sessions) == expected and len({s['pid'] for s in sessions}) == expected
               and all(s.get('exitCode') == 0 for s in sessions),
               'Native qualification requires distinct, cleanly closed processes for every phase')


def finalize_cell(cold,warm,out):
    ce.require(not out.exists(),'Choose a new cell directory');out.mkdir()
    read=lambda path:json.loads(path.read_text(encoding='utf-8'))
    c=read(cold/'installed-run.json');w=read(warm/'installed-run.json')
    assert c['status']==w['status']=='passed' and c['authorityRevoked'] and w['authorityRevoked']
    assert c['cacheMode']=='cold' and c['initialCacheFiles']==0 and w['cacheMode']=='warm' and w['retainedPayloadsVerified']>0
    assert w['successfulColdReceiptSha256']==ce.sha(cold/'installed-run.json')
    assert c['candidateId']==w['candidateId'] and c['generatorId']==w['generatorId'] and c['gradleHome']==w['gradleHome']
    binding=read(cold/'run-binding.json');warm_binding=read(warm/'run-binding.json')
    case_label='six-case-acceptance' if binding['level']=='G1' else 'thirteen-case-acceptance'
    assert binding['level']==warm_binding['level']
    assert binding['artifactSha256']==warm_binding['artifactSha256']
    prepared=read(Path(c['preparedProduct']));candidate=Path(prepared['candidatePath']);metadata=ce.verify(candidate)
    assert metadata['candidateId']==c['candidateId']
    assert read(cold/'portable-check/offline-result.json')['status']==read(warm/'portable-check/offline-result.json')['status']=='passed'
    required_negatives={'no-opt-in','old-revision','controlled-compile-failure','jar-tamper','xml-tamper','duplicate-current','external-source-conflict','duplicate-historical','changed-input','sdk-timeout','required-case-missing','missing-export'}
    for folder,r in ((cold,c),(warm,w)):
        native=read(folder/'result.json');assert native['status']=='passed'
        assert required_negatives.issubset({g['label'] for g in native['negativeGates']})
        verify_native_reconnect(native,binding['level'])
        assert read(folder/'read-only-check.json')['status']=='passed'
        assert read(folder/'native-create.json')['status']=='committed'
        assert read(folder/'native-create-rejected.json')['code']=='USER_APPROVAL_REQUIRED'
        assert read(folder/'default-create-rejected.stdout')['code']=='USER_APPROVAL_REQUIRED'
        findings={check['id']:check['status'] for check in read(folder/'doctor.json')['data']['findings']}
        assert all(findings.get(key)=='available' for key in ('product_java','workspace_java','wrapper_runtime','wrapper'))

    files=[]
    def add(identifier,role,source):
        target=out/(identifier+source.suffix);shutil.copy2(source,target)
        files.append({'id':identifier,'role':role,'path':target.name,'bytes':target.stat().st_size,'sha256':ce.sha(target)})
        return identifier

    for prefix,folder in (('cold',cold),('warm',warm)):
        add(prefix+'-external-harness','original_receipt',folder/'external-harness.json')
        add(prefix+'-installed-run','cache_conditions' if prefix=='cold' else 'supporting_run',folder/'installed-run.json')
        add(prefix+'-native-result','native_result' if prefix=='cold' else 'supporting_run',folder/'result.json')
        add(prefix+'-doctor','doctor' if prefix=='cold' else 'supporting_run',folder/'doctor.json')
        add(prefix+'-binding','run_binding' if prefix=='cold' else 'supporting_run',folder/'run-binding.json')
        add(prefix+'-offline-result','negative_result' if prefix=='cold' else 'supporting_run',folder/'portable-check/offline-result.json')
        for kind in ('current','historical'):
            add(prefix+'-portable-'+kind,'portable_'+kind if prefix=='cold' else 'supporting_portable',folder/'portable-check'/('portable-'+kind+'.zip'))
        for label in ('environment','native-create','native-create-rejected','read-only-check','initial-build','controlled-compile-failure','repaired-build',
                      case_label,'verified-export','reopened-export','explicit-historical','final-reopened-export','required-case-missing-proof',
                      'duplicate-current','duplicate-historical','jar-tamper','xml-tamper','external-source-conflict','changed-input','missing-export','cache-inventory'):
            add(prefix+'-'+label,'original_receipt',folder/(label+'.json'))
        add(prefix+'-default-create-rejected','original_receipt',folder/'default-create-rejected.stdout')
        if binding['level']=='G2':
            for source in sorted(folder.glob('scoped-*.json')):
                add(prefix+'-'+source.stem,'original_receipt',source)
    add('prepared-product','identity',Path(c['preparedProduct']))

    check_ids={
        'installed_identity':['prepared-product','cold-environment','cold-doctor','cold-binding'],
        'native_create':['cold-native-create','warm-native-create'],
        'build_failure_repair':['cold-initial-build','cold-controlled-compile-failure','cold-repaired-build','warm-native-result'],
        'required_cases':['cold-'+case_label,'cold-required-case-missing-proof','warm-native-result'],
        'verified_export':['cold-verified-export','cold-explicit-historical','cold-portable-current','cold-portable-historical'],
        'offline_verification':['cold-offline-result','warm-offline-result','cold-portable-current','cold-portable-historical'],
        'process_reconnect':['cold-native-result','cold-final-reopened-export','warm-native-result'],
        'negative_evidence':['cold-native-result','cold-offline-result','cold-duplicate-current','cold-duplicate-historical','cold-required-case-missing-proof'],
        'read_only':['cold-read-only-check','cold-native-create-rejected','cold-default-create-rejected','warm-read-only-check'],
        'cache_conditions':['cold-installed-run','warm-installed-run','cold-cache-inventory','warm-cache-inventory'],
    }
    if binding['level']=='G2':
        for kind in ('item','block','recipe','function','code'):
            check_ids[kind]=['cold-scoped-contract-'+kind,'cold-scoped-elements',
                             'cold-thirteen-case-acceptance','warm-native-result']
        check_ids['thirteen_required_cases']=check_ids['required_cases']
    receipt={k:binding[k] for k in ('candidateId','cell','generatorId','level','observedArtifacts','artifactSha256')}
    receipt.update(schemaVersion='1',kind='candidate-cell-evidence',status='passed',
                   checks={k:{'status':'passed','evidenceIds':v} for k,v in check_ids.items()},evidence=files,
                   installationMode='frozen portable ZIP or tar in a fresh isolated directory',
                   limitations=[c['networkCondition'],
                                'Authorization uses the installed TaskAuthorizationStore fixture; desktop issuance UI not exercised',
                                'No player/client acceptance; dirty source cannot qualify for support'])
    ce.write(out/'cell.json',receipt)
    evaluated=ce.evaluate(candidate,[out/'cell.json']);ce.write(out/'evaluation.json',evaluated)
    assert next(r for r in evaluated['cells'] if r['cell']==receipt['cell'])['status']=='passed'
    print(json.dumps({'status':'passed','cell':receipt['cell'],'path':str(out/'cell.json'),'artifactSha256':receipt['artifactSha256'],'formalSupportClaim':False}))
    return out/'cell.json'

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--candidate',type=Path,required=True)
    parser.add_argument('--platform',choices=('windows','ubuntu'),required=True)
    parser.add_argument('--loader',choices=ce.LOADERS,required=True)
    parser.add_argument('--output',type=Path,required=True)
    parser.add_argument('--work-root',type=Path,required=True)
    parser.add_argument('--cache-root',type=Path,help='Optional fresh cold dependency cache on a separate volume')
    parser.add_argument('--jvm-proxy-host')
    parser.add_argument('--jvm-proxy-port',type=int)
    args=parser.parse_args()
    ce.require(__debug__,'Do not run acceptance with Python assertions disabled')
    host_identity(args.platform)
    ce.require((args.jvm_proxy_host is None)==(args.jvm_proxy_port is None),'Provide both explicit proxy host and port')
    output=args.output.resolve();work=args.work_root.resolve();candidate=args.candidate.resolve()
    ce.detached_output(ROOT,output)
    ce.require(not output.exists() and not work.exists(),'Choose fresh output and work directories')
    ce.require(not work.is_relative_to(output) and not output.is_relative_to(work),
               'Keep transient work separate from retained evidence')
    output.mkdir(parents=True);work.mkdir(parents=True)
    result={'status':'running','platform':args.platform,'generatorId':args.loader+'-26.3',
            'clientAcceptance':'not_run','formalSupportClaim':False,'source':'installed_product'}
    try:
        prepared_path=output/'prepared-product.json'
        prepared=prepare(candidate,work/'installation',prepared_path,args.platform)
        result['candidateId']=prepared['candidateId']
        for mode in ('cold','warm'):
            run=run_installed(prepared_path,args.loader+'-26.3',mode,output/mode,work/mode,args.platform,
                              output/'cold/installed-run.json' if mode=='warm' else None,
                              args.jvm_proxy_host,args.jvm_proxy_port,args.cache_root)
            collect_portable(run)
        cell=finalize_cell(output/'cold',output/'warm',output/'cell')
        result.update(status='passed',cell=str(cell),cellSha256=sha(cell))
    except BaseException as error:
        result.update(status='failed',error=str(error),traceback=traceback.format_exc())
        raise
    finally:
        ce.write(output/'gate-result.json',result)
    return 0


if __name__=='__main__':
    sys.exit(main())
