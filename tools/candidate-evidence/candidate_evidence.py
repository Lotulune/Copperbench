"""Freeze source/assets and account for independently bound installation and client cells."""
from __future__ import annotations
import argparse
from datetime import datetime, timezone
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import uuid

VERSION = '1'
LOADERS = ('fabric', 'neoforge')
CELLS = {**{f'install:{platform}:{loader}': (platform, loader, 'install')
            for platform in ('windows', 'ubuntu') for loader in LOADERS},
         **{f'client:{desktop}:{loader}': ('windows' if desktop == 'windows' else 'ubuntu', loader, 'client')
            for desktop in ('windows', 'xorg', 'wayland') for loader in LOADERS}}
INSTALL_CHECKS = {'installed_identity', 'native_create', 'build_failure_repair', 'required_cases', 'verified_export',
                  'offline_verification', 'process_reconnect', 'negative_evidence', 'read_only', 'cache_conditions'}
CLIENT_CHECKS = {'installed_identity', 'visible_window', 'player_input', 'mod_behavior', 'save_exit',
                 'same_world_reopen', 'normal_close'}
G2_CHECKS = {'item', 'block', 'recipe', 'function', 'code', 'thirteen_required_cases'}
SOURCE_KEYS = ('commit', 'gitTree', 'workingTreeSha256', 'dirty')
SDK_ROLES = {'python_native', 'python_mcp', 'typescript'}
PLATFORM_ROLES = {'application', 'launcher', 'package', 'portable_package'}
GENERATOR_ROLES = {'generator', 'sample'}
INSTALL_ROLES = {'portable_current', 'portable_historical', 'native_result', 'doctor', 'negative_result', 'cache_conditions', 'run_binding'}
CLIENT_ROLES = {'screenshot', 'actions', 'client_log', 'world_state', 'process_state', 'verification', 'run_binding'}

# Always load the repository's reviewed verifier, never code from an evidence bundle.
_spec = importlib.util.spec_from_file_location('candidate_portable_verifier', Path(__file__).resolve().parents[1] / 'portable-evidence/copperbench_evidence.py')
portable = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(portable)


def require(condition, message):
    if not condition: raise ValueError(message)


def canonical(value):
    return json.dumps(value, sort_keys=True, separators=(',', ':'), ensure_ascii=False, allow_nan=False).encode('utf-8')


def digest(value): return hashlib.sha256(canonical(value)).hexdigest()


def sha(path):
    with path.open('rb') as stream: return hashlib.file_digest(stream, 'sha256').hexdigest()


def read(path):
    require(path.stat().st_size <= 16 * 1024 * 1024, 'JSON evidence exceeds 16 MiB')
    def unique(pairs):
        value = {}
        for key, item in pairs:
            require(key not in value, 'Duplicate JSON key: ' + key)
            value[key] = item
        return value
    return json.loads(path.read_text(encoding='utf-8-sig'), object_pairs_hook=unique,
                      parse_constant=lambda _: require(False, 'Non-finite JSON number'))


def write(path, value): path.write_text(json.dumps(value, ensure_ascii=False, indent=2, allow_nan=False) + '\n', encoding='utf-8')


def member(root, name):
    require(isinstance(name, str) and name and '\\' not in name and ':' not in name
            and all(part not in ('', '.', '..') for part in name.split('/')), 'Invalid relative member path')
    root = root.resolve(); path = root / name
    require(path.resolve().is_relative_to(root), 'Member leaves evidence root')
    for item in [path, *path.parents]:
        require(not item.is_symlink() and not getattr(item, 'is_junction', lambda: False)(), 'Linked evidence is not accepted')
        if item == root: break
    require(path.is_file(), f'Missing regular evidence file: {name}')
    return path


def source_snapshot(repository):
    repository = repository.resolve()
    def git(*args): return subprocess.check_output(['git', *args], cwd=repository)
    commit = git('rev-parse', 'HEAD').decode().strip()
    tree = git('rev-parse', 'HEAD^{tree}').decode().strip()
    require(re.fullmatch('[0-9a-f]{40}', commit) and re.fullmatch('[0-9a-f]{40}', tree), 'Git source identity is unavailable')
    names = sorted(set(git('ls-files', '--cached', '--others', '--exclude-standard', '-z').decode('utf-8').split('\0')) - {''})
    modes = {}
    for entry in git('ls-files', '--stage', '-z').decode('utf-8').split('\0'):
        if not entry: continue
        info, name = entry.split('\t', 1); mode, _, stage = info.split(' ')
        require(stage == '0', 'Cannot freeze a source tree with unresolved merge conflicts')
        modes[name] = mode
    files = []
    for name in names:
        path = repository / name
        if not path.exists():
            files.append({'path': name, 'mode': modes.get(name, '100644'), 'deleted': True}); continue
        path = member(repository, name)
        mode = modes.get(name, '100644') if os.name == 'nt' else ('100755' if path.stat().st_mode & 0o111 else '100644')
        files.append({'path': name, 'mode': mode, 'bytes': path.stat().st_size, 'sha256': sha(path)})
    dirty = bool(git('status', '--porcelain', '--untracked-files=normal'))
    return {'commit': commit, 'gitTree': tree, 'workingTreeSha256': digest(files), 'dirty': dirty, 'files': files}


def source_identity(source): return {key: source[key] for key in SOURCE_KEYS}


def valid_hash(value, length=64):
    require(isinstance(value, str) and re.fullmatch('[0-9a-f]{' + str(length) + '}', value), 'Invalid source or artifact digest')


def valid_source(source):
    require(isinstance(source, dict) and set(source) == set(SOURCE_KEYS), 'Invalid source identity shape')
    valid_hash(source['commit'], 40); valid_hash(source['gitTree'], 40); valid_hash(source['workingTreeSha256'])
    require(type(source['dirty']) is bool, 'Source dirty state must be explicit')


def valid_file(file):
    require(isinstance(file, dict) and type(file.get('bytes')) is int and file['bytes'] >= 0, 'Invalid evidence byte count')
    valid_hash(file.get('sha256'))


def detached_output(repository, path):
    """Outputs must not enter the source snapshot they describe."""
    path = path.resolve(); repository = repository.resolve()
    if path.is_relative_to(repository):
        relative = path.relative_to(repository).as_posix()
        ignored = subprocess.run(['git', 'check-ignore', '--quiet', relative], cwd=repository).returncode == 0
        tracked = subprocess.check_output(['git', 'ls-files', '--', relative], cwd=repository)
        require(ignored and not tracked, 'Write candidate inputs and outputs outside the repository or under a Git-ignored directory')


def scope_parts(scope):
    require(isinstance(scope, str), 'Qualification scope must be a string')
    match = re.fullmatch(r'(fabric|neoforge):(G1|G2):(windows|ubuntu-xorg|ubuntu-wayland|ubuntu)', scope)
    require(match is not None, 'Unknown qualification scope')
    loader, level, desktop = match.groups()
    require((level == 'G1' and desktop != 'ubuntu') or (level == 'G2' and desktop in ('windows', 'ubuntu')),
            'G1 selects a desktop; G2 Ubuntu requires Xorg and Wayland together')
    return loader, level, desktop


def required_cells(scope):
    loader, _, desktop = scope_parts(scope)
    platform = 'windows' if desktop == 'windows' else 'ubuntu'
    desktops = ['xorg', 'wayland'] if desktop == 'ubuntu' else [desktop.removeprefix('ubuntu-')]
    return [f'install:{platform}:{loader}', *[f'client:{item}:{loader}' for item in desktops]]


def valid_selection(source, scopes, availability, assets):
    valid_source(source)
    require(isinstance(scopes, list) and scopes and all(isinstance(s, str) for s in scopes)
            and len(scopes) == len(set(scopes)), 'Select unique scopes')
    require(isinstance(availability, dict) and availability.get('sourceSha256') == source['workingTreeSha256'],
            'Scope availability belongs to another source tree')
    require(isinstance(availability.get('scopes'), dict), 'Explicit scope availability is required')
    for scope, state in availability['scopes'].items():
        scope_parts(scope); require(state in ('ready', 'unavailable'), 'Unknown scope availability')
    for scope in scopes:
        scope_parts(scope)
        require(availability.get('scopes', {}).get(scope) == 'ready', f'Scope is unavailable: {scope}')
    # One level per loader/platform prevents mixing G1 and G2 assertions in the same cell.
    levels = {}
    for scope in scopes:
        _, level, _ = scope_parts(scope)
        for cell in required_cells(scope):
            require(cell not in levels or levels[cell] == level, 'A cell cannot mix G1 and G2 evidence')
            levels[cell] = level
    require(isinstance(assets, list) and assets and all(isinstance(a, dict) for a in assets), 'Candidate assets are required')
    keys = [a.get('id') for a in assets]
    require(all(isinstance(k, str) and re.fullmatch('[a-z0-9][a-z0-9._-]{0,79}', k) for k in keys)
            and len(keys) == len(set(keys)), 'Asset IDs must be unique simple names')
    roles = {(a.get('role'), a.get('platform'), a.get('generatorId')) for a in assets}
    require(len(roles) == len(assets), 'Duplicate asset role/platform/generator identity')
    for role, platform, generator in roles:
        require((role in SDK_ROLES and platform is None and generator is None)
                or (role in PLATFORM_ROLES and platform in ('windows', 'ubuntu') and generator is None)
                or (role in GENERATOR_ROLES and platform is None and generator in ('fabric-26.3', 'neoforge-26.3')),
                'Invalid asset role/platform/generator identity')
    require(all((role, None, None) in roles for role in SDK_ROLES), 'Both Python SDK modules and the TypeScript SDK must be frozen')
    for scope in scopes:
        loader, _, desktop = scope_parts(scope); platform = 'windows' if desktop == 'windows' else 'ubuntu'
        require(all((role, platform, None) in roles for role in ('application', 'launcher', 'package')), f'Missing {platform} product assets')
        require(all((role, None, loader + '-26.3') in roles for role in ('generator', 'sample')), f'Missing {loader} generator/sample')
    return levels


def validate_build(build, source, assets):
    require(build.get('schemaVersion') == VERSION and build.get('kind') == 'candidate-build-receipt'
            and build.get('status') == 'passed', 'A successful candidate build receipt is required')
    require(build.get('sourceBefore') == source == build.get('sourceAfter'), 'Build belongs to another source tree')
    require(isinstance(build.get('commands'), list) and build['commands']
            and all(isinstance(command, list) and command and all(isinstance(arg, str) for arg in command) for command in build['commands']),
            'Original build commands are required')
    recorded = build.get('assets')
    require(isinstance(recorded, list) and len(recorded) == len(assets), 'Build asset inventory is incomplete')
    require({a['id']: {k: v for k, v in a.items() if k != 'path'} for a in recorded}
            == {a['id']: {k: v for k, v in a.items() if k != 'path'} for a in assets}, 'Assets do not match the selected build')


def build_candidate(repository, plan_path, output):
    """Run an operator-selected local build plan and bind before/after source to actual output bytes."""
    repository = repository.resolve(); detached_output(repository, plan_path); detached_output(repository, output)
    plan = read(plan_path); before = source_snapshot(repository)
    require(plan.get('schemaVersion') == VERSION and isinstance(plan.get('commands'), list) and plan['commands'], 'Build commands are required')
    availability = {'sourceSha256': before['workingTreeSha256'], 'scopes': plan['availability']}
    valid_selection(source_identity(before), plan['scopes'], availability, plan['assets'])
    require(not output.exists(), 'Build output must be new'); output.mkdir(parents=True)
    result = {'schemaVersion': VERSION, 'kind': 'candidate-build-receipt', 'status': 'failed',
              'sourceBefore': source_identity(before), 'commands': plan['commands'],
              'startedAt': datetime.now(timezone.utc).isoformat()}
    try:
        for index, command in enumerate(plan['commands']):
            require(isinstance(command, list) and command and all(isinstance(arg, str) for arg in command), 'Expected a build argument vector')
            with (output / f'build-{index + 1}.log').open('wb') as log:
                subprocess.run(command, cwd=repository, stdout=log, stderr=subprocess.STDOUT, check=True)
        after = source_snapshot(repository); result['sourceAfter'] = source_identity(after)
        require(after == before, 'Source changed during candidate build')
        result['assets'] = []
        for asset in plan['assets']:
            path = member(repository, asset['path'])
            result['assets'].append({**asset, 'bytes': path.stat().st_size, 'sha256': sha(path)})
        result['status'] = 'passed'
        inputs = {'schemaVersion': VERSION, 'source': source_identity(before), 'scopes': plan['scopes'],
                  'availability': availability, 'assets': result['assets'], 'buildReceipt': 'build-receipt.json'}
        write(output / 'freeze-inputs.json', inputs)
        return inputs
    except BaseException as failure:
        result['reason'] = str(failure); raise
    finally:
        result['finishedAt'] = datetime.now(timezone.utc).isoformat(); write(output / 'build-receipt.json', result)


def freeze(repository, inputs_path, output):
    repository = repository.resolve(); detached_output(repository, inputs_path); detached_output(repository, output)
    inputs = read(inputs_path); before = source_snapshot(repository)
    require(inputs.get('schemaVersion') == VERSION, 'Unsupported candidate input version')
    require(inputs.get('source') == source_identity(before), 'Source identity is missing or stale')
    scopes, availability, assets = inputs.get('scopes'), inputs.get('availability'), inputs.get('assets')
    valid_selection(source_identity(before), scopes, availability, assets)
    build_path = member(inputs_path.parent, inputs['buildReceipt']); build = read(build_path)
    validate_build(build, source_identity(before), assets)
    require(not output.exists() and output.parent.is_dir(), 'Candidate output must be a new directory with an existing parent')
    staging = output.parent / ('.candidate-staging-' + uuid.uuid4().hex); staging.mkdir()
    try:
        frozen = []
        for asset in assets:
            source = member(repository, asset['path']); relative = 'artifacts/' + asset['id'] + '/' + source.name
            target = staging / relative; target.parent.mkdir(parents=True)
            expected = {'bytes': source.stat().st_size, 'sha256': sha(source)}
            valid_file(asset)
            require(all(asset[key] == value for key, value in expected.items()), 'Asset differs from the selected build')
            shutil.copy2(source, target)
            require(target.stat().st_size == expected['bytes'] and sha(target) == expected['sha256'] == sha(source), 'Asset changed while freezing')
            frozen.append({**{k: asset[k] for k in ('id', 'role', 'platform', 'generatorId') if k in asset}, 'path': relative, **expected})
        require(source_snapshot(repository) == before, 'Source changed while freezing')
        write(staging / 'source-files.json', before)
        shutil.copy2(build_path, staging / 'build-receipt.json')
        payload = {'schemaVersion': VERSION, 'kind': 'copperbench-scoped-candidate',
                   'status': 'frozen_pending_acceptance', 'formalSupportClaim': False,
                   'source': source_identity(before), 'buildReceiptSha256': sha(staging / 'build-receipt.json'),
                   'sourceManifestSha256': sha(staging / 'source-files.json'), 'availability': availability,
                   'scopes': sorted(scopes), 'assets': sorted(frozen, key=lambda a: a['id'])}
        metadata = {**payload, 'candidateId': 'sha256:' + digest(payload)}
        write(staging / 'candidate.json', metadata)
        verify(staging)
        require(source_snapshot(repository) == before, 'Source changed before candidate publication')
        staging.rename(output)
        return metadata
    except BaseException as failure:
        write(staging / 'FAILED.json', {'status': 'failed', 'reason': str(failure)})
        raise


def verify(directory):
    metadata = read(member(directory, 'candidate.json'))
    require(metadata.get('schemaVersion') == VERSION and metadata.get('kind') == 'copperbench-scoped-candidate', 'Unsupported candidate')
    require(metadata.get('status') == 'frozen_pending_acceptance' and metadata.get('formalSupportClaim') is False, 'Freeze cannot claim acceptance or formal support')
    valid_selection(metadata.get('source'), metadata.get('scopes'), metadata.get('availability'), metadata.get('assets'))
    payload = {k: v for k, v in metadata.items() if k != 'candidateId'}
    require(metadata.get('candidateId') == 'sha256:' + digest(payload), 'Candidate identity does not bind its contents')
    manifest = member(directory, 'source-files.json'); source = read(manifest)
    require(isinstance(source.get('files'), list) and source['files'], 'Source file manifest is missing')
    names = []
    for file in source['files']:
        name = file.get('path'); require(isinstance(name, str) and name and '\\' not in name
                and ':' not in name and all(part not in ('', '.', '..') for part in name.split('/')), 'Invalid source member')
        require(file.get('mode') in ('100644', '100755'), 'Unsupported source file mode')
        if file.get('deleted') is not True: valid_file(file)
        names.append(name)
    require(names == sorted(set(names)), 'Source members must be sorted and unique')
    require(sha(manifest) == metadata['sourceManifestSha256'] and digest(source['files']) == source['workingTreeSha256'], 'Source manifest changed')
    require(metadata['source'] == source_identity(source), 'Source identity mismatch')
    build_path = member(directory, 'build-receipt.json')
    require(sha(build_path) == metadata['buildReceiptSha256'], 'Build receipt changed')
    validate_build(read(build_path), metadata['source'], metadata['assets'])
    for asset in metadata['assets']:
        valid_file(asset)
        path = member(directory, asset['path'])
        require(path.stat().st_size == asset['bytes'] and sha(path) == asset['sha256'], f'Candidate asset changed: {asset["id"]}')
    return metadata


def verify_install(receipt, paths):
    identities = []
    for role, scope in [('portable_current', 'current_at_export'), ('portable_historical', 'historical_at_export')]:
        matches = paths[role]; require(len(matches) == 1, 'Select exactly one portable bundle for ' + role)
        path = matches[0]; before = sha(path)
        result = portable.verify(path); identity = result['identity']
        raw = portable.parse_json(portable.read_bundle(path)['verification.json'])
        require(sha(path) == before, 'Portable evidence changed during evaluation')
        require(identity['environment'].get('generatorId') == receipt['generatorId']
                and identity['environment'].get('minecraftVersion') == '26.3', 'Portable evidence uses another exact generator')
        require(identity['inputScope'] == scope, 'Portable current/historical scope mismatch')
        expected_id, minimum = ('camp-kit-native-v1', 6) if receipt['level'] == 'G1' else ('camp-kit-scoped-v1', 13)
        require(identity.get('requiredCaseManifest', {}).get('id') == expected_id and identity['minimumTests'] == minimum,
                'Portable evidence does not meet the fixed G1/G2 business gate')
        require(raw['artifactSha256'] == receipt.get('artifactSha256'), 'Portable mod JAR differs from the installed receipt')
        identities.append((identity['taskId'], identity['sourceSha256'], identity['binding']['workspaceId']))
    require(identities[0] == identities[1], 'Current/historical exports must refer to the same selected acceptance task')


def verify_pass(receipt, candidate, files, root):
    cell = receipt['cell']; platform, loader, kind = CELLS[cell]
    ids = {file['id'] for file in files}; checks = receipt.get('checks', {})
    required = INSTALL_CHECKS if kind == 'install' else CLIENT_CHECKS
    if receipt['level'] == 'G2': required = required | G2_CHECKS
    require(isinstance(checks, dict) and required.issubset(checks), 'Required cell checks are missing')
    for name in required:
        check = checks[name]
        require(isinstance(check, dict) and check.get('status') == 'passed' and isinstance(check.get('evidenceIds'), list)
                and check['evidenceIds'] and all(isinstance(i, str) for i in check['evidenceIds'])
                and set(check['evidenceIds']).issubset(ids), f'Check lacks passing raw evidence: {name}')
    paths = {}
    for file in files: paths.setdefault(file.get('role'), []).append(member(root, file['path']))
    require((INSTALL_ROLES if kind == 'install' else CLIENT_ROLES).issubset(paths), 'Required raw evidence roles are missing')
    require(receipt.get('generatorId') == loader + '-26.3', 'Exact generator does not match this cell')
    expected = {a['id']: a['sha256'] for a in candidate['assets'] if a.get('platform') == platform
                or a.get('role') in SDK_ROLES or a.get('generatorId') == loader + '-26.3'}
    require(receipt.get('observedArtifacts') == expected, 'Observed installed assets do not match the frozen candidate')
    valid_hash(receipt.get('artifactSha256'))
    require(len(paths['run_binding']) == 1, 'One original run binding is required')
    binding = read(paths['run_binding'][0])
    require(all(binding.get(key) == receipt.get(key) for key in
                ('candidateId', 'cell', 'generatorId', 'level', 'observedArtifacts', 'artifactSha256')),
            'Original run evidence belongs to another candidate or artifact')
    if kind == 'install': verify_install(receipt, paths)
    else:
        require(len(paths['world_state']) == 1, 'One world persistence record is required')
        world = read(paths['world_state'][0])
        require(isinstance(world.get('worldId'), str) and world['worldId'].strip() and world.get('worldName')
                and world.get('gameDirectory') and type(world.get('clientPid')) is int and world['clientPid'] > 0
                and world.get('savedWorldId') == world['worldId'] == world.get('reopenedWorldId'),
                'Client evidence must identify and reopen the same world')


def evaluate(directory, receipt_paths):
    candidate = verify(directory)
    rows = {cell: {'cell': cell, 'candidateId': candidate['candidateId'], 'status': 'not_run', 'reason': 'No selected receipt'} for cell in CELLS}
    expected_levels = {cell: scope_parts(scope)[1] for scope in candidate['scopes'] for cell in required_cells(scope)}
    seen = set()
    selected = {}
    for path in receipt_paths:
        receipt_hash = sha(path); receipt = read(path); cell = receipt.get('cell')
        require(receipt.get('schemaVersion') == VERSION and receipt.get('kind') == 'candidate-cell-evidence', 'Unsupported cell receipt')
        require(receipt.get('candidateId') == candidate['candidateId'], 'Old evidence cannot be rebound to this candidate')
        require(cell in expected_levels and cell not in seen, 'Cell is outside the selected scope or has ambiguous attempts')
        require(receipt.get('level') == expected_levels[cell], 'Evidence level does not match the frozen scope')
        status = receipt.get('status'); require(status in ('passed', 'failed', 'blocked', 'not_run'), 'Unknown cell status')
        seen.add(cell); files = receipt.get('evidence', [])
        require(isinstance(files, list), 'Evidence must be a list of original members')
        evidence_ids = set()
        for file in files:
            valid_file(file)
            require(file.get('id') not in evidence_ids and isinstance(file.get('id'), str), 'Duplicate evidence identity')
            evidence_ids.add(file['id']); source = member(path.parent, file['path'])
            require(source.stat().st_size == file['bytes'] and sha(source) == file['sha256'], 'Raw cell evidence changed')
        if status == 'passed':
            verify_pass(receipt, candidate, files, path.parent)
        else:
            require(isinstance(receipt.get('reason'), str) and receipt['reason'].strip(), 'A non-pass requires an explicit reason')
        require(sha(path) == receipt_hash, 'Cell receipt changed during evaluation')
        for file in files:
            source = member(path.parent, file['path'])
            require(source.stat().st_size == file['bytes'] and sha(source) == file['sha256'], 'Raw cell evidence changed during evaluation')
        rows[cell] = {'cell': cell, 'candidateId': candidate['candidateId'], 'status': status,
                      'reason': receipt.get('reason', ''), 'receiptSha256': receipt_hash}
        selected[cell] = receipt
    for cell, receipt in selected.items():
        if CELLS[cell][2] == 'client' and receipt['status'] == 'passed':
            platform, loader, _ = CELLS[cell]; installed = f'install:{platform}:{loader}'
            require(rows[installed]['status'] == 'passed'
                    and receipt.get('installationReceiptSha256') == rows[installed]['receiptSha256']
                    and receipt['artifactSha256'] == selected[installed]['artifactSha256'],
                    'Client must use the same passing installed candidate and mod JAR')
    scopes = []
    for scope in candidate['scopes']:
        cells = required_cells(scope); passed = all(rows[cell]['status'] == 'passed' for cell in cells)
        scopes.append({'scope': scope, 'cells': cells, 'evidenceComplete': passed,
                       'qualificationEligible': passed and not candidate['source']['dirty']})
    statuses = {row['status'] for row in rows.values()}
    overall = 'passed' if statuses == {'passed'} else 'failed' if 'failed' in statuses else 'blocked' if 'blocked' in statuses else 'not_run'
    return {'schemaVersion': VERSION, 'candidateId': candidate['candidateId'], 'status': overall,
            'cells': list(rows.values()), 'scopes': scopes, 'formalSupportClaim': False,
            'producerAuthenticated': False, 'scope': 'evidence_binding_and_completeness_only'}


def main():
    parser = argparse.ArgumentParser(description=__doc__); commands = parser.add_subparsers(dest='command', required=True)
    snapshot = commands.add_parser('source'); snapshot.add_argument('repository', type=Path); snapshot.add_argument('output', type=Path)
    build = commands.add_parser('build'); build.add_argument('repository', type=Path); build.add_argument('plan', type=Path); build.add_argument('output', type=Path)
    create = commands.add_parser('freeze'); create.add_argument('repository', type=Path); create.add_argument('inputs', type=Path); create.add_argument('output', type=Path)
    check = commands.add_parser('verify'); check.add_argument('candidate', type=Path)
    account = commands.add_parser('evaluate'); account.add_argument('candidate', type=Path); account.add_argument('--receipt', action='append', type=Path, default=[])
    args = parser.parse_args()
    try:
        if args.command == 'source':
            detached_output(args.repository, args.output)
            result = source_snapshot(args.repository); require(not args.output.exists(), 'Source snapshot already exists'); write(args.output, result)
            result = {k: result[k] for k in ('commit', 'gitTree', 'workingTreeSha256', 'dirty')}
        elif args.command == 'build': result = build_candidate(args.repository, args.plan, args.output)
        elif args.command == 'freeze': result = freeze(args.repository, args.inputs, args.output)
        elif args.command == 'verify': result = {'status': 'integrity_verified', 'candidateId': verify(args.candidate)['candidateId']}
        else: result = evaluate(args.candidate, args.receipt)
        print(json.dumps(result, ensure_ascii=False)); return 0
    except (ValueError, OSError, KeyError, TypeError, subprocess.SubprocessError, portable.EvidenceError) as error:
        print(json.dumps({'status': 'failed', 'reason': str(error)}, ensure_ascii=False)); return 2


if __name__ == '__main__': sys.exit(main())
