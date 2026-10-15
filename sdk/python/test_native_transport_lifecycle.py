"""Native pipe/socket lifecycle regressions, without Java Core or a desktop app."""
from __future__ import annotations

import hashlib
import io
import json
import os
from pathlib import Path
import queue
import select
import socket
import subprocess
import sys
import tempfile
import threading
import time
import unittest
from unittest.mock import patch

from copperbench import ClientMetrics, NativeApiError, Workspace


PIPE_HOST = r'''
import json
from pathlib import Path
import sys

ending = '\r\n' if sys.argv[1] == 'crlf' else '\n'
root = Path(__file__).with_suffix('')

def emit(value, operation):
    wire = (json.dumps(value, ensure_ascii=False) + ending).encode('utf-8')
    with root.with_suffix('.responses').open('a', encoding='utf-8') as stream:
        stream.write(json.dumps({'operation': operation, 'bytes': len(wire)}) + '\n')
    sys.stdout.buffer.write(wire)
    sys.stdout.buffer.flush()

emit({'nativeApiVersion': '1', 'status': 'ready', 'workspaceId': 'pipe-fixture'}, 'ready')
for line in sys.stdin:
    request = json.loads(line)
    with root.with_suffix('.requests').open('a', encoding='utf-8') as stream:
        stream.write(line)
    operation = request['operation']
    if operation == 'bad_utf8':
        sys.stdout.buffer.write(b'\xff\r\n')
        sys.stdout.buffer.flush()
        continue
    result = {'status': 'succeeded', 'revision': 0,
              'data': {'name': '铜铃'} if operation == 'unicode' else {}}
    emit({'id': request['id'], 'result': result}, operation)
root.with_suffix('.eof').write_text('normal EOF', encoding='utf-8')
'''


class PausedAfterFlush:
    """Model a writer descheduled after sending bytes, outside the I/O lock."""

    def __init__(self, stream):
        self.stream = stream
        self.flushed = threading.Event()
        self.release = threading.Event()

    def __getattr__(self, name):
        return getattr(self.stream, name)

    def flush(self):
        self.stream.flush()
        self.flushed.set()
        if not self.release.wait(10):
            raise RuntimeError('The test did not release the acknowledged writer')


class SignalledWrite:
    def __init__(self, stream):
        self.stream = stream
        self.entered = threading.Event()
        self.flushed = threading.Event()

    def __getattr__(self, name):
        return getattr(self.stream, name)

    def write(self, value):
        self.entered.set()
        return self.stream.write(value)

    def flush(self):
        self.stream.flush()
        self.flushed.set()


class DesktopHost:
    """A loopback Native host that remains available after a client disconnect."""

    workspace_id = 'desktop-fixture'
    token = 'native-test-token'

    def __init__(self, ending='\n', mode='normal'):
        self.ending, self.mode = ending, mode
        self.stopped = threading.Event()
        self.blocked_read = threading.Event()
        self.blocked_write = threading.Event()
        self.blocked_write_claimed = False
        self.disconnected = threading.Event()
        self.lock = threading.Lock()
        self.requests = []
        self.response_sizes = []
        self.errors = []
        self.clients = []
        self.workers = []
        self.listener = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        self.listener.setsockopt(socket.SOL_SOCKET, socket.SO_RCVBUF, 4096)
        self.listener.bind(('127.0.0.1', 0))
        self.listener.listen()
        self.listener.settimeout(0.1)
        self.port = self.listener.getsockname()[1]
        self.thread = threading.Thread(target=self.accept, daemon=True)
        self.thread.start()

    def accept(self):
        while not self.stopped.is_set():
            try:
                client, _ = self.listener.accept()
            except socket.timeout:
                continue
            except OSError:
                return
            client.setsockopt(socket.SOL_SOCKET, socket.SO_RCVBUF, 4096)
            with self.lock:
                self.clients.append(client)
                worker = threading.Thread(target=self.serve, args=(client,), daemon=True)
                self.workers.append(worker)
            worker.start()

    def emit(self, client, value, operation):
        wire = (json.dumps(value, ensure_ascii=False) + self.ending).encode('utf-8')
        with self.lock:
            self.response_sizes.append({'operation': operation, 'bytes': len(wire)})
        client.sendall(wire)

    def serve(self, client):
        try:
            with client, client.makefile('rb') as stream:
                hello = json.loads(stream.readline())
                if hello != {'token': self.token}:
                    raise AssertionError('Native fixture authentication did not match')
                self.emit(client, {'nativeApiVersion': '1', 'status': 'ready',
                                   'workspaceId': self.workspace_id}, 'ready')
                for line in stream:
                    request = json.loads(line)
                    with self.lock:
                        self.requests.append(request)
                    operation = request['operation']
                    if operation == 'slow':
                        self.blocked_read.set()
                        self.stopped.wait(10)
                        return
                    if operation == 'bad_utf8':
                        client.sendall(b'\xff\r\n')
                        continue
                    result = {'status': 'succeeded', 'revision': 0,
                              'data': {'name': '铜铃'} if operation == 'unicode' else {}}
                    self.emit(client, {'id': request['id'], 'result': result}, operation)
                    with self.lock:
                        block_write = (self.mode == 'blocked_write' and operation == 'get_workbench'
                                       and not self.blocked_write_claimed)
                        if block_write:
                            self.blocked_write_claimed = True
                    if block_write:
                        self.blocked_write.set()
                        self.stopped.wait(10)
                        return
        except (OSError, ValueError):
            # Disconnecting during a response is a normal fixture lifecycle.
            pass
        except Exception as error:
            with self.lock:
                self.errors.append(error)
        finally:
            self.disconnected.set()

    def close(self):
        self.stopped.set()
        self.listener.close()
        self.thread.join(timeout=1)
        with self.lock:
            clients, workers = list(self.clients), list(self.workers)
        for client in clients:
            try:
                client.shutdown(socket.SHUT_RDWR)
            except OSError:
                pass
            client.close()
        for worker in workers:
            worker.join(timeout=1)


class NativeTransportLifecycleTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory(prefix='copperbench-native-lifecycle-')
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        self.descriptor = self.root / 'isolated.mcreator'
        self.descriptor.write_text('{}', encoding='utf-8')

    def open_pipe(self, label='pipe', ending='lf', **options):
        directory = self.root / label
        directory.mkdir()
        descriptor = directory / 'fixture.mcreator'
        descriptor.write_text('{}', encoding='utf-8')
        host = directory / 'host.py'
        host.write_text(PIPE_HOST, encoding='utf-8')
        original_popen = subprocess.Popen

        def hidden_popen(*args, **kwargs):
            if os.name == 'nt':
                kwargs['creationflags'] = kwargs.get('creationflags', 0) | subprocess.CREATE_NO_WINDOW
            return original_popen(*args, **kwargs)

        with patch('copperbench_native.subprocess.Popen', side_effect=hidden_popen):
            client = Workspace.open(descriptor, launcher=[sys.executable, '-u', str(host), ending],
                                    startup_timeout=5, request_timeout=5, **options)
        self.addCleanup(client.close)
        return client, host

    def desktop_host(self, **options):
        host = DesktopHost(**options)
        self.addCleanup(host.close)
        return host

    def attach(self, host, **options):
        key = self.descriptor.resolve().as_posix()
        if os.name == 'nt':
            key = key.lower()
        digest = hashlib.sha256(key.encode('utf-8')).hexdigest()
        discovery = self.root / '.copperbench/python-sessions' / (digest + '.json')
        discovery.parent.mkdir(parents=True, exist_ok=True)
        discovery.write_text(json.dumps({'nativeApiVersion': '1', 'port': host.port,
                                        'token': host.token, 'workspaceId': host.workspace_id}), encoding='utf-8')
        with patch('copperbench_native.Path.home', return_value=self.root):
            client = Workspace.connect(self.descriptor, **options)
        self.addCleanup(client.close)
        return client

    def acknowledged_writer_close(self, client, assert_host_alive):
        client._writer.join(timeout=2)
        paused = PausedAfterFlush(client._input)
        client._input = paused
        waiting = threading.Event()
        closed = []
        closer = None
        try:
            self.assertEqual('succeeded', client.query('acknowledged')['status'])
            self.assertTrue(paused.flushed.wait(2))
            writer = client._writer
            self.assertTrue(writer.is_alive(), 'The controlled writer must still be paused after its acknowledgement')
            original_join = writer.join

            def observed_join(timeout=None):
                if not waiting.is_set():
                    waiting.set()
                    assert_host_alive()
                    # Resume only once the SDK's close path actually reaches
                    # its writer wait. The old eager-terminate path has killed
                    # an owned host by this point and fails this assertion.
                    paused.release.set()
                return original_join(timeout)

            def close():
                try:
                    client.close()
                except Exception as error:
                    closed.append(error)

            with patch.object(writer, 'join', side_effect=observed_join):
                closer = threading.Thread(target=close, daemon=True)
                closer.start()
                self.assertTrue(waiting.wait(2), 'Close must wait for its already acknowledged writer')
                # Unlike the historical review probe, resume while close is
                # waiting, so graceful EOF can actually finish the host.
                closer.join(timeout=3)
                self.assertFalse(closer.is_alive())
                self.assertEqual([], closed)
        finally:
            paused.release.set()
            if closer is not None:
                closer.join(timeout=3)
            if client._writer is not None:
                client._writer.join(timeout=2)

    def test_owned_acknowledged_writer_resumes_during_close_and_exits_by_eof(self):
        client, host = self.open_pipe()
        self.acknowledged_writer_close(client, lambda: self.assertIsNone(client._process.poll()))
        self.assertEqual(0, client._process.returncode)
        self.assertEqual('normal EOF', host.with_suffix('.eof').read_text(encoding='utf-8'))
        requests = [json.loads(line) for line in host.with_suffix('.requests').read_text(encoding='utf-8').splitlines()]
        self.assertEqual(1, sum(request['operation'] == 'acknowledged' for request in requests))

    def test_owned_metrics_count_exact_lf_crlf_and_unicode_wire_bytes(self):
        for ending in ('lf', 'crlf'):
            with self.subTest(ending=ending):
                metrics = ClientMetrics()
                client, host = self.open_pipe(label=ending, ending=ending, metrics=metrics)
                self.assertEqual('铜铃', client.query('unicode')['data']['name'])
                sizes = [json.loads(line) for line in host.with_suffix('.responses').read_text(encoding='utf-8').splitlines()]
                rows = metrics.snapshot()['operations']
                self.assertEqual({'get_workbench', 'unicode'}, set(rows))
                for operation in rows:
                    self.assertEqual(sum(item['bytes'] for item in sizes if item['operation'] == operation),
                                     rows[operation]['responseBytes'])
                client.close()
                self.assertEqual(0, client._process.returncode)

    def test_owned_invalid_utf8_fails_explicitly_and_reaps_only_its_host(self):
        client, _ = self.open_pipe()
        with self.assertRaises(NativeApiError) as error:
            client.query('bad_utf8')
        self.assertEqual('NATIVE_INVALID_RESPONSE', error.exception.code)
        self.assertIsNotNone(client._process.poll())

    def test_response_character_limit_is_unchanged_for_lf_and_crlf(self):
        for ending in ('\n', '\r\n'):
            for body, valid in (('{"x":1}', True), ('{"x":10}', False)):
                with self.subTest(ending=repr(ending), valid=valid):
                    wire = (body + ending).encode('utf-8')
                    client = Workspace.__new__(Workspace)
                    client._responses = queue.Queue()
                    client._output = io.TextIOWrapper(io.BytesIO(wire), encoding='utf-8', newline='')
                    with patch('copperbench_native._MAX_RESPONSE_CHARS', 8):
                        client._read_output()
                    value = client._responses.get_nowait()
                    if valid:
                        self.assertEqual(({'x': 1}, len(wire)), value)
                    else:
                        self.assertIsInstance(value, NativeApiError)
                        self.assertEqual('NATIVE_INVALID_RESPONSE', value.code)
                        self.assertIn('exceeds 32 Mi characters', str(value))

    def test_attached_acknowledged_writer_disconnects_and_host_accepts_another_client(self):
        host = self.desktop_host()
        client = self.attach(host)

        def assert_connection_closed():
            self.assertTrue(host.thread.is_alive())
            self.assertEqual(-1, client._socket.fileno(), 'The OS socket must close while its writer wrapper is still alive')

        self.acknowledged_writer_close(client, assert_connection_closed)
        self.assertTrue(host.disconnected.wait(2))
        self.assertIsNone(client._process)
        another = self.attach(host)
        self.assertEqual('铜铃', another.query('unicode')['data']['name'])
        self.assertEqual(1, sum(request['operation'] == 'acknowledged' for request in host.requests))
        self.assertEqual([], host.errors)

    def test_attached_metrics_count_exact_lf_crlf_and_unicode_wire_bytes(self):
        for ending in ('\n', '\r\n'):
            with self.subTest(ending=repr(ending)):
                host = self.desktop_host(ending=ending)
                metrics = ClientMetrics()
                client = self.attach(host, metrics=metrics)
                self.assertEqual('铜铃', client.query('unicode')['data']['name'])
                rows = metrics.snapshot()['operations']
                self.assertEqual({'get_workbench', 'unicode'}, set(rows))
                for operation in rows:
                    self.assertEqual(sum(item['bytes'] for item in host.response_sizes if item['operation'] == operation),
                                     rows[operation]['responseBytes'])
                client.close()
                self.assertTrue(host.thread.is_alive())
                self.assertEqual([], host.errors)

    def test_attached_invalid_utf8_does_not_stop_the_host(self):
        host = self.desktop_host()
        client = self.attach(host)
        with self.assertRaises(NativeApiError) as error:
            client.query('bad_utf8')
        self.assertEqual('NATIVE_INVALID_RESPONSE', error.exception.code)
        another = self.attach(host)
        self.assertEqual('succeeded', another.get_workspace()['status'])
        self.assertEqual([], host.errors)

    def test_attached_close_interrupts_a_blocked_read_without_replaying_it(self):
        host = self.desktop_host()
        client = self.attach(host, request_timeout=60)
        errors = []

        def call():
            try:
                client.query('slow')
            except Exception as error:
                errors.append(error)

        worker = threading.Thread(target=call, daemon=True)
        worker.start()
        self.assertTrue(host.blocked_read.wait(2))
        started = time.monotonic()
        client.close()
        worker.join(timeout=2)
        self.assertLess(time.monotonic() - started, 3)
        self.assertFalse(worker.is_alive())
        self.assertEqual(['NATIVE_SESSION_CLOSED'], [getattr(error, 'code', None) for error in errors])
        self.assertEqual(1, sum(request['operation'] == 'slow' for request in host.requests))
        another = self.attach(host)
        self.assertEqual('succeeded', another.get_workspace()['status'])

    def test_attached_close_interrupts_a_blocked_write_without_stopping_host(self):
        host = self.desktop_host(mode='blocked_write')
        client = self.attach(host, request_timeout=60)
        self.assertTrue(host.blocked_write.wait(2))
        client._socket.setsockopt(socket.SOL_SOCKET, socket.SO_SNDBUF, 4096)
        # Loopback can buffer the entire request despite SO_SNDBUF/SO_RCVBUF.
        # Establish actual backpressure first, without changing blocking mode
        # under the SDK's concurrently waiting reader. The host is not reading
        # this connection, so this bounded whitespace prefix is fixture setup.
        stop_prefill = threading.Event()
        prefill_finished = threading.Event()

        def prefill():
            try:
                chunk = b' ' * (64 * 1024)
                for _ in range(1024):  # At most 64 MiB, no unbounded producer.
                    if stop_prefill.is_set():
                        break
                    client._socket.sendall(chunk)
            except OSError:
                pass  # SDK socket shutdown interrupts the pending send.
            finally:
                prefill_finished.set()

        filler = threading.Thread(target=prefill, daemon=True)
        filler.start()

        def stop_filler():
            stop_prefill.set()
            try:
                client.close()
            finally:
                filler.join(timeout=2)
                self.assertFalse(filler.is_alive(), 'Socket shutdown must release the fixture prefill writer')

        self.addCleanup(stop_filler)
        deadline = time.monotonic() + 3
        while time.monotonic() < deadline:
            if (not select.select([], [client._socket], [], 0.05)[1]
                    and not select.select([], [client._socket], [], 0.05)[1]):
                break
            if prefill_finished.is_set():
                self.fail('Socket fixture did not establish backpressure within its bounded prefill')
        else:
            self.fail('Socket fixture did not become non-writable before its preparation deadline')
        self.assertFalse(prefill_finished.is_set(), 'The fixture prefill send must still be blocked')
        stop_prefill.set()
        signalled = SignalledWrite(client._input)
        client._input = signalled
        errors = []

        def call():
            try:
                client.command('update_mod_element', content='x' * 1024 * 1024)
            except Exception as error:
                errors.append(error)

        worker = threading.Thread(target=call, daemon=True)
        worker.start()
        self.assertTrue(signalled.entered.wait(2))
        self.assertFalse(signalled.flushed.wait(0.05), 'The fixture must keep this write blocked before close')
        self.assertTrue(client._writer.is_alive())
        started = time.monotonic()
        client.close()
        worker.join(timeout=2)
        client._writer.join(timeout=2)
        self.assertFalse(client._writer.is_alive(), 'SDK close must release its actual buffered socket writer')
        self.assertEqual(-1, client._socket.fileno(), 'The closed connection must release its OS socket')
        filler.join(timeout=2)
        self.assertLess(time.monotonic() - started, 3)
        self.assertFalse(worker.is_alive())
        self.assertFalse(filler.is_alive(), 'SDK shutdown must interrupt both socket writers')
        self.assertEqual(['NATIVE_SESSION_CLOSED'], [getattr(error, 'code', None) for error in errors])
        self.assertIsNone(client._process)
        another = self.attach(host)
        self.assertEqual('succeeded', another.get_workspace()['status'])
        self.assertEqual([], host.errors)


if __name__ == '__main__':
    unittest.main()
