import argparse
import hashlib
import io
import json
from pathlib import Path
from zipfile import ZipFile

p = argparse.ArgumentParser()
p.add_argument('--archive', type=Path, required=True)
p.add_argument('--source', required=True)
p.add_argument('--result', type=Path, required=True)
a = p.parse_args()
with ZipFile(a.archive) as package:
    matches = [name for name in package.namelist() if name.endswith('/lib/copperbench.jar') or name == 'lib/copperbench.jar']
    assert len(matches) == 1
    jar_bytes = package.read(matches[0])
with ZipFile(io.BytesIO(jar_bytes)) as jar:
    assert 'product.version=0.1.17' in jar.read('mcreator.conf').decode('utf-8')
    checks = {}
    for name, marker in [('dev/copperbench/agent/AgentChatService.class', 'usesSessionModel'),
                         ('dev/copperbench/agent/AgentChatStore.class', 'retainedAttachmentIds'),
                         ('dev/copperbench/agent/AgentChatStore$Conversation.class', 'retainedAttachmentIds')]:
        data = jar.read(name)
        assert marker.encode() in data, name
        checks[name] = hashlib.sha256(data).hexdigest()
    scripts = {name: jar.read(name) for name in jar.namelist() if name.startswith('copperbench/ui/assets/') and name.endswith('.js')}
    styles = {name: jar.read(name) for name in jar.namelist() if name.startswith('copperbench/ui/assets/') and name.endswith('.css')}
    for marker in ['Keep session model', '沿用会话模型', 'The attachment chunk was not acknowledged.', '附件分块未被确认。']:
        assert any(marker.encode() in data for data in scripts.values()), marker
    assert any(b'.titlebar:has(.workbench-mode-slot){column-gap:4px}' in data for data in styles.values())
    for name, data in {**scripts, **styles}.items():
        if b'Keep session model' in data or b'.titlebar:has(.workbench-mode-slot)' in data:
            checks[name] = hashlib.sha256(data).hexdigest()
result = {'status': 'passed', 'sourceCommit': a.source, 'archive': a.archive.name, 'productVersion': '0.1.17',
          'jarEntry': matches[0], 'jarBytes': len(jar_bytes), 'jarSha256': hashlib.sha256(jar_bytes).hexdigest(),
          'checkedEntries': checks, 'scope': 'Packaged version and new conversation/retention/localization/compact CSS markers; source test and signed provenance identities are verified separately.'}
a.result.write_text(json.dumps(result, indent=2) + '\n', encoding='utf-8')
print(json.dumps({'status': 'passed', 'jarSha256': result['jarSha256'], 'checkedEntries': len(checks)}))
