import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { spawnSync } from 'node:child_process';

const root = fileURLToPath(new URL('../', import.meta.url));
const common = 'tests/reference-engine/common';
const android = 'tests/reference-engine/android';

test('an isolated mobile source tree regenerates every shipped engine module and detects drift without rewriting it', () => {
  const isolated = fs.mkdtempSync(path.join(os.tmpdir(), 'inzira-engine-'));
  try {
    // Only this repository's sources, generator and pinned compiler are available.
    for (const relative of ['scripts/bundle-shared-logic.mjs', 'scripts/native-pdf-module.mjs', 'tests/reference-engine/source']) {
      fs.mkdirSync(path.dirname(path.join(isolated, relative)), { recursive: true });
      fs.cpSync(path.join(root, relative), path.join(isolated, relative), { recursive: true });
    }
    fs.mkdirSync(path.join(isolated, 'node_modules'), { recursive: true });
    fs.symlinkSync(path.join(root, 'node_modules/typescript'), path.join(isolated, 'node_modules/typescript'), 'dir');
    // An unrelated working directory must not affect source/dependency resolution.
    const run = (...args) => spawnSync(process.execPath, [path.join(isolated, 'scripts/bundle-shared-logic.mjs'), ...args], { cwd: os.tmpdir(), encoding: 'utf8' });
    const generated = run();
    assert.equal(generated.status, 0, generated.stderr);
    for (const relative of [common, android]) {
      for (const file of fs.readdirSync(path.join(isolated, relative))) {
        assert.equal(fs.readFileSync(path.join(isolated, relative, file), 'utf8'), fs.readFileSync(path.join(root, relative, file), 'utf8'), file);
      }
    }
    assert.equal(run('--check').status, 0);
    const target = path.join(isolated, common, 'logic.js');
    fs.appendFileSync(target, '\n// stale output\n');
    const stale = fs.readFileSync(target, 'utf8');
    const checked = run('--check');
    assert.equal(checked.status, 1);
    assert.match(checked.stderr, /logic\.js/);
    assert.equal(fs.readFileSync(target, 'utf8'), stale);
    fs.rmSync(path.join(isolated, android, 'pdf-import.js'));
    assert.match(run('--check').stderr, /pdf-import\.js/);
    assert.equal(run().status, 0);
    assert.equal(run('--check').status, 0);
    assert.notEqual(run('/missing/website').status, 0);
  } finally {
    fs.rmSync(isolated, { recursive: true, force: true });
  }
});
