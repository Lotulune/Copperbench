"""Restore one previously archived test world for a bound reopen-only check."""
import hashlib
import json
from pathlib import Path
import shutil


def require(condition, message):
    if not condition:
        raise ValueError(message)


def read(path):
    return json.loads(Path(path).read_text(encoding='utf-8-sig'))


def sha(path):
    with Path(path).open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def prepare_resume(config, record, workspace_root, evidence_root):
    source = Path(config['resumeEvidence']).resolve()
    previous = read(source/'session.json')
    keys = ('candidateId', 'cell', 'generatorId', 'level', 'observedArtifacts', 'artifactSha256', 'sourceBefore')
    require(all(previous.get(key) == record.get(key) for key in keys),
            'Saved world belongs to another candidate, client cell, artifact or mod input')
    archives = []
    for path in sorted((source/'responses').glob('*/response.json')):
        response = read(path)
        result = response.get('result', {})
        if response.get('operation') == 'archive_world' and result.get('status') == 'archived' and result.get('attempt') == 1:
            request = read(source/'requests'/f"{response['sequence']:04d}.json")
            require(request.get('operation') == 'archive_world', 'Archive request operation mismatch')
            name = request.get('arguments', {}).get('world_name')
            require(name == result.get('world'), 'Archive request and receipt identify different worlds')
            archives.append((name, path))
    require(len(archives) == 1, 'Exactly one successful first-save archive is required')
    name, archive_receipt = archives[0]
    require(isinstance(name, str) and 0 < len(name) <= 32 and name not in ('.', '..')
            and '/' not in name and '\\' not in name and ':' not in name,
            'Invalid exact saved-world directory name')
    saved = source/'world-after-first-save'
    require(saved.is_dir() and (saved/'level.dat').is_file(), 'Original first-save world is missing')
    members = list(saved.rglob('*'))
    require(all(not path.is_symlink() and path.resolve().is_relative_to(saved.resolve()) for path in members),
            'World archive contains a link outside the saved world')
    hashes = {path.relative_to(saved).as_posix(): sha(path) for path in sorted(members) if path.is_file()}
    target = Path(workspace_root)/'run/saves'/name
    snapshot = Path(evidence_root)/'world-after-first-save'
    require(not target.exists() and not snapshot.exists(), 'Refusing to overwrite an existing world or evidence snapshot')
    target.parent.mkdir(parents=True, exist_ok=True)
    shutil.copytree(saved, target)
    shutil.copytree(saved, snapshot)
    require(all(sha(target/relative) == digest == sha(snapshot/relative) for relative, digest in hashes.items()),
            'Copied world differs from the original first-save archive')
    shutil.copy2(source/'session.json', Path(evidence_root)/'resume-source-session.json')
    shutil.copy2(archive_receipt, Path(evidence_root)/'resume-source-archive.json')
    config['worldName'] = name
    record['worldName'] = name
    return {'scope': 'supplementary_reopen_only', 'sourceRun': config['resumeRun'], 'worldName': name,
            'sourceSessionSha256': sha(source/'session.json'), 'sourceArchiveReceiptSha256': sha(archive_receipt),
            'sourceInstallationReceiptSha256': previous['installationReceiptSha256'],
            'candidateId': record['candidateId'], 'artifactSha256': record['artifactSha256'],
            'worldFilesBeforeLaunch': hashes}
