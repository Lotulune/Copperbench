"""Archive the resumed, committed-candidate trials without private user state."""
from pathlib import Path
import json
import shutil

WORK = Path(r'D:\AICoding\Minecraft_ModCreator\.tmp\stage15-linux')
RUN = WORK / 'build/stage16-agent-reliability/cold-cache-c0178f6b-20260912/resumed-d9fb1458'
OUT = WORK / 'evidence/stage16/2026-09-12/cold-cache-and-storage/resumed-d9fb1458'
OUT.mkdir(exist_ok=True)

def copy_new(source, target):
    if target.exists():
        assert source.read_bytes() == target.read_bytes(), str(target)
    else:
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(source, target)

for source, label in [
    (Path(r'D:\AICoding\testmod2\s16-cold-official\evidence-final'), 'windows-official'),
    (RUN / 'windows-mirror-failure', 'windows-mirror-failure'),
    (RUN / 'ubuntu-network-failure', 'ubuntu-network-failure'),
    (RUN / 'ubuntu-evidence-final', 'ubuntu-official'),
    (RUN / 'ubuntu-screenshots', 'ubuntu-official/screenshots'),
]:
    if source.exists():
        for path in source.rglob('*'):
            if path.is_file():
                assert not path.is_symlink()
                copy_new(path, OUT / label / path.relative_to(source))

for name in [
    'source-association.json', 'host-backup-summary.json', 'host-installation.json',
    'windows-install-candidate.json', 'linux-install-candidate.json',
    'memory-before-host-upgrade.json', 'memory-before-cold-client-render.json',
    'memory-before-ubuntu-cold.json', 'Monitor-Resources.ps1',
    'memory-during-ubuntu-cold.json', 'memory-before-ubuntu-assets.json',
    'memory-before-ubuntu-render.json', 'prepare-ubuntu-cold-retry.py',
    'ubuntu-backup-summary.json', 'final-resource-state.json', 'memory-after-validation.json',
    'archive-resumed-cold.py', 'verify-resumed-cold.py',
]:
    source = RUN / name
    if source.exists():
        copy_new(source, OUT / name)

if (RUN / 'resource-monitor.stop').exists():
    copy_new(RUN / 'resource-samples.jsonl', OUT / 'resource-samples.jsonl')

print(json.dumps({'archive': str(OUT), 'files': sum(p.is_file() for p in OUT.rglob('*'))}))
