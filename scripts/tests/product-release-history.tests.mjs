import test from 'node:test';
import assert from 'node:assert/strict';
import { betaHistoryVersion } from '../product-release-history.mjs';

test('a stable patch retains the original published Beta version', () => {
  assert.equal(betaHistoryVersion({ version: '0.1.1', channel: 'stable' },
    { tag: 'v0.1.0-beta.4', status: 'public-prerelease', historical: true }), '0.1.0');
});

test('active Beta releases still require the current product version', () => {
  assert.equal(betaHistoryVersion({ version: '0.1.1', channel: 'beta' },
    { tag: 'v0.1.1-beta.1' }), '0.1.1');
  assert.throws(() => betaHistoryVersion({ version: '0.1.1', channel: 'beta' },
    { tag: 'v0.1.0-beta.4' }), /match product.version/);
});

test('unpublished, future, malformed and non-stable histories are rejected', () => {
  for (const beta of [
    { tag: 'v0.1.0-beta.4', status: 'ready' },
    { tag: 'v0.2.0-beta.1', status: 'public-prerelease' },
    { tag: 'v0.1.0-preview.8', status: 'public-prerelease' }
  ]) assert.throws(() => betaHistoryVersion({ version: '0.1.1', channel: 'stable' },
    { ...beta, historical: true }));
  assert.throws(() => betaHistoryVersion({ version: '0.1.1', channel: 'beta' },
    { tag: 'v0.1.0-beta.4', status: 'public-prerelease', historical: true }), /published Beta/);
});
