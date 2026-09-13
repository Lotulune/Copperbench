"""Record cache contents and file ages without reading account data."""
from datetime import datetime, timezone
import json
import os
from pathlib import Path
import stat
import sys

ROOT = Path(__file__).resolve().parent
label = sys.argv[1]
assert label.replace('-', '').replace('_', '').isalnum()
folders = {'gradle': ROOT / 'home/.copperbench/gradle',
           'workspaceGradle': ROOT / 'workspace/.gradle',
           'workspaceRun': ROOT / 'workspace/run',
           'isolatedDefaultGradle': ROOT / 'home/.gradle'}
facts = {}
for name, folder in folders.items():
    rows = []
    for base, dirs, files in os.walk(folder, followlinks=False):
        for file in files:
            path = Path(base, file)
            try:
                info = path.stat(follow_symlinks=False)
                if getattr(info, 'st_file_attributes', 0) & getattr(stat, 'FILE_ATTRIBUTE_REPARSE_POINT', 1024):
                    continue
                rows.append({'path': path.relative_to(folder).as_posix(), 'bytes': info.st_size,
                             'modifiedNs': info.st_mtime_ns})
            except FileNotFoundError:
                continue
    facts[name] = {'exists': folder.exists(), 'files': len(rows), 'bytes': sum(r['bytes'] for r in rows),
                   'entries': sorted(rows, key=lambda r: r['path'])}
output = {'at': datetime.now(timezone.utc).isoformat(), 'label': label, 'folders': facts}
target = ROOT / ('cache-' + label + '.json')
with target.open('x', encoding='utf-8') as stream:
    json.dump(output, stream, indent=2)
    stream.write('\n')
print(json.dumps({name: {key: value for key, value in fact.items() if key != 'entries'}
                  for name, fact in facts.items()}))
