"""Reuse every installed-Agent assertion, attaching the UI-issued Stage16 grant."""
import importlib.util
import json
import os
from pathlib import Path
import sys
from datetime import datetime, timezone
import time

HERE = Path(__file__).resolve().parent
spec = importlib.util.spec_from_file_location('installed_agent', HERE / 'verify-stage15-linux-installed-agent.py')
gate = importlib.util.module_from_spec(spec)
spec.loader.exec_module(gate)
grant_file = HERE.parent / 'grant.stdout'
grant = json.loads(grant_file.read_text())
assert grant['status'] == 'completed'
authorization_id = grant['data'].get('authorizationId') or grant['data'].get('id')
assert authorization_id
BaseClient = gate.CopperbenchClient

class ScopedClient(BaseClient):
    def __init__(self, *args, **kwargs):
        super().__init__(*args, task_authorization_id=authorization_id, **kwargs)

    def call_tool(self, name, arguments):
        began = time.monotonic()
        event = {'at': datetime.now(timezone.utc).isoformat(), 'tool': name, 'arguments': arguments,
                 'taskAuthorizationId': self.task_authorization_id}
        try:
            result = super().call_tool(name, arguments)
            event['result'] = result
            return result
        except Exception as error:
            event['error'] = {'type': type(error).__name__, 'code': getattr(error, 'code', None),
                              'details': getattr(error, 'details', None)}
            raise
        finally:
            event['elapsedSeconds'] = round(time.monotonic() - began, 6)
            trace = Path(sys.argv[sys.argv.index('--output') + 1]).with_name('mcp-tool-events.jsonl')
            with trace.open('a', encoding='utf-8') as log:
                log.write(json.dumps(event, ensure_ascii=False).replace(self.token, '[REDACTED]') + '\n')

gate.CopperbenchClient = ScopedClient
if os.name == 'nt':
    # A GUI launcher already masked the entry; Windows getpass expects a console.
    gate.getpass.getpass = lambda prompt: sys.stdin.readline().rstrip('\r\n')
gate.main()
