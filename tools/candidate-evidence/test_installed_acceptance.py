"""Synthetic gate boundaries; these tests are not installed or player acceptance."""
import copy
import io
import json
import os
from pathlib import Path
import shlex
import subprocess
import sys
import tarfile
import tempfile
import unittest
from unittest.mock import patch
import zipfile

import candidate_evidence as ce
import installed_acceptance as gate


class InstalledAcceptanceTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(); self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)

    def zip(self, names):
        path = self.root / 'product.zip'
        with zipfile.ZipFile(path, 'w') as stream:
            for name in names: stream.writestr(name, b'fixture')
        return path

    def tar(self, members):
        path = self.root / 'product.tar.gz'
        with tarfile.open(path, 'w:gz') as stream:
            for member, payload in members:
                stream.addfile(member, io.BytesIO(payload) if payload is not None else None)
        return path

    def test_new_zip_is_extracted_and_existing_installation_is_not_overwritten(self):
        archive = self.zip(['Product/lib/application.jar', 'Product/sdk/native.py'])
        destination = self.root / 'install'
        product, count = gate.extract_product(archive, destination, 'windows')
        self.assertEqual(count, 2)
        self.assertEqual((product / 'sdk/native.py').read_bytes(), b'fixture')
        with self.assertRaisesRegex(ValueError, 'new installation'):
            gate.extract_product(archive, destination, 'windows')

    def test_zip_traversal_case_collision_and_multiple_roots_are_rejected(self):
        for index, names in enumerate((['Product/../../escaped'], ['Product/A', 'Product/a'], ['A/x', 'B/y'])):
            with self.subTest(names=names), self.assertRaises(ValueError):
                gate.extract_product(self.zip(names), self.root / str(index), 'windows')
        self.assertFalse((self.root / 'escaped').exists())

    def test_tar_preserves_executable_product_launcher(self):
        launcher = tarfile.TarInfo('Product/copperbench.sh'); launcher.mode = 0o755
        content = b'#!/bin/sh\nexit 0\n'; launcher.size = len(content)
        product, count = gate.extract_product(self.tar([(launcher, content)]), self.root / 'install', 'ubuntu')
        self.assertEqual(count, 1)
        self.assertEqual((product / 'copperbench.sh').read_bytes(), content)
        if os.name != 'nt': self.assertTrue(os.access(product / 'copperbench.sh', os.X_OK))

    def test_tar_escaping_symlink_and_special_file_are_rejected(self):
        link = tarfile.TarInfo('Product/escape'); link.type = tarfile.SYMTYPE; link.linkname = '../../outside'
        fifo = tarfile.TarInfo('Product/fifo'); fifo.type = tarfile.FIFOTYPE
        for index, member in enumerate((link, fifo)):
            with self.subTest(member=member.name), self.assertRaises((ValueError, tarfile.FilterError)):
                gate.extract_product(self.tar([(member, None)]), self.root / str(index), 'ubuntu')

    def test_warm_requires_successful_cold_for_exact_candidate_generator_and_os(self):
        prepared = {'candidateId': 'sha256:one'}
        receipt = {'status': 'passed', 'cacheMode': 'cold', 'initialCacheFiles': 0, 'authorityRevoked': True,
                   'nativeResult': {'status': 'passed'}, 'candidateId': 'sha256:one',
                   'generatorId': 'fabric-26.3', 'platform': 'ubuntu'}
        path = self.root / 'cold.json'; ce.write(path, receipt)
        self.assertEqual(gate.successful_cold_receipt(path, prepared, 'fabric-26.3', 'ubuntu'), receipt)
        for key, value in [('status', 'failed'), ('initialCacheFiles', 1), ('authorityRevoked', False),
                           ('candidateId', 'sha256:two'), ('generatorId', 'neoforge-26.3'), ('platform', 'windows')]:
            with self.subTest(key=key):
                changed = copy.deepcopy(receipt); changed[key] = value; ce.write(path, changed)
                with self.assertRaises(ValueError): gate.successful_cold_receipt(path, prepared, 'fabric-26.3', 'ubuntu')

    def test_warm_cache_rejects_modified_payload_and_path_escape(self):
        cache = self.root / 'cache'; cache.mkdir(); payload = cache / 'dependency.jar'; payload.write_bytes(b'original')
        inventory = self.root / 'inventory.json'
        row = {'path': payload.name, 'bytes': payload.stat().st_size, 'sha256': ce.sha(payload)}
        ce.write(inventory, [row]); digest = ce.sha(inventory)
        self.assertEqual(gate.verify_retained_cache(cache, inventory, digest), 1)
        payload.write_bytes(b'modified')
        with self.assertRaisesRegex(ValueError, 'changed'): gate.verify_retained_cache(cache, inventory, digest)
        row['path'] = '../outside'; ce.write(inventory, [row])
        with self.assertRaises(ValueError): gate.verify_retained_cache(cache, inventory, ce.sha(inventory))

    def test_linux_environment_isolated_without_mutating_parent_or_carrying_jvm_injections(self):
        work = self.root / 'run'; work.mkdir()
        with patch.dict(os.environ, {'JAVA_TOOL_OPTIONS': '-Dhost.test=secret', 'XDG_DATA_HOME': '/host/data'}):
            env = gate.run_environment(self.root / 'product', work / 'home', work / 'gradle', work, 'ubuntu')
            self.assertEqual(os.environ['JAVA_TOOL_OPTIONS'], '-Dhost.test=secret')
            self.assertNotIn('host.test', env['JAVA_TOOL_OPTIONS'])
            self.assertTrue(Path(env['XDG_DATA_HOME']).is_relative_to(work))
            self.assertEqual(env['HOME'], str(work / 'isolated-jvm-home'))
            self.assertNotIn('proxyHost', env['JAVA_TOOL_OPTIONS'])

    def test_windows_jvm_options_preserve_spaces_without_crashing_launcher_quote_form(self):
        work = self.root / 'run with spaces'; work.mkdir()
        env = gate.run_environment(self.root / 'product', work / 'home', work / 'gradle', work, 'windows')
        self.assertNotIn('"', env['JAVA_TOOL_OPTIONS'])
        self.assertEqual(shlex.split(env['JAVA_TOOL_OPTIONS']), ['-Duser.home=' + str(work / 'isolated-jvm-home')])
        quoted = self.root / "run'with-quote"; quoted.mkdir()
        with self.assertRaisesRegex(ValueError, 'Choose a work root'):
            gate.run_environment(self.root / 'product', quoted / 'home', quoted / 'gradle', quoted, 'windows')

    def test_process_crash_keeps_exit_code_and_raw_streams(self):
        def crash(argv, **kwargs):
            kwargs['stderr'].write(b'original process failure')
            return subprocess.CompletedProcess(argv, 0xc0000005)
        with patch.object(gate.subprocess, 'run', side_effect=crash):
            with self.assertRaisesRegex(RuntimeError, r'discovery.*0xc0000005'):
                gate.invoke_json('discovery', ['launcher'], self.root, {}, self.root)
        self.assertEqual((self.root / 'discovery.stderr').read_bytes(), b'original process failure')
        self.assertEqual(ce.read(self.root / 'discovery-process.json')['exitCode'], 0xc0000005)
        with patch.object(gate.subprocess, 'run', side_effect=subprocess.TimeoutExpired(['launcher'], 1)):
            with self.assertRaises(subprocess.TimeoutExpired):
                gate.invoke_json('timeout', ['launcher'], self.root, {}, self.root, timeout=1)
        self.assertEqual(ce.read(self.root / 'timeout-process.json')['errorType'], 'TimeoutExpired')

    def test_structured_negative_receipt_is_returned_with_nonzero_exit(self):
        code, receipt = gate.invoke_json('denied', [sys.executable, '-c',
            'import sys; print(\'{"code":"USER_APPROVAL_REQUIRED"}\'); sys.exit(3)'],
            self.root, os.environ.copy(), self.root)
        self.assertEqual(code, 3)
        self.assertEqual(receipt['code'], 'USER_APPROVAL_REQUIRED')

    def test_cleanup_failures_do_not_hide_primary_failure_or_skip_logs(self):
        record = {'status': 'failed', 'error': 'primary creation error', 'authorityRevoked': False}
        with patch.object(gate, 'collect_raw_logs') as collect:
            def failed_invoke(label, argv): raise RuntimeError(label + ' crashed')
            with self.assertRaisesRegex(ValueError, 'primary creation error'):
                try: raise ValueError('primary creation error')
                finally:
                    gate.cleanup_run(record, {'id': 'test-grant'}, failed_invoke, self.root / 'launcher',
                                     self.root, self.root, self.root, self.root / 'home', self.root / 'cache', 'windows')
            collect.assert_called_once()
        self.assertEqual(record['error'], 'primary creation error')
        self.assertEqual(record['cleanupError'], 'authority-cleanup crashed')
        self.assertEqual(record['authorityRecoveryError'], 'authority-recovery crashed')
        self.assertFalse(record['authorityRevoked'])

    def test_recovery_revokes_grant_but_failed_launcher_cleanup_cannot_pass(self):
        record = {'status': 'passed', 'authorityRevoked': False}
        def invoke(label, argv):
            if label == 'authority-cleanup': raise RuntimeError('launcher crashed')
            return 0, {'revoked': True, 'active': False}
        with patch.object(gate, 'collect_raw_logs') as collect:
            with self.assertRaisesRegex(RuntimeError, 'launcher crashed'):
                gate.cleanup_run(record, {'id': 'test-grant'}, invoke, self.root / 'launcher',
                                 self.root, self.root, self.root, self.root / 'home', self.root / 'cache', 'windows')
            collect.assert_called_once()
        self.assertEqual(record['status'], 'failed')
        self.assertTrue(record['authorityRevoked'])

    def test_separate_cold_cache_is_fresh_and_recorded_even_on_discovery_failure(self):
        prepared = self.root / 'prepared.json'
        ce.write(prepared, {'candidateId': 'sha256:test', 'productRoot': str(self.root / 'product'),
                           'candidatePath': str(self.root / 'candidate'), 'platform': 'windows',
                           'observedArtifacts': {}, 'assetFiles': {}})
        cache = self.root / 'cache-volume' / 'new-cache'
        evidence = self.root / 'evidence'; work = self.root / 'work'
        with patch.object(gate, 'host_identity', return_value={}), \
             patch.object(gate, 'snapshot_harness', return_value={}), \
             patch.object(gate, 'invoke_json', side_effect=RuntimeError('discovery failed')):
            with self.assertRaisesRegex(RuntimeError, 'discovery failed'):
                gate.run_installed(prepared, 'neoforge-26.3', 'cold', evidence, work, 'windows', cache_root=cache)
            with self.assertRaisesRegex(ValueError, 'initial cache'):
                gate.run_installed(prepared, 'neoforge-26.3', 'cold', self.root / 'evidence-2',
                                   self.root / 'work-2', 'windows', cache_root=cache)
        record = ce.read(evidence / 'installed-run.json')
        self.assertEqual(record['gradleHome'], str(cache.resolve()))
        self.assertEqual(record['initialCacheFiles'], 0)
        self.assertEqual(record['error'], 'discovery failed')
        self.assertFalse((evidence / 'IssueGrant.java').exists())

    def test_ubuntu_cell_rejects_a_different_distribution_version(self):
        with patch.object(gate.host_platform, 'system', return_value='Linux'), \
             patch.object(gate.host_platform, 'machine', return_value='x86_64'), \
             patch.object(gate.host_platform, 'freedesktop_os_release', return_value={'ID': 'ubuntu', 'VERSION_ID': '22.04'}):
            with self.assertRaisesRegex(ValueError, 'Ubuntu 24.04'): gate.host_identity('ubuntu')

    def test_failed_run_cannot_produce_portable_pass(self):
        run = self.root / 'failed'; run.mkdir()
        ce.write(run / 'installed-run.json', {'status': 'failed', 'authorityRevoked': True})
        with self.assertRaisesRegex(ValueError, 'failed run'): gate.collect_portable(run)
        self.assertFalse((run / 'portable-check').exists())

    def test_reconnect_counts_integrated_setup_and_rejects_duplicate_or_unclean_sessions(self):
        for level,count in [('G1',3),('G2',4)]:
            native={'sessions':[{'pid':100+i,'exitCode':0} for i in range(count)]}
            gate.verify_native_reconnect(native,level)
            for altered in (native['sessions'][:-1], native['sessions']+[{'pid':200,'exitCode':0}],
                            native['sessions'][:-1]+[native['sessions'][0]],
                            native['sessions'][:-1]+[{'pid':200,'exitCode':1}]):
                with self.subTest(level=level,sessions=altered), self.assertRaisesRegex(ValueError,'distinct, cleanly closed'):
                    gate.verify_native_reconnect({'sessions':altered},level)


if __name__ == '__main__': unittest.main()
