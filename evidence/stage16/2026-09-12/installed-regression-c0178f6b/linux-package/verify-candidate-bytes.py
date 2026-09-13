"""Verify the frozen archive contains the committed OpenGL fix and shared installed bytes."""
import hashlib
import io
import json
from pathlib import Path
import zipfile

root = Path(__file__).resolve().parent
candidate = json.loads((root / 'candidate.json').read_text(encoding='utf-8-sig'))
with zipfile.ZipFile(candidate['archivePath']) as archive:
    for member, expected in (
        ('Copperbench010/copperbench.exe', candidate['productExeSha256']),
        ('Copperbench010/lib/copperbench.jar', candidate['applicationJarSha256']),
        ('Copperbench010/lib/copperbench-local-ipc-agent.jar', candidate['startupHelperJarSha256']),
    ):
        data = archive.read(member)
        assert hashlib.sha256(data).hexdigest() == expected, member
    jar_bytes = archive.read('Copperbench010/lib/copperbench.jar')
with zipfile.ZipFile(io.BytesIO(jar_bytes)) as jar:
    gateway = jar.read('dev/copperbench/generator/GradleWorkspaceTaskGateway.class')
    assert b'WINDOWS_OPENGL_INITIALIZATION_FAILED' in gateway
    assert b'diagnostic.task_client_opengl_initialization_failed' in gateway
    outer = jar.read('dev/copperbench/generator/fabric/Fabric1211ProcessRunner.class')
    assert b'graphicalFailureCode' in outer
    assert b'wgl: the driver does not appear to support opengl' in outer
result = {'status':'passed', 'sourceCommit':candidate['sourceCommit'],
          'archiveApplicationAndNativeLauncherHashesMatch':True,
          'compiledWindowsGraphicsClassificationPresent':True,
          'compiledZeroExitFailureHandlingPresent':True,
          'scope':'packaged byte provenance, not a runtime test'}
(root / 'candidate-byte-verification.json').write_text(json.dumps(result, indent=2) + '\n', encoding='utf-8')
print(json.dumps(result))
