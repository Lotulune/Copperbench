from datetime import datetime, timezone
import hashlib
import json
import os
from pathlib import Path
ROOT = Path(__file__).resolve().parent
os.umask(0o077)
assert os.getuid() == 1000
assert ROOT == Path('/home/stage15/s16-cold-fixed')
data = ROOT / 'home/.copperbench'
gradle = data / 'gradle'
assert not (ROOT / 'home').exists()
assert not (ROOT / 'workspace').exists()
gradle.mkdir(parents=True)
(data / 'userpreferences').write_text(json.dumps({'core': {'gradle': {'Xmx': 1024, 'useChinaMirrors': False}}}) + '\n')
(gradle / 'gradle.properties').write_text('org.gradle.workers.max=1\norg.gradle.parallel=false\norg.gradle.jvmargs=-Xmx1G\n')
facts = {'at': datetime.now(timezone.utc).isoformat(), 'workspaceExists': False,
         'dependencyCacheExists': False, 'wrapperCacheExists': False, 'minecraftAssetsExist': False,
         'sourceCacheCopied': False, 'firstUseDefaultPreferences': False,
         'profileReason': 'Authorized low-memory profile and supported official repository preference',
         'javaToolOptionsPurpose': 'Only -Duser.home isolates historical Gradle distribution lookup',
         'profileFiles': [{'path': str(p.relative_to(ROOT)), 'bytes': p.stat().st_size,
                           'sha256': hashlib.file_digest(p.open('rb'), 'sha256').hexdigest()}
                          for p in (data / 'userpreferences', gradle / 'gradle.properties')],
         'originalUserDataRetained': True}
(ROOT / 'cold-initial-state.json').write_text(json.dumps(facts, indent=2) + '\n')
print(json.dumps({'initialized': True, 'dependencyCacheEmpty': True, 'assetsEmpty': True}))
