import argparse
import hashlib
import json
import re
from datetime import datetime, timezone
from pathlib import Path

p = argparse.ArgumentParser()
p.add_argument('--platform', choices=['windows', 'linux'], required=True)
p.add_argument('--source', required=True)
p.add_argument('--release', type=Path, required=True)
p.add_argument('--payload', type=Path, required=True)
p.add_argument('--result', type=Path, required=True)
args = p.parse_args()
release = json.loads(args.release.read_text(encoding='utf-8-sig'))
tag = 'v0.1.17' if args.platform == 'windows' else 'v0.1.17-linux-stable'
assert release['tag_name'] == tag and not release['draft'] and not release['prerelease']
assets = {a['name']: a for a in release['assets']}
assert len(assets) == len(release['assets'])

def identity(path):
    digest = hashlib.sha256()
    with path.open('rb') as stream:
        for part in iter(lambda: stream.read(1024 * 1024), b''):
            digest.update(part)
    return {'name': path.name, 'size': path.stat().st_size, 'sha256': digest.hexdigest()}

expected = []
for file in sorted(args.payload.iterdir()):
    if not file.is_file():
        continue
    item = identity(file)
    public_name = file.name.replace(' ', '.')
    remote = assets[public_name]
    assert remote['size'] == item['size'], public_name
    assert remote['digest'] == 'sha256:' + item['sha256'], public_name
    expected.append({**item, 'publicName': public_name, 'url': remote['browser_download_url']})
assert {x['publicName'] for x in expected} == set(assets), 'Unexpected or missing release files'

if args.platform == 'windows':
    metadata = json.loads((args.payload / 'RELEASE-METADATA.json').read_text(encoding='utf-8-sig'))
    assert metadata['tag'] == tag and metadata['commit'] == args.source
    assert metadata['binarySource']['commit'] == args.source
    assert len(expected) == 15
    listed = {}
    for line in (args.payload / 'SHA256SUMS.txt').read_text(encoding='utf-8-sig').splitlines():
        match = re.fullmatch(r'([0-9a-fA-F]{64})  (.+)', line)
        assert match, line
        name = match[2].replace(' ', '.')
        assert name not in listed
        listed[name] = match[1].lower()
    assert set(listed) == set(assets) - {'SHA256SUMS.txt'}
    assert all(assets[name]['digest'] == 'sha256:' + sha for name, sha in listed.items())
    status = json.loads((args.payload / 'product-status.json').read_text(encoding='utf-8-sig'))
    assert status['product']['version'] == '0.1.17'
    source_details = {'sourceCommit': args.source, 'workflowRunId': metadata['workflowRun']}
else:
    metadata = json.loads((args.payload / 'LINUX-CANDIDATE-METADATA.json').read_text(encoding='utf-8-sig'))
    authorization = json.loads((args.payload / 'LINUX-RELEASE-AUTHORIZATION.json').read_text(encoding='utf-8-sig'))
    assert metadata['source']['commit'] == args.source
    assert authorization['candidateSourceCommit'] == args.source and authorization['releaseTag'] == tag
    assert authorization['candidateId'] == metadata['candidateId']
    assert len(expected) == 18
    source_details = {'binarySourceCommit': args.source, 'candidateId': metadata['candidateId'],
                      'candidateWorkflowRunId': metadata['source']['workflowRunId']}

descriptor = (args.payload / f'copperbench-update-{args.platform}-x64.properties').read_text(encoding='utf-8-sig')
properties = dict(line.split('=', 1) for line in descriptor.splitlines() if line and not line.startswith('#') and '=' in line)
assert properties['version'] == '0.1.17'
components = []
for index in range(int(properties['parts'])):
    prefix = f'part.{index}.'
    name = properties[prefix + 'name']
    assert assets[name]['digest'] == 'sha256:' + properties[prefix + 'sha256']
    assert assets[name]['size'] == int(properties[prefix + 'bytes'])
    components.append(name)
assert components

result = {'schemaVersion': 1, 'status': 'passed', 'platform': args.platform, 'updateComponents': components,
          'verifiedAt': datetime.now(timezone.utc).isoformat(), 'releaseTag': tag,
          'releaseId': release['id'], 'releaseUrl': release['html_url'], 'publishedAt': release['published_at'],
          'assetCount': len(expected), 'assets': expected, **source_details,
          'scope': 'Every local payload byte hash and size matches the public GitHub asset digest; workflow provenance is recorded separately.'}
args.result.parent.mkdir(parents=True, exist_ok=True)
args.result.write_text(json.dumps(result, indent=2) + '\n', encoding='utf-8')
print(json.dumps({k: result[k] for k in ['status', 'platform', 'releaseTag', 'assetCount']}))
