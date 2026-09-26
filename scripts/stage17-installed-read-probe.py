"""Read-only Stage17 smoke against a packaged/installed candidate via the public native SDK."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import socket
import sys
import time

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--workspace', type=Path, required=True)
parser.add_argument('--distribution', type=Path, required=True)
parser.add_argument('--sdk', type=Path, required=True)
parser.add_argument('--output', type=Path, required=True)
parser.add_argument('--expected-probe-state', default='unreachable', choices=['unreachable', 'initialization_error'])
args = parser.parse_args()
sys.path.insert(0, str(args.sdk.resolve()))
from copperbench import Workspace

distribution = args.distribution.resolve()
jar = distribution / 'lib/copperbench.jar'
java = distribution / ('jdk/bin/java.exe' if os.name == 'nt' else 'jdk/bin/java')
launcher = [str(java), '--add-opens=java.base/java.lang=ALL-UNNAMED',
            '--enable-native-access=ALL-UNNAMED,jcef', '-Dcopperbench.productShell=true',
            *(['-Dcopperbench.stage15LinuxCandidate=true'] if os.name != 'nt' else []),
            '-cp', str(distribution / 'lib/*'), 'net.mcreator.Launcher']
result = {'applicationSha256': hashlib.sha256(jar.read_bytes()).hexdigest(), 'status': 'running'}
try:
    with Workspace.open(args.workspace, launcher=launcher, cwd=distribution, startup_timeout=120) as workspace:
        result['environment'] = workspace.query('get_workspace_environment')['data']
        result['contract'] = workspace.field_contract('block')
        result['elements'] = list(workspace.list_mod_elements())
        result['health'] = workspace.query('get_workspace_health')['data']
        result['blockbenchNoProbe'] = workspace.query('get_blockbench_environment')['data']
        with socket.socket() as port:
            port.bind(('127.0.0.1', 0))
            endpoint = f'http://127.0.0.1:{port.getsockname()[1]}/mcp'
        started = time.monotonic()
        result['blockbenchUnreachable'] = workspace.query('get_blockbench_environment', probeMcp=True, endpoint=endpoint)['data']
        result['probeElapsedSeconds'] = time.monotonic() - started
        assert result['blockbenchNoProbe']['mcp']['state'] == 'not_checked'
        assert result['blockbenchUnreachable']['mcp']['state'] == args.expected_probe_state
        assert result['probeElapsedSeconds'] < 12
        assert result['blockbenchUnreachable']['application']['applicationSha256'] == result['applicationSha256']
        result['status'] = 'passed'
except Exception as error:
    result['status'] = 'failed'
    result['error'] = str(error)
    raise
finally:
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding='utf-8')
