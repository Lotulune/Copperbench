import unittest
import contextlib
import io
from unittest.mock import Mock
import copperbench as cb
import copperbench_scripting as scripting


class ScriptingTest(unittest.TestCase):
    def setUp(self):
        self.previous = scripting._workspace
        self.workspace = Mock()
        self.workspace.operations = ('get_workbench', 'build_workspace', 'update_mod_element')
        cb.use_workspace(self.workspace)
        self.addCleanup(cb.use_workspace, self.previous)

    def test_core_operations_are_discoverable_and_keep_query_command_boundaries(self):
        self.assertIn('workspace', dir(cb.ops))
        cb.ops.get_workbench()
        self.workspace.query.assert_called_once_with('get_workbench')
        cb.ops.workspace.build(expected_revision=7)
        self.workspace.command.assert_called_once_with('build_workspace', expected_revision=7, scope='workspace')
        with self.assertRaises(AttributeError):
            cb.ops.invented_operation()

    def test_registered_operator_checks_poll_and_can_be_unregistered(self):
        calls = []
        class Example(cb.types.Operator):
            idname = 'test.example'
            enabled = False
            @classmethod
            def poll(cls, context): return cls.enabled
            def execute(self, context, value=0): calls.append(value)
        cb.utils.register_class(Example)
        try:
            with self.assertRaises(RuntimeError): cb.ops.test.example()
            Example.enabled = True
            cb.ops.test.example(value=42)
            self.assertEqual([42], calls)
            with self.assertRaises(ValueError): cb.utils.register_class(Example)
        finally:
            cb.utils.unregister_class(Example)
        with self.assertRaises(AttributeError): cb.ops.test.example()

    def test_timers_unregister_and_do_not_repeat_after_none(self):
        timers = scripting.Timers()
        calls = []
        def once(): calls.append(1)
        timers.register(once)
        timers._tick()
        timers._tick()
        self.assertEqual([1], calls)
        self.assertFalse(timers.is_registered(once))
        timers.register(once, first_interval=10)
        timers.unregister(once)
        timers._tick()
        self.assertEqual([1], calls)

    def test_text_blocks_and_help_are_available_without_mcp(self):
        texts = scripting.Texts()
        text = texts.new('example.py', 'x = 1\n')
        text.write('print(x)\n')
        self.assertEqual('x = 1\nprint(x)\n', text.as_string())
        text.clear()
        self.assertEqual('', text.as_string())
        self.assertIn('cb.context', cb.api_help())

    def test_one_broken_handler_does_not_hide_the_event_from_other_handlers(self):
        app = scripting.App()
        calls = []
        self.workspace.get_context.return_value = {'selectionVersion': 0}
        self.workspace.revision = 1
        def broken(context): raise ValueError('handler failed')
        app.handlers.workspace_update.extend([broken, lambda context: calls.append(self.workspace.revision)])
        app.process_events()
        self.workspace.revision = 2
        with contextlib.redirect_stderr(io.StringIO()): app.process_events()
        self.assertEqual([2], calls)

    def test_operator_metadata_must_be_serializable_before_registration(self):
        class Invalid(cb.types.Operator):
            idname = 'test.invalid'
            label = object()
        with self.assertRaises(TypeError): cb.utils.register_class(Invalid)
        self.assertNotIn(Invalid.idname, scripting._registered)


if __name__ == '__main__': unittest.main()
