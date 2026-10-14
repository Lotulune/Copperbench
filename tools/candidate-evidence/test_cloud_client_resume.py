import json
from pathlib import Path
import tempfile
import unittest

from cloud_client_resume import prepare_resume


class ResumeBindingTest(unittest.TestCase):
    def setUp(self):
        self.temporary=tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root=Path(self.temporary.name)
        self.source=self.root/'source';self.source.mkdir()
        self.output=self.root/'output';self.output.mkdir()
        self.workspace=self.root/'workspace'
        self.record={'candidateId':'frozen-candidate','cell':'client:xorg:fabric','generatorId':'fabric-26.3',
                     'level':'G1','observedArtifacts':{'application':'unchanged'},'artifactSha256':'same-mod',
                     'sourceBefore':{'src/Main.java':'same-source'},'worldName':'new-unused-name'}
        self.previous={**self.record,'installationReceiptSha256':'original-install-receipt'}
        self.write(self.source/'session.json',self.previous)
        self.write(self.source/'responses/0040/response.json',
                   {'sequence':40,'operation':'archive_world','result':{'status':'archived','attempt':1,'world':'ExactSavedWorld'}})
        self.write(self.source/'requests/0040.json',{'operation':'archive_world','arguments':{'world_name':'ExactSavedWorld'}})
        saved=self.source/'world-after-first-save';saved.mkdir();(saved/'level.dat').write_bytes(b'original-world-bytes')
        self.config={'resumeEvidence':str(self.source),'resumeRun':123}

    @staticmethod
    def write(path,value):
        path.parent.mkdir(parents=True,exist_ok=True);path.write_text(json.dumps(value),encoding='utf-8')

    def test_restores_exact_archived_name_and_bytes_with_provenance(self):
        result=prepare_resume(self.config,self.record,self.workspace,self.output)
        self.assertEqual('ExactSavedWorld',self.record['worldName'])
        self.assertEqual(b'original-world-bytes',(self.workspace/'run/saves/ExactSavedWorld/level.dat').read_bytes())
        self.assertEqual(b'original-world-bytes',(self.output/'world-after-first-save/level.dat').read_bytes())
        self.assertEqual('original-install-receipt',result['sourceInstallationReceiptSha256'])
        self.assertEqual(123,result['sourceRun'])

    def test_rejects_different_mod_inputs_before_copying(self):
        self.previous['sourceBefore']={'src/Main.java':'changed'};self.write(self.source/'session.json',self.previous)
        with self.assertRaisesRegex(ValueError,'another candidate'):
            prepare_resume(self.config,self.record,self.workspace,self.output)
        self.assertFalse(self.workspace.exists())

    def test_does_not_overwrite_existing_world(self):
        target=self.workspace/'run/saves/ExactSavedWorld';target.mkdir(parents=True)
        (target/'level.dat').write_bytes(b'keep-existing')
        with self.assertRaisesRegex(ValueError,'overwrite'):
            prepare_resume(self.config,self.record,self.workspace,self.output)
        self.assertEqual(b'keep-existing',(target/'level.dat').read_bytes())

    def test_rejects_archive_receipt_name_mismatch(self):
        self.write(self.source/'requests/0040.json',{'operation':'archive_world','arguments':{'world_name':'../other'}})
        with self.assertRaisesRegex(ValueError,'different worlds'):
            prepare_resume(self.config,self.record,self.workspace,self.output)
        self.assertFalse(self.workspace.exists())


if __name__=='__main__':unittest.main()
