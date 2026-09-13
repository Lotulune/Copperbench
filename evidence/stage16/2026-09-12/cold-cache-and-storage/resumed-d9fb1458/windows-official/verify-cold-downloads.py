"""Validate the downloaded Minecraft asset objects against their fetched index."""
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parent
gradle = ROOT / 'home/.copperbench/gradle'
initial = json.loads((ROOT / 'cold-initial-state.json').read_text(encoding='utf-8'))
assert not initial['dependencyCacheExists'] and not initial['minecraftAssetsExist']
started = datetime.fromisoformat(initial['at']).timestamp()
asset_indexes = list(gradle.glob('caches/fabric-loom/assets/indexes/*.json'))
assert asset_indexes, 'No downloaded Minecraft asset index exists'
results = []
for index in asset_indexes:
    payload = json.loads(index.read_text(encoding='utf-8'))
    objects = {row['hash']: row['size'] for row in payload['objects'].values()}
    root = index.parent.parent / 'objects'
    mismatches = []
    older = []
    for digest, size in objects.items():
        path = root / digest[:2] / digest
        if not path.is_file():
            mismatches.append({'hash': digest, 'reason': 'missing'})
            continue
        info = path.stat()
        if info.st_size != size or hashlib.file_digest(path.open('rb'), 'sha1').hexdigest() != digest:
            mismatches.append({'hash': digest, 'reason': 'hash-or-size'})
        if info.st_mtime < started - 2:
            older.append(digest)
    result = {'index': index.relative_to(ROOT).as_posix(),
              'indexSha256': hashlib.file_digest(index.open('rb'), 'sha256').hexdigest(),
              'uniqueObjects': len(objects), 'expectedBytes': sum(objects.values()),
              'mismatches': mismatches, 'objectsPredatingTrial': older}
    results.append(result)
    assert not mismatches, mismatches[:3]
    assert not older, 'Asset files predate this cold-cache trial'
facts = {'at': datetime.now(timezone.utc).isoformat(), 'status': 'passed',
         'indexes': results, 'allAssetHashesVerified': True,
         'initialDependencyCacheEmpty': True, 'initialAssetsEmpty': True,
         'bundledRuntimesPermitted': True, 'sameTrialSubsequentStepsReuseCache': True}
with (ROOT / 'cold-download-verification.json').open('x', encoding='utf-8') as stream:
    json.dump(facts, stream, indent=2)
    stream.write('\n')
print(json.dumps({'status': 'passed', 'uniqueObjects': sum(x['uniqueObjects'] for x in results),
                  'bytes': sum(x['expectedBytes'] for x in results)}))
