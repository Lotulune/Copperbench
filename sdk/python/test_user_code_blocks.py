"""Workspace.insert_user_code: CRLF-safe, idempotent edits of generated user code blocks."""
from __future__ import annotations

import unittest
from unittest.mock import patch

from copperbench import NativeApiError, Workspace

MAIN = 'src/main/java/com/example/emberkit/EmberkitMod.java'


def generated(newline: str) -> str:
    return newline.join([
        'public class EmberkitMod implements ModInitializer {',
        '\t@Override public void onInitialize() {',
        '\t\t// Start of user code block mod init',
        '\t\tSystem.out.println("existing");',
        '\t\t// End of user code block mod init',
        '\t}',
        '}',
        '',
    ])


class UserCodeBlockTest(unittest.TestCase):
    def run_insert(self, content: str, code: str, block: str = 'mod init'):
        workspace = Workspace.__new__(Workspace)
        read = {'data': {'content': content, 'sha256': 'b' * 64, 'userCodeBlocks': ['mod init']}}
        with patch.object(Workspace, 'read_file', return_value=read), \
                patch.object(Workspace, 'write_file', return_value={'status': 'committed'}) as write:
            result = workspace.insert_user_code(MAIN, block, code)
        return result, write

    def test_crlf_files_keep_their_line_endings_and_block_indentation(self):
        result, write = self.run_insert(generated('\r\n'), 'EmberKitContent.register();')
        self.assertEqual({'status': 'committed'}, result)
        path, text = write.call_args.args
        self.assertEqual(MAIN, path)
        self.assertEqual('b' * 64, write.call_args.kwargs['expected_sha256'])
        self.assertIn('\t\tSystem.out.println("existing");\r\n\t\tEmberKitContent.register();\r\n'
                      '\t\t// End of user code block mod init\r\n', text)
        self.assertNotIn('\r\r', text)
        self.assertEqual(text.count('\n'), text.count('\r\n'), 'no LF-only line may be introduced')

    def test_lf_files_and_multi_line_code_keep_relative_indentation(self):
        _, write = self.run_insert(generated('\n'), 'if (enabled) {\n\tregister();\n}\n')
        text = write.call_args.args[1]
        self.assertIn('\t\tif (enabled) {\n\t\t\tregister();\n\t\t}\n\t\t// End of user code block mod init\n', text)
        self.assertNotIn('\r', text)

    def test_code_already_in_the_block_is_not_written_twice(self):
        result, write = self.run_insert(generated('\r\n'), '  System.out.println("existing");  ')
        self.assertEqual('unchanged', result['status'])
        write.assert_not_called()

    def test_missing_or_ambiguous_blocks_are_rejected_without_writing(self):
        for content, block in ((generated('\n'), 'client init'),
                               (generated('\n') + generated('\n'), 'mod init')):
            with self.subTest(block=block):
                with self.assertRaises(NativeApiError) as error:
                    self.run_insert(content, 'register();', block)
                self.assertEqual('NATIVE_USER_CODE_BLOCK_NOT_FOUND', error.exception.code)
        for invalid in ('', '   '):
            with self.assertRaises(ValueError):
                self.run_insert(generated('\n'), invalid)


if __name__ == '__main__':
    unittest.main()
