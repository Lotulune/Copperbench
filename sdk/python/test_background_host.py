"""Workspace.attach: reuse one Core across scripts, without Java Core or a desktop app."""
from __future__ import annotations

from concurrent.futures import ThreadPoolExecutor
import threading
import json
import os
from pathlib import Path
import sys
import tempfile
import time
import unittest
from unittest.mock import patch

from copperbench import NativeApiError, Workspace


SERVE_HOST = r'''
import json, os, sys, time
from pathlib import Path
Path(sys.argv[1]).write_text(json.dumps({'pid': os.getpid(), 'argv': sys.argv[2:]}), encoding='utf-8')
mode = os.environ['FIXTURE_MODE']
if mode == 'serving':
    print(json.dumps({'nativeApiVersion': '1', 'status': 'serving', 'workspaceId': 'fixture'}), flush=True)
    time.sleep(30)
elif mode == 'locked':
    print(json.dumps({'status': 'failed', 'code': 'WORKSPACE_WRITE_LOCKED'}), flush=True)
elif mode == 'failed':
    print(json.dumps({'status': 'failed', 'code': 'HEADLESS_PRODUCT_START_FAILED'}), flush=True)
'''


class BackgroundHostTest(unittest.TestCase):
    def setUp(self):
        directory = tempfile.TemporaryDirectory()
        self.addCleanup(directory.cleanup)
        self.root = Path(directory.name)
        self.workspace = self.root / 'bell.mcreator'
        self.workspace.write_text('{}', encoding='utf-8')
        self.script = self.root / 'serve_host.py'
        self.script.write_text(SERVE_HOST, encoding='utf-8')
        self.record = self.root / 'host.json'

    def launcher(self):
        return [sys.executable, str(self.script), str(self.record)]

    def stop_fixture(self):
        if self.record.exists():
            pid = json.loads(self.record.read_text(encoding='utf-8'))['pid']
            try:
                os.kill(pid, 9)
            except OSError:
                pass

    def test_an_available_session_is_reused_without_starting_a_host(self):
        session = object()
        with patch.object(Workspace, 'connect', return_value=session) as connect, \
                patch.object(Workspace, '_start_background_host') as start:
            self.assertIs(session, Workspace.attach(self.workspace, launcher=self.launcher()))
        connect.assert_called_once()
        start.assert_not_called()

    def test_a_missing_session_starts_one_detached_serve_host_then_connects(self):
        session = object()
        unavailable = NativeApiError('none', 'NATIVE_CONNECTION_UNAVAILABLE')
        self.addCleanup(self.stop_fixture)
        with patch.dict(os.environ, {'FIXTURE_MODE': 'serving'}), \
                patch.object(Workspace, 'connect', side_effect=[unavailable, unavailable, session]) as connect:
            started = time.monotonic()
            self.assertIs(session, Workspace.attach(self.workspace, launcher=self.launcher(), idle_timeout=60.5))
            self.assertLess(time.monotonic() - started, 20, 'attach returns at readiness, not when the host exits')
        self.assertEqual(3, connect.call_count)
        argv = json.loads(self.record.read_text(encoding='utf-8'))['argv']
        self.assertEqual(['headless', '--workspace', str(self.workspace.resolve()), 'serve', '--idle-timeout', '61'], argv)

    def test_a_concurrent_start_reconnects_to_the_host_that_won_the_write_lock(self):
        session = object()
        unavailable = NativeApiError('none', 'NATIVE_CONNECTION_UNAVAILABLE')
        first_lookup = threading.local()
        both_missed = threading.Barrier(2)
        published = threading.Event()

        def connect(*args, **kwargs):
            if not getattr(first_lookup, 'done', False):
                first_lookup.done = True
                both_missed.wait(timeout=5)
                raise unavailable
            if not published.is_set():
                raise unavailable
            return session

        def launch(*args):
            published.set()
            return {'status': 'serving'}

        with patch.object(Workspace, 'connect', side_effect=connect), \
                patch.object(Workspace, '_start_background_host', side_effect=launch) as start, \
                ThreadPoolExecutor(max_workers=2) as pool:
            calls = [pool.submit(Workspace.attach, self.workspace, launcher=self.launcher()) for _ in range(2)]
            self.assertEqual([session, session], [call.result(timeout=5) for call in calls])
            start.assert_called_once()

        with patch.dict(os.environ, {'FIXTURE_MODE': 'locked'}), \
                patch.object(Workspace, 'connect', side_effect=[unavailable, unavailable, session]):
            self.assertIs(session, Workspace.attach(self.workspace, launcher=self.launcher()))
        with patch.dict(os.environ, {'FIXTURE_MODE': 'locked'}), \
                patch.object(Workspace, 'connect', side_effect=[unavailable, unavailable, unavailable]):
            with self.assertRaises(NativeApiError) as error:
                Workspace.attach(self.workspace, launcher=self.launcher())
        self.assertEqual('WORKSPACE_WRITE_LOCKED', error.exception.code)

    def test_a_failed_start_reports_the_core_code_without_retrying(self):
        unavailable = NativeApiError('none', 'NATIVE_CONNECTION_UNAVAILABLE')
        with patch.dict(os.environ, {'FIXTURE_MODE': 'failed'}), \
                patch.object(Workspace, 'connect', side_effect=[unavailable, unavailable]) as connect:
            with self.assertRaises(NativeApiError) as error:
                Workspace.attach(self.workspace, launcher=self.launcher())
        self.assertEqual('HEADLESS_PRODUCT_START_FAILED', error.exception.code)
        self.assertEqual(2, connect.call_count)

    def test_other_connection_errors_and_invalid_idle_timeouts_are_not_masked(self):
        rejected = NativeApiError('bad token', 'NATIVE_CONNECTION_REJECTED')
        with patch.object(Workspace, 'connect', side_effect=[rejected]), \
                patch.object(Workspace, '_start_background_host') as start:
            with self.assertRaises(NativeApiError) as error:
                Workspace.attach(self.workspace, launcher=self.launcher())
        self.assertEqual('NATIVE_CONNECTION_REJECTED', error.exception.code)
        start.assert_not_called()
        for invalid in (1, 9 * 3600, float('nan')):
            with self.assertRaises(ValueError):
                Workspace.attach(self.workspace, launcher=self.launcher(), idle_timeout=invalid)


if __name__ == '__main__':
    unittest.main()
