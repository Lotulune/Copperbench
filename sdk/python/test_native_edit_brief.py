"""Read-only, targeted startup information for the native agent route."""
import json
import unittest
from unittest.mock import Mock, call

from copperbench import Workspace, NativeApiError

MANIFEST = 'src/main/resources/fabric.mod.json'
MAIN = 'src/main/java/dev/example/River.java'


class NativeEditBriefTest(unittest.TestCase):
    def setUp(self):
        self.ws = Workspace.__new__(Workspace)
        self.env = {'generator': {'id': 'fabric-1.21.1', 'minecraftVersion': '1.21.1'},
                    'execution': {'loader': 'fabric', 'testing': {'state': 'not_configured'}}}
        self.ws.get_workspace_environment = Mock(return_value={'data': self.env})
        self.files = {MANIFEST: {'content': json.dumps({'id': 'river', 'entrypoints': {'main': ['dev.example.River']}})},
                      'build.gradle': {'content': 'dependencies {\n  mappings loom.officialMojangMappings()\n}\n'},
                      MAIN: {'relativePath': MAIN, 'ownership': 'generated', 'editable': False,
                             'userCodeBlocks': ['mod init'], 'content': 'large Java source not needed in a brief'}}
        self.ws.read_file = Mock(side_effect=lambda path: {'data': self.files[path]})
        self.ws.query = Mock(side_effect=AssertionError('no inventory scan'))
        self.ws.command = Mock(side_effect=AssertionError('read-only'))

    def test_java_entrypoint_reads_only_named_files_and_returns_core_ownership(self):
        data = self.ws.native_edit_brief()
        self.assertEqual(('ready', MAIN, 'river', 'generated', False, ['mod init'], 'mappings loom.officialMojangMappings()'),
                         tuple(data[k] for k in ('status', 'main', 'modId', 'ownership', 'editable', 'userCodeBlocks', 'mappings')))
        self.assertNotIn('content', data)
        self.ws.get_workspace_environment.assert_called_once_with(field_contracts='summary')
        self.assertEqual([call(MANIFEST), call('build.gradle'), call(MAIN)], self.ws.read_file.call_args_list)

    def test_unrecognized_layouts_stop_without_inventing_a_source_path(self):
        self.env['execution']['loader'] = 'neoforge'
        self.assertEqual('needs_discovery', self.ws.native_edit_brief()['status'])
        self.ws.read_file.assert_not_called()
        self.env['execution']['loader'] = 'fabric'
        for main in ([], ['a.A', 'b.B'], ['a.A::init'], ['../Other'], [{'adapter': 'kotlin', 'value': 'dev.Object'}]):
            with self.subTest(main=main):
                self.files[MANIFEST]['content'] = json.dumps({'id': 'river', 'entrypoints': {'main': main}})
                self.ws.read_file.reset_mock()
                self.assertEqual('custom_entrypoints', self.ws.native_edit_brief()['reason'])
                self.ws.read_file.assert_called_once_with(MANIFEST)

    def test_custom_mappings_and_missing_files_are_distinct_from_denied_reads(self):
        self.files['build.gradle']['content'] += 'mappings project.otherMappings\n'
        self.assertEqual('inspect_build_mappings', self.ws.native_edit_brief()['reason'])
        self.assertEqual([call(MANIFEST), call('build.gradle')], self.ws.read_file.call_args_list)
        self.ws.read_file.side_effect = NativeApiError('source is missing', 'WORKSPACE_SOURCE_IO')
        brief = self.ws.native_edit_brief()
        self.assertEqual(('needs_discovery', MANIFEST, 'WORKSPACE_SOURCE_IO', 'source is missing'),
                         tuple(brief[k] for k in ('status', 'path', 'reason', 'detail')))
        denied = NativeApiError('source access denied', 'WORKSPACE_SOURCE_PATH_INVALID')
        self.ws.read_file.side_effect = denied
        with self.assertRaises(NativeApiError) as exc:
            self.ws.native_edit_brief()
        self.assertIs(denied, exc.exception)
