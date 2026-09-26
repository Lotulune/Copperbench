import unittest
from render import render


class RenderTest(unittest.TestCase):
    def test_preserves_unrelated_code_and_is_idempotent(self):
        source = 'before\r\n// Start of user code block mod init\r\ncustom();\r\n// End of user code block mod init\r\nafter'
        for loader in ('fabric-1.21.1', 'neoforge-1.21.1'):
            result = render(loader, 'dev.example', 'example:machine', source)
            self.assertIn('custom();\r\n', result['mainSourceAfter'])
            again = render(loader, 'dev.example', 'example:machine', result['mainSourceAfter'])
            self.assertFalse(again['mainSourceChanged'])
            self.assertEqual(result['mainSourceAfter'], again['mainSourceAfter'])
            self.assertNotIn('__PACKAGE__', result['command']['initialValues']['code'])
            name = result['command']['name']
            self.assertRegex(name, r'^[a-z][a-z0-9_]{0,63}$')
            self.assertIn('public final class ' + name, result['command']['initialValues']['code'])
            self.assertIn('private ' + name + '()', result['command']['initialValues']['code'])
            self.assertIn('dev.example.' + name + '.init();', result['mainSourceAfter'])

    def test_rejects_wrong_generator_missing_markers_and_conflicting_hook(self):
        with self.assertRaises(ValueError):
            render('fabric-26.2', 'dev.example', 'example:machine', '')
        with self.assertRaises(ValueError):
            render('fabric-1.21.1', 'dev.example', 'example:machine', '')
        source = '// Start of user code block mod init\nother.Stage17MachineRuntime.init();\n// End of user code block mod init'
        with self.assertRaises(ValueError):
            render('fabric-1.21.1', 'dev.example', 'example:machine', source)


if __name__ == '__main__':
    unittest.main()
