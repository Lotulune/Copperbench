// Copyright (C) 2026 Copperbench contributors; SPDX-License-Identifier: GPL-3.0-only
import { createHash } from 'node:crypto';
import { createReadStream, existsSync, mkdirSync, readFileSync, readdirSync, writeFileSync, copyFileSync, lstatSync, readlinkSync, cpSync, rmSync, chmodSync, statSync } from 'node:fs';
import { dirname, resolve, join, relative, basename, sep } from 'node:path';
import { fileURLToPath } from 'node:url';
import { spawnSync } from 'node:child_process';
const source = dirname(fileURLToPath(import.meta.url)), root = resolve(source, '../..');
const args = process.argv.slice(2);
const option = (name, fallback) => { const i = args.indexOf(name); return i >= 0 ? args[i + 1] : fallback; };
const platform = option('--platform', process.platform === 'win32' ? 'windows' : 'linux');
const version = option('--version');
if (!['windows', 'linux'].includes(platform) || !/^\d+\.\d+\.\d+$/.test(version ?? '') || version.split('.').some(p => +p > 65535)) throw new Error('Use windows/linux and a numeric three-part version (each <=65535).');
const work = join(root, 'build/unified-installer', platform);
if (!work.startsWith(join(root, 'build/unified-installer') + sep)) throw new Error('Invalid staging directory');
rmSync(work, { recursive: true, force: true }); mkdirSync(join(work, 'classes'), { recursive: true });
const host = process.platform === 'win32' ? 'win' : 'linux', suffix = process.platform === 'win32' ? '.exe' : '';
const java = resolve(option('--jdk', join(root, `jdk/jbr25_${host}_64`)));
function run(program, argv, cwd = root) {
  const result = spawnSync(program, argv, { cwd, stdio: 'inherit', windowsHide: true });
  if (result.error || result.status !== 0) throw new Error(`${basename(program)} failed: ${result.error?.message ?? result.status}`);
}
function files(dir, predicate) { return readdirSync(dir, { withFileTypes: true }).flatMap(item => item.isDirectory() ? files(join(dir, item.name), predicate) : predicate(item.name) ? [join(dir, item.name)] : []); }
run(join(java, `bin/javac${suffix}`), ['--release', '17', '-encoding', 'UTF-8', '-d', join(work, 'classes'), ...files(join(source, 'src'), name => name.endsWith('.java'))]);
for (const [from, to] of [[join(source, 'assets/landscape.png'), 'landscape.png'], [join(root, 'LICENSE.txt'), 'LICENSE.txt'], [join(root, 'LICENSE-ADDITIONAL-TERMS.md'), 'LICENSE-ADDITIONAL-TERMS.md'], [join(root, 'src/main/resources/net/mcreator/ui/res/icon.png'), 'icon.png']]) copyFileSync(from, join(work, 'classes', to));
const fontDirectory = join(source, 'assets/fonts');
if (createHash('sha256').update(readFileSync(join(fontDirectory, 'NotoSansCJKsc-Regular.otf'))).digest('hex') !== '2c76254f6fc379fddfce0a7e84fb5385bb135d3e399294f6eeb6680d0365b74b') throw new Error('Installer font checksum mismatch');
cpSync(fontDirectory, join(work, 'classes/fonts'), { recursive: true });
const sourceFiles = [join(source, 'build.mjs'), ...files(join(source, 'src'), name => name.endsWith('.java')),
  ...files(join(source, 'assets'), () => true)];
const sourceManifest = sourceFiles.sort().map(file => ({ path: relative(root, file).split(sep).join('/'),
  sha256: createHash('sha256').update(readFileSync(file)).digest('hex') }));
writeFileSync(join(work, 'classes/installer-source.json'), JSON.stringify({ version, files: sourceManifest }, null, 2) + '\n');
const jar = join(work, 'copperbench-setup.jar');
const jarTool = existsSync(join(java, `bin/jar${suffix}`)) ? join(java, `bin/jar${suffix}`) : join(root, `jdk/jdk21_${host}_64/bin/jar${suffix}`);
run(jarTool, ['--create', '--file', jar, '--main-class', 'dev.copperbench.installer.InstallerMain', '-C', join(work, 'classes'), '.']);
if (args.includes('--compile-only')) { console.log(jar); process.exit(0); }
const payload = resolve(option('--payload', join(root, 'build/export', platform === 'windows' ? 'win64' : 'linux64')));
if (!existsSync(payload)) throw new Error(`Application payload missing: ${payload}`);
async function digest(path) { const hash = createHash('sha256'); for await (const chunk of createReadStream(path)) hash.update(chunk); return hash.digest('hex'); }
const manifest = [];
const encoded = value => Buffer.from(value).toString('base64');
async function visit(directory) {
  for (const name of readdirSync(directory).sort()) {
    const file = join(directory, name), info = lstatSync(file), path = relative(payload, file).split(sep).join('/');
    if (/[\r\n\t\\:]/.test(path)) throw new Error(`Unsupported payload filename: ${path}`);
    let mode = info.mode & 0o777;
    if (platform === 'linux' && /^(copperbench\.sh|gradlew|jdk(?:21)?\/bin\/.*|jdk(?:21)?\/lib\/(jspawnhelper|jexec|jcef_helper|cef_server)|gradle-dists\/.*\/bin\/gradle)$/.test(path)) mode |= 0o111;
    if (info.isSymbolicLink()) manifest.push(['L', encoded(path), encoded(readlinkSync(file).split(sep).join('/')), 0, mode].join('\t'));
    else if (info.isDirectory()) { manifest.push(['D', encoded(path), '-', 0, mode].join('\t')); await visit(file); }
    else if (info.isFile()) manifest.push(['F', encoded(path), await digest(file), info.size, mode].join('\t'));
    else throw new Error(`Unsupported payload entry: ${path}`);
  }
}
await visit(payload);
if (!manifest.length) throw new Error('The payload is empty');
const manifestPath = join(work, 'payload.tsv'); writeFileSync(manifestPath, manifest.join('\n') + '\n', 'utf8');
const output = resolve(option('--output', join(root, 'build/export', `Copperbench ${version} ${platform === 'windows' ? 'Windows 64bit.exe' : 'Linux x86_64 Setup.tar.gz'}`)));
mkdirSync(dirname(output), { recursive: true });
if (platform === 'windows') {
  let script = readFileSync(join(source, 'bootstrap.nsi'), 'utf8');
  for (const [key, value] of Object.entries({ VERSION: version, YEAR: new Date().getFullYear(), OUTPUT: output, PAYLOAD: payload, JAR: jar, MANIFEST: manifestPath,
    ICON: join(root, 'src/main/resources/net/mcreator/ui/res/icon.ico') })) script = script.replaceAll(`%${key}%`, String(value));
  const scriptPath = join(work, 'bootstrap.nsi'); writeFileSync(scriptPath, '\uFEFF' + script, 'utf8');
  run(resolve(option('--makensis', join(root, 'build/tools/nsis/makensis.exe'))), ['/V2', scriptPath]);
} else {
  const bundle = join(work, `Copperbench-${version}-Setup`); mkdirSync(bundle);
  cpSync(payload, join(bundle, 'payload'), { recursive: true, dereference: false });
  // Runtime and launcher executability must survive archive extraction.
  for (const line of manifest) {
    const [type, path, , , mode] = line.split('\t');
    if (type !== 'L') chmodSync(join(bundle, 'payload', Buffer.from(path, 'base64').toString()), +mode);
  }
  copyFileSync(jar, join(bundle, 'copperbench-setup.jar')); copyFileSync(manifestPath, join(bundle, 'payload.tsv'));
  copyFileSync(join(root, 'docs/user/graphical-installation.md'), join(bundle, 'Installation - 安装说明.md'));
  writeFileSync(join(bundle, 'Install Copperbench'), '#!/bin/sh\nset -eu\nSETUP_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)\nexec "$SETUP_DIR/payload/jdk/bin/java" -jar "$SETUP_DIR/copperbench-setup.jar" --payload "$SETUP_DIR/payload" --manifest "$SETUP_DIR/payload.tsv" --version ' + version + '\n', { mode: 0o755 });
  run('tar', ['-czf', output, '-C', work, basename(bundle)]);
}
writeFileSync(output + '.build.json', JSON.stringify({ schemaVersion: 1, platform, version, offline: true, sha256: await digest(output), bytes: statSync(output).size, payloadFiles: manifest.length, payloadManifestSha256: await digest(manifestPath) }, null, 2) + '\n');
console.log(`Built offline installer: ${output}`);
