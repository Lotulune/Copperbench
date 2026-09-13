"""Check the local source-fix evidence without claiming an installed-package replay."""
import hashlib
import json
from pathlib import Path
import xml.etree.ElementTree as ET
root = Path(__file__).resolve().parent
manifest = json.loads((root / 'archive-manifest.json').read_text(encoding='utf-8-sig'))
for entry in manifest['files']:
    path = (root / entry['path']).resolve()
    assert path.is_relative_to(root)
    content = path.read_bytes()
    assert len(content) == entry['bytes'] and hashlib.sha256(content).hexdigest() == entry['sha256']
state = json.loads((root / 'source-and-tests.json').read_text(encoding='utf-8-sig'))
assert not state['installedCandidateRetested'] and not state['committed'] and not state['published']
suites = [ET.parse(path).getroot() for path in root.glob('TEST-*.xml')]
assert len(suites) == 2
assert sum(int(s.attrib['tests']) for s in suites) == 26
assert all(int(s.attrib['failures']) == int(s.attrib['errors']) == int(s.attrib['skipped']) == 0 for s in suites)
cases = [case.attrib['name'] for suite in suites for case in suite.findall('testcase')]
assert 'recognizesTheObservedWindowsOpenGlDriverFailure()' in cases
assert sum('false' in name or 'true' in name for name in cases) >= 2
print(json.dumps({'status':'passed', 'tests':26, 'failures':0, 'scope':'local source regression checks',
                  'installedCandidateRetested':False, 'published':False}))
