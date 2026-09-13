"""Reuse every installed-Agent assertion, attaching the UI-issued Stage16 grant."""
import importlib.util
import json
from pathlib import Path
import sys

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

gate.CopperbenchClient = ScopedClient
gate.main()
