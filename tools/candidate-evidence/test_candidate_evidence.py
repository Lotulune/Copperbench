"""Synthetic filesystem/protocol cases; these are not installed-product acceptance."""
from copy import deepcopy
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
import zipfile

import candidate_evidence as candidate
import package_candidate

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'portable-evidence'))
from test_collection import fixture as portable_fixture


class CandidateTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(); self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name); self.repo = self.root / 'repo'; self.repo.mkdir()
        self.git('init', '-q')
        (self.repo / '.gitignore').write_text('output/\n', encoding='utf-8')
        (self.repo / 'source.txt').write_text('Synthetic test source\n', encoding='utf-8')
        self.git('add', '.'); self.git('-c', 'user.name=Fixture', '-c', 'user.email=fixture@example.invalid', 'commit', '-qm', 'Synthetic fixture')
        self.output = self.repo / 'output'; self.output.mkdir()
        self.assets = []
        for role in sorted(candidate.SDK_ROLES | candidate.PLATFORM_ROLES | candidate.GENERATOR_ROLES):
            path = self.output / (role + '.bin'); path.write_bytes(('Synthetic ' + role).encode())
            asset = {'id': role.replace('_', '-'), 'role': role, 'path': path.relative_to(self.repo).as_posix(),
                     'bytes': path.stat().st_size, 'sha256': candidate.sha(path)}
            if role in candidate.PLATFORM_ROLES: asset['platform'] = 'windows'
            if role in candidate.GENERATOR_ROLES: asset['generatorId'] = 'fabric-26.3'
            self.assets.append(asset)
        source = candidate.source_identity(candidate.source_snapshot(self.repo))
        self.inputs = {'schemaVersion': '1', 'source': source, 'scopes': ['fabric:G1:windows'],
                       'availability': {'sourceSha256': source['workingTreeSha256'], 'scopes': {'fabric:G1:windows': 'ready', 'neoforge:G1:windows': 'unavailable'}},
                       'assets': self.assets, 'buildReceipt': 'build-receipt.json'}
        self.input_path = self.output / 'inputs.json'
        self.frozen = self.output / 'candidate'
        self.save_inputs()

    def git(self, *args):
        return subprocess.check_output(['git', *args], cwd=self.repo, stderr=subprocess.STDOUT)

    def save_inputs(self):
        candidate.write(self.input_path, self.inputs)
        candidate.write(self.output / 'build-receipt.json', {'schemaVersion': '1', 'kind': 'candidate-build-receipt',
            'status': 'passed', 'sourceBefore': self.inputs['source'], 'sourceAfter': self.inputs['source'],
            'commands': [['synthetic-fixture-build']], 'assets': self.inputs['assets']})

    def freeze(self):
        self.metadata = candidate.freeze(self.repo, self.input_path, self.frozen)
        return self.metadata

    def rewrite_metadata(self, edit):
        path = self.frozen / 'candidate.json'; metadata = candidate.read(path); edit(metadata)
        metadata['candidateId'] = 'sha256:' + candidate.digest({k: v for k, v in metadata.items() if k != 'candidateId'})
        candidate.write(path, metadata)

    def test_freeze_binds_actual_bytes_and_leaves_all_ten_cells_unrun(self):
        metadata = self.freeze()
        self.assertEqual(metadata, candidate.verify(self.frozen))
        result = candidate.evaluate(self.frozen, [])
        self.assertEqual(10, len(result['cells'])); self.assertEqual('not_run', result['status'])
        self.assertFalse(result['scopes'][0]['qualificationEligible'])
        self.assertFalse(result['formalSupportClaim']); self.assertFalse(result['producerAuthenticated'])

    def test_missing_role_is_rejected_even_after_rehashing_metadata(self):
        self.freeze(); self.rewrite_metadata(lambda m: m['assets'].pop())
        with self.assertRaisesRegex(ValueError, 'SDK|Missing'): candidate.verify(self.frozen)

    def test_unavailable_scope_and_mixed_levels_are_rejected(self):
        self.inputs['scopes'].append('neoforge:G1:windows'); self.save_inputs()
        with self.assertRaisesRegex(ValueError, 'unavailable'): self.freeze()
        self.inputs['scopes'] = ['fabric:G1:windows', 'fabric:G2:windows']
        self.inputs['availability']['scopes']['fabric:G2:windows'] = 'ready'; self.save_inputs()
        with self.assertRaisesRegex(ValueError, 'mix G1 and G2'): self.freeze()

    def test_wrong_asset_digest_or_stale_build_is_rejected(self):
        (self.repo / self.assets[0]['path']).write_bytes(b'changed')
        with self.assertRaisesRegex(ValueError, 'selected build'): self.freeze()
        self.assertFalse(self.frozen.exists())
        self.assertTrue(list(self.output.glob('.candidate-staging-*/FAILED.json')))

    def test_source_change_rejects_old_inputs(self):
        (self.repo / 'source.txt').write_text('new source', encoding='utf-8')
        with self.assertRaisesRegex(ValueError, 'stale'): self.freeze()

    def test_source_mode_changes_are_part_of_the_snapshot(self):
        before = candidate.source_snapshot(self.repo)
        if os.name == 'nt': self.git('update-index', '--chmod=+x', 'source.txt')
        else: (self.repo / 'source.txt').chmod(0o755)
        after = candidate.source_snapshot(self.repo)
        self.assertNotEqual(before['workingTreeSha256'], after['workingTreeSha256'])
        self.assertTrue(after['dirty'])

    def test_missing_scope_and_invalid_source_are_rejected_after_rehash(self):
        self.freeze(); original = (self.frozen / 'candidate.json').read_bytes()
        for edit in (lambda m: m.update(scopes=[]), lambda m: m['source'].update(dirty='false'),
                     lambda m: m['availability']['scopes'].update({'fabric:G1:windows': 'unavailable'})):
            self.rewrite_metadata(edit)
            with self.assertRaises(ValueError): candidate.verify(self.frozen)
            (self.frozen / 'candidate.json').write_bytes(original)

    def test_build_receipt_and_copied_asset_tampering_are_rejected(self):
        self.freeze(); path = self.frozen / 'build-receipt.json'; original = path.read_bytes()
        path.write_bytes(original + b' ')
        with self.assertRaisesRegex(ValueError, 'Build receipt changed'): candidate.verify(self.frozen)
        path.write_bytes(original)
        (self.frozen / self.metadata['assets'][0]['path']).write_bytes(b'tampered')
        with self.assertRaisesRegex(ValueError, 'asset changed'): candidate.verify(self.frozen)

    def test_outputs_cannot_change_the_snapshot_they_describe(self):
        with self.assertRaisesRegex(ValueError, 'Git-ignored'): candidate.detached_output(self.repo, self.repo / 'inputs.json')
        candidate.detached_output(self.repo, self.output / 'safe.json')
        candidate.detached_output(self.repo, self.root / 'safe.json')

    def plan(self, command):
        plan = {'schemaVersion': '1', 'commands': [command], 'scopes': self.inputs['scopes'],
                'availability': self.inputs['availability']['scopes'], 'assets': self.assets}
        path = self.output / 'plan.json'; candidate.write(path, plan); return path

    def test_build_executes_and_freezes_source_before_and_after(self):
        command = [sys.executable, '-c', 'from pathlib import Path; Path("output/application.bin").write_bytes(b"new-built-output")']
        output = self.output / 'actual-build'
        inputs = candidate.build_candidate(self.repo, self.plan(command), output)
        self.assertEqual(candidate.sha(self.output / 'application.bin'), next(a['sha256'] for a in inputs['assets'] if a['role'] == 'application'))
        self.metadata = candidate.freeze(self.repo, output / 'freeze-inputs.json', self.frozen)
        self.assertEqual(self.metadata, candidate.verify(self.frozen))

    def test_build_failure_or_source_change_never_emits_successful_freeze_input(self):
        for index, code in enumerate(('raise SystemExit(7)', 'from pathlib import Path; Path("source.txt").write_text("mutated")')):
            output = self.output / f'failed-build-{index}'
            with self.assertRaises((ValueError, subprocess.CalledProcessError)):
                candidate.build_candidate(self.repo, self.plan([sys.executable, '-c', code]), output)
            self.assertEqual('failed', candidate.read(output / 'build-receipt.json')['status'])
            self.assertFalse((output / 'freeze-inputs.json').exists())

    def add_file(self, receipt, role, path):
        receipt['evidence'].append({'id': role, 'role': role, 'path': path.relative_to(self.output).as_posix(),
                                    'bytes': path.stat().st_size, 'sha256': candidate.sha(path)})

    def receipt(self, kind='install', *, xml=None):
        receipt = {'schemaVersion': '1', 'kind': 'candidate-cell-evidence', 'candidateId': self.metadata['candidateId'],
                   'cell': kind + ':windows:fabric', 'level': 'G1', 'status': 'passed', 'generatorId': 'fabric-26.3',
                   'observedArtifacts': {a['id']: a['sha256'] for a in self.metadata['assets']}, 'evidence': []}
        roles = candidate.INSTALL_ROLES if kind == 'install' else candidate.CLIENT_ROLES
        for role in sorted(roles):
            path = self.output / (kind + '-' + role + '.json')
            if role.startswith('portable_'):
                source = self.output / role; raw = portable_fixture(source, native=True, historical=role.endswith('historical'))
                if role == 'portable_historical':
                    # Both exports select one actual JAR; independently creating ZIPs
                    # embeds different wall-clock timestamps and is a different artifact.
                    (source / 'verified-mod.jar').write_bytes((self.output / 'portable_current/verified-mod.jar').read_bytes())
                    raw['artifactSha256'] = candidate.sha(source / 'verified-mod.jar')
                if xml is not None:
                    (source / 'gametest-results.xml').write_bytes(xml)
                    raw['reportSha256'] = candidate.sha(source / 'gametest-results.xml')
                candidate.write(source / 'verification.json', raw)
                bundle = self.output / (role + '-bundle'); candidate.portable.collect(source, bundle)
                path = self.output / (role + '.zip')
                with zipfile.ZipFile(path, 'w') as archive:
                    for member in bundle.iterdir(): archive.write(member, member.name)
                receipt['artifactSha256'] = raw['artifactSha256']
            elif role == 'world_state':
                candidate.write(path, {'worldId': 'synthetic-world', 'worldName': 'Synthetic', 'gameDirectory': 'synthetic/game',
                                      'clientPid': 123, 'savedWorldId': 'synthetic-world', 'reopenedWorldId': 'synthetic-world'})
            else: candidate.write(path, {'scope': 'synthetic unit test evidence'})
            self.add_file(receipt, role, path)
        if kind == 'client': receipt['artifactSha256'] = self.installed['artifactSha256']
        binding_path = self.output / (kind + '-run_binding.json')
        candidate.write(binding_path, {key: receipt[key] for key in ('candidateId', 'cell', 'generatorId', 'level', 'observedArtifacts', 'artifactSha256')})
        self.refresh(receipt, 'run_binding')
        checks = candidate.INSTALL_CHECKS if kind == 'install' else candidate.CLIENT_CHECKS
        receipt['checks'] = {name: {'status': 'passed', 'evidenceIds': [file['id'] for file in receipt['evidence']]} for name in checks}
        if kind == 'client': receipt['installationReceiptSha256'] = candidate.sha(self.output / 'install.json')
        candidate.write(self.output / (kind + '.json'), receipt)
        return receipt

    def refresh(self, receipt, role):
        entry = next(file for file in receipt['evidence'] if file['role'] == role)
        path = self.output / entry['path']; entry.update(bytes=path.stat().st_size, sha256=candidate.sha(path))

    def test_portable_raw_verification_and_client_binding_complete_only_selected_scope(self):
        self.freeze(); self.installed = self.receipt(); self.receipt('client')
        result = candidate.evaluate(self.frozen, [self.output / 'client.json', self.output / 'install.json'])
        self.assertTrue(result['scopes'][0]['evidenceComplete']); self.assertTrue(result['scopes'][0]['qualificationEligible'])
        self.assertEqual('not_run', result['status']); self.assertEqual(8, sum(c['status'] == 'not_run' for c in result['cells']))

    def test_synchronized_five_of_six_or_duplicate_xml_never_passes(self):
        self.freeze()
        xml = b'<testsuite>' + b'<testcase name="copperbench_camp_kit:t01"/>' * 6 + b'</testsuite>'
        self.receipt(xml=xml)
        with self.assertRaises(candidate.portable.EvidenceError): candidate.evaluate(self.frozen, [self.output / 'install.json'])

    def test_five_distinct_cases_with_a_forged_six_case_summary_are_rejected(self):
        self.freeze()
        xml = ('<testsuite>' + ''.join(f'<testcase name="copperbench_camp_kit:t{n:02d}"/>' for n in [1, 2, 3, 4, 11]) + '</testsuite>').encode()
        self.receipt(xml=xml)
        with self.assertRaises(candidate.portable.EvidenceError): candidate.evaluate(self.frozen, [self.output / 'install.json'])

    def test_client_cannot_substitute_another_jar_or_installation_attempt(self):
        self.freeze(); self.installed = self.receipt(); receipt = self.receipt('client')
        receipt['artifactSha256'] = 'd' * 64
        binding_path = self.output / 'client-run_binding.json'; binding = candidate.read(binding_path)
        binding['artifactSha256'] = receipt['artifactSha256']; candidate.write(binding_path, binding)
        self.refresh(receipt, 'run_binding'); candidate.write(self.output / 'client.json', receipt)
        with self.assertRaisesRegex(ValueError, 'same passing installed'):
            candidate.evaluate(self.frozen, [self.output / 'install.json', self.output / 'client.json'])

    def test_package_plan_freezes_shipped_sdk_and_leaves_other_routes_unavailable(self):
        folder = self.repo / 'src/main/resources'; folder.mkdir(parents=True)
        (folder / 'mcreator.conf').write_text('product.name=Copperbench\nproduct.version=0.1.4\n', encoding='utf-8')
        plan = package_candidate.plan(self.repo, 'ubuntu', ['fabric'])
        self.assertEqual(['fabric:G1:ubuntu-xorg', 'fabric:G1:ubuntu-wayland'], plan['scopes'])
        self.assertEqual('unavailable', plan['availability']['neoforge:G1:ubuntu-xorg'])
        self.assertEqual('unavailable', plan['availability']['fabric:G2:ubuntu'])
        self.assertTrue(all(a['path'].startswith('build/export/linux64/sdk/') for a in plan['assets'] if a['role'] in candidate.SDK_ROLES))
        self.assertIn('exportLinuxCandidate', plan['commands'][0])

    def test_g2_plan_requires_both_linux_desktops_and_does_not_inherit_g1(self):
        folder = self.repo / 'src/main/resources'; folder.mkdir(parents=True)
        (folder / 'mcreator.conf').write_text('product.name=Copperbench\nproduct.version=0.1.4\n', encoding='utf-8')
        plan = package_candidate.plan(self.repo, 'ubuntu', ['fabric'], 'G2')
        self.assertEqual(['fabric:G2:ubuntu'], plan['scopes'])
        self.assertEqual({'install:ubuntu:fabric', 'client:xorg:fabric', 'client:wayland:fabric'},
                         set(candidate.required_cells(plan['scopes'][0])))
        self.assertEqual('unavailable', plan['availability']['fabric:G1:ubuntu-xorg'])
        self.assertEqual('unavailable', plan['availability']['neoforge:G2:ubuntu'])
        self.assertIn('G2', plan['commands'][1])
        with self.assertRaisesRegex(ValueError, 'level'):
            package_candidate.plan(self.repo, 'ubuntu', ['fabric'], 'G3')

    def test_old_receipt_or_changed_original_binding_cannot_be_rebound(self):
        self.freeze(); receipt = self.receipt(); path = self.output / 'install.json'
        receipt['candidateId'] = 'sha256:' + '0' * 64; candidate.write(path, receipt)
        with self.assertRaisesRegex(ValueError, 'Old evidence'): candidate.evaluate(self.frozen, [path])
        receipt['candidateId'] = self.metadata['candidateId']
        binding = self.output / 'install-run_binding.json'; raw = candidate.read(binding); raw['candidateId'] = 'sha256:' + '0' * 64
        candidate.write(binding, raw); self.refresh(receipt, 'run_binding'); candidate.write(path, receipt)
        with self.assertRaisesRegex(ValueError, 'another candidate'): candidate.evaluate(self.frozen, [path])

    def test_ambiguous_attempts_and_missing_client_roles_are_rejected(self):
        self.freeze(); self.installed = self.receipt(); receipt = self.receipt('client')
        installed = self.output / 'install.json'; client = self.output / 'client.json'
        with self.assertRaisesRegex(ValueError, 'ambiguous'): candidate.evaluate(self.frozen, [installed, installed])
        removed = next(file for file in receipt['evidence'] if file['role'] == 'screenshot'); receipt['evidence'].remove(removed)
        for check in receipt['checks'].values(): check['evidenceIds'].remove(removed['id'])
        candidate.write(client, receipt)
        with self.assertRaisesRegex(ValueError, 'roles are missing'): candidate.evaluate(self.frozen, [installed, client])

    def test_client_requires_same_world_and_selected_installation(self):
        self.freeze(); self.installed = self.receipt(); receipt = self.receipt('client')
        client = self.output / 'client.json'; installed = self.output / 'install.json'
        with self.assertRaisesRegex(ValueError, 'same passing installed'): candidate.evaluate(self.frozen, [client])
        world_path = self.output / 'client-world_state.json'; world = candidate.read(world_path); world['reopenedWorldId'] = 'different'
        candidate.write(world_path, world); self.refresh(receipt, 'world_state'); candidate.write(client, receipt)
        with self.assertRaisesRegex(ValueError, 'same world'): candidate.evaluate(self.frozen, [installed, client])

    def test_g1_evidence_cannot_satisfy_a_g2_candidate(self):
        self.inputs['scopes'] = ['fabric:G2:windows']; self.inputs['availability']['scopes']['fabric:G2:windows'] = 'ready'
        self.save_inputs(); self.freeze(); receipt = self.receipt(); receipt['level'] = 'G2'
        for name in candidate.G2_CHECKS: receipt['checks'][name] = deepcopy(next(iter(receipt['checks'].values())))
        binding_path = self.output / 'install-run_binding.json'; binding = candidate.read(binding_path); binding['level'] = 'G2'
        candidate.write(binding_path, binding); self.refresh(receipt, 'run_binding'); candidate.write(self.output / 'install.json', receipt)
        with self.assertRaisesRegex(ValueError, 'fixed G1/G2'): candidate.evaluate(self.frozen, [self.output / 'install.json'])

    def test_dirty_source_is_bound_but_cannot_qualify(self):
        (self.repo / 'source.txt').write_text('local changes', encoding='utf-8')
        source = candidate.source_identity(candidate.source_snapshot(self.repo)); self.inputs['source'] = source
        self.inputs['availability']['sourceSha256'] = source['workingTreeSha256']; self.save_inputs()
        self.freeze(); self.installed = self.receipt(); self.receipt('client')
        result = candidate.evaluate(self.frozen, [self.output / 'client.json', self.output / 'install.json'])
        self.assertTrue(result['scopes'][0]['evidenceComplete']); self.assertFalse(result['scopes'][0]['qualificationEligible'])

    def test_g2_ubuntu_requires_both_sessions_and_statuses_remain_distinct(self):
        self.assertEqual(['install:ubuntu:fabric', 'client:xorg:fabric', 'client:wayland:fabric'], candidate.required_cells('fabric:G2:ubuntu'))
        self.freeze()
        for status in ('failed', 'blocked', 'not_run'):
            path = self.output / 'status.json'; candidate.write(path, {'schemaVersion': '1', 'kind': 'candidate-cell-evidence',
                'candidateId': self.metadata['candidateId'], 'cell': 'install:windows:fabric', 'level': 'G1',
                'status': status, 'reason': 'Observed test condition'})
            self.assertEqual(status, candidate.evaluate(self.frozen, [path])['status'])


if __name__ == '__main__': unittest.main()
