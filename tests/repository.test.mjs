import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';

test('repo.json points to generated builds manifest', async () => {
  const repo = JSON.parse(await readFile('repo.json', 'utf8'));
  assert.equal(repo.manifestVersion, 1);
  assert.deepEqual(repo.pluginLists, [
    'https://raw.githubusercontent.com/ddysiodev/ddys-cloudstream/builds/plugins.json',
  ]);
});

test('package metadata is private and versioned', async () => {
  const pkg = JSON.parse(await readFile('package.json', 'utf8'));
  assert.equal(pkg.name, 'ddys-cloudstream');
  assert.equal(pkg.version, '0.1.0');
  assert.equal(pkg.private, true);
});
