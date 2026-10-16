import argparse
import hashlib
import json
import subprocess
from pathlib import Path

p = argparse.ArgumentParser()
p.add_argument('--payload', type=Path, required=True)
p.add_argument('--metadata', required=True)
p.add_argument('--workflow', required=True)
p.add_argument('--source-ref', required=True)
p.add_argument('--source', required=True)
p.add_argument('--run', required=True)
p.add_argument('--attempt', default='1')
p.add_argument('--output', type=Path, required=True)
args = p.parse_args()
args.output.mkdir(parents=True, exist_ok=True)
command = ['gh', 'attestation', 'verify', str(args.payload / args.metadata), '--repo', 'Lotulune/Copperbench',
           '--source-digest', args.source, '--source-ref', args.source_ref,
           '--signer-workflow', 'Lotulune/Copperbench/.github/workflows/' + args.workflow, '--format', 'json']
checked = subprocess.run(command, capture_output=True, timeout=180)
(args.output / 'verified-bundle.json').write_bytes(checked.stdout)
(args.output / 'verification.stderr.log').write_bytes(checked.stderr)
assert checked.returncode == 0, f'Attestation verifier exited {checked.returncode}; inspect verification.stderr.log'
bundles = json.loads(checked.stdout)
expected_invocation = f'https://github.com/Lotulune/Copperbench/actions/runs/{args.run}/attempts/{args.attempt}'
statements = []
for item in bundles:
    s = item['verificationResult']['statement']
    if s['predicateType'] != 'https://slsa.dev/provenance/v1':
        continue
    pred = s['predicate']
    if pred['runDetails']['metadata']['invocationId'] != expected_invocation:
        continue
    definition = pred['buildDefinition']
    wf = definition['externalParameters']['workflow']
    if wf != {'path': '.github/workflows/' + args.workflow, 'ref': args.source_ref,
              'repository': 'https://github.com/Lotulune/Copperbench'}:
        continue
    assert any(x.get('digest', {}).get('gitCommit') == args.source for x in definition['resolvedDependencies'])
    statements.append(s)
assert statements, 'No verified statement matched the exact source/run/attempt/workflow'
subjects = {item['digest']['sha256'] for s in statements for item in s['subject']}
files = []
for path in sorted(args.payload.iterdir()):
    if not path.is_file():
        continue
    digest = hashlib.sha256()
    with path.open('rb') as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b''):
            digest.update(chunk)
    sha = digest.hexdigest()
    assert sha in subjects, 'Payload is not covered by the verified statement: ' + path.name
    files.append({'name': path.name, 'size': path.stat().st_size, 'sha256': sha})
result = {'status': 'passed', 'sourceCommit': args.source, 'sourceRef': args.source_ref,
          'workflow': args.workflow, 'runId': args.run, 'attempt': args.attempt,
          'verifiedStatementCount': len(statements), 'files': files,
          'scope': 'Verified SLSA statement signature using metadata subject; every payload byte digest is covered by that same verified statement.'}
(args.output / 'provenance-verification.json').write_text(json.dumps(result, indent=2) + '\n', encoding='utf-8')
print(json.dumps({'status': 'passed', 'files': len(files), 'runId': args.run}))
