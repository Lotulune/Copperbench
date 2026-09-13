"""Read cached asset sizes for the exact locally selected Minecraft asset index."""
from datetime import datetime, timezone
import json
from pathlib import Path
root = Path(r'C:\Users\g7admin\.gradle\caches\fabric-loom\assets')
data = json.loads((root / 'indexes/1.21.1-17.json').read_text())
unique = {value['hash']: value['size'] for value in data['objects'].values()}
present = {}
for digest, size in unique.items():
    path = root / 'objects' / digest[:2] / digest
    if path.is_file() and path.stat().st_size == size:
        present[digest] = size
result = {'at': datetime.now(timezone.utc).isoformat(), 'index':'1.21.1-17.json',
    'expectedUniqueObjects':len(unique), 'availableUniqueObjects':len(present),
    'missingUniqueObjects':len(unique)-len(present), 'expectedBytes':sum(unique.values()),
    'availableBytes':sum(present.values())}
with Path(r'C:\Temp\S16-33ceb6e9\asset-index-progress.jsonl').open('a', encoding='utf-8') as log:
    log.write(json.dumps(result) + '\n')
print(json.dumps(result))
