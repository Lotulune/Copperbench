"""Evidence collection must retain failures without reusing old runs or copying authority keys."""
import json
import os
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch
import nightly
import p263_native


class ExactNativeEvidenceTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(); self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)

    def test_backend_forces_fresh_execution_for_each_generator_and_launcher(self):
        for generator, loader_class in [('fabric-26.3', 'Fabric'), ('neoforge-26.3', 'NeoForge')]:
            for hosted in ['true', 'false']:
                with self.subTest(generator=generator, hosted=hosted):
                    output = self.root / f'{generator}-{hosted}'
                    argv = ['p263_native.py', '--generator', generator,
                            '--gradle-home', str(self.root / 'gradle home'), '--output', str(output)]
                    with patch.object(p263_native, 'ROOT', self.root), \
                            patch.object(p263_native, 'source_snapshot', return_value={}), \
                            patch.object(p263_native, 'source_identity', return_value={'commit': 'source', 'dirty': False}), \
                            patch.dict(os.environ, {'GITHUB_ACTIONS': hosted, 'GITHUB_SHA': 'source'}), \
                            patch.object(sys, 'argv', argv), \
                            patch.object(nightly, 'run_check', return_value=1) as run_check:
                        # Stop at the process boundary without starting a Minecraft build.
                        self.assertEqual(1, p263_native.main())
                    run_check.assert_called_once()
                    directory, stage, command = run_check.call_args.args
                    self.assertEqual(output.resolve(), directory)
                    self.assertEqual('backend', stage)
                    if os.name == 'nt' and hosted == 'false':
                        arguments = json.loads(Path(command[-1]).read_text(encoding='utf-8'))
                    else:
                        arguments = command[1:]
                    # Task-scoped --rerun bypasses both FROM-CACHE and UP-TO-DATE,
                    # while leaving compiler and dependency caching available.
                    index = arguments.index('test')
                    self.assertEqual('--rerun', arguments[index + 1])
                    self.assertEqual(f'dev.copperbench.generator.workspace.{loader_class}263AcceptanceTest',
                                     arguments[arguments.index('--tests') + 1])
                    self.assertIn(f'-Dcopperbench.p263.{generator.split("-")[0]}NativeAcceptance=true', arguments)
                    self.assertNotIn('--no-build-cache', arguments)
                    self.assertNotIn('--rerun-tasks', arguments)

    def test_old_or_ambiguous_workspace_cannot_be_selected_for_a_new_run(self):
        old = self.root / 'backend-old'; old.mkdir()
        with self.assertRaises(ValueError): p263_native.new_run(self.root, {old}, 'backend-')
        fresh = self.root / 'backend-current'; fresh.mkdir()
        self.assertEqual(fresh, p263_native.new_run(self.root, {old}, 'backend-'))
        (self.root / 'backend-ambiguous').mkdir()
        with self.assertRaises(ValueError): p263_native.new_run(self.root, {old}, 'backend-')

    def test_failure_collection_keeps_raw_report_and_excludes_authority_keys(self):
        run = self.root / 'delivery-current'; run.mkdir()
        home = self.root / 'build/p263-native-homes/session'; home.mkdir(parents=True)
        (run / 'config.json').write_text(json.dumps({'isolatedUserHome': str(home)}), encoding='utf-8')
        (home / 'private-authority-key').write_text('private fixture', encoding='utf-8')
        report = home / 'task-runs/task/report.xml'; report.parent.mkdir(parents=True)
        report.write_bytes(b'<testsuite failures="1"/>')
        records = run / 'workspace/.copperbench/task-records'; records.mkdir(parents=True)
        (records / 'failed.json').write_text(json.dumps({'task': {'state': 'failed', 'verification': {'reportPath': str(report)}}}), encoding='utf-8')
        (run / 'result.json').write_text('{"status":"failed"}', encoding='utf-8')
        output = self.root / 'collected'; output.mkdir()
        with patch.object(p263_native, 'ROOT', self.root): p263_native.collect_raw([run], output)
        copied = output / 'raw/delivery-current/tasks/failed/report.xml'
        self.assertEqual(report.read_bytes(), copied.read_bytes())
        inventory = json.loads((output / 'raw-inventory.json').read_text(encoding='utf-8'))
        self.assertEqual('raw_delivery_evidence_only', inventory['claim'])
        self.assertFalse(any('private-authority-key' in row['path'] for row in inventory['files']))
        outsider = self.root / 'unrelated.xml'; outsider.write_text('unrelated', encoding='utf-8')
        with self.assertRaises(ValueError): p263_native.bound_path(outsider, [run, home])

    def test_record_cannot_collect_another_sessions_report_or_the_authority_store(self):
        run = self.root / 'delivery-current'; run.mkdir()
        home = self.root / 'build/p263-native-homes/current'; home.mkdir(parents=True)
        (run / 'config.json').write_text(json.dumps({'isolatedUserHome': str(home)}), encoding='utf-8')
        records = run / 'workspace/.copperbench/task-records'; records.mkdir(parents=True)
        for index, path in enumerate([home / 'task-authorizations/private.xml', home.parent / 'unrelated/task-runs/task/report.xml']):
            path.parent.mkdir(parents=True, exist_ok=True); path.write_text('private fixture', encoding='utf-8')
            (records / 'failed.json').write_text(json.dumps({'task': {'verification': {'reportPath': str(path)}}}), encoding='utf-8')
            output = self.root / f'rejected-{index}'; output.mkdir()
            with patch.object(p263_native, 'ROOT', self.root), self.assertRaisesRegex(ValueError, 'outside this run'):
                p263_native.collect_raw([run], output)

    def test_every_exact_generator_has_an_independent_gate_and_new_routes_require_portable_evidence(self):
        ids = {*nightly.GENERATORS, *nightly.NATIVE_GENERATORS}
        self.assertEqual(10, len(ids))
        self.assertEqual({f'{loader}-{version}' for loader in ['fabric', 'neoforge']
                          for version in ['1.20.1', '1.21.1', '26.1.2', '26.2', '26.3']}, ids)
        for generator in nightly.NATIVE_GENERATORS:
            self.assertEqual(('backend', 'native', 'collect', 'offline'), nightly.GATES['generator-' + generator][1])


if __name__ == '__main__': unittest.main()
