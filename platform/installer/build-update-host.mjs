// Copyright (C) 2026 Copperbench contributors; SPDX-License-Identifier: GPL-3.0-only
import { readFileSync, writeFileSync, mkdirSync } from 'node:fs';
import { resolve, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { spawnSync } from 'node:child_process';
const root = resolve(dirname(fileURLToPath(import.meta.url)), '../..');
const version = process.argv[2];
if (!/^\d+\.\d+\.\d+$/.test(version ?? '')) throw new Error('A numeric product version is required');
const output = resolve(root, 'build/installer');
mkdirSync(output, { recursive: true });
let script = '!define UPDATE_HOST\n' + readFileSync(resolve(root, 'platform/installer/bootstrap.nsi'), 'utf8');
for (const [key, value] of Object.entries({ VERSION: version, YEAR: new Date().getFullYear(),
  OUTPUT: resolve(output, 'copperbench-update-host.exe'), ICON: resolve(root, 'src/main/resources/net/mcreator/ui/res/icon.ico') })) {
  script = script.replaceAll(`%${key}%`, String(value));
}
const source = resolve(output, 'update-host.nsi');
writeFileSync(source, '\uFEFF' + script, 'utf8');
const result = spawnSync(process.platform === 'win32' ? resolve(root, 'build/tools/nsis/makensis.exe') : 'makensis', ['/V2', source], { stdio: 'inherit', windowsHide: true });
if (result.error || result.status !== 0) throw new Error(`Update host compilation failed: ${result.error?.message ?? result.status}`);
