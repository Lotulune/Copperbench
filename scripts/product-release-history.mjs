import assert from 'node:assert/strict';

/** Published Beta provenance remains immutable when a later stable version ships. */
export function betaHistoryVersion(product, betaRelease) {
  const version = /^v(\d+\.\d+\.\d+)-beta\.\d+$/.exec(betaRelease?.tag ?? '')?.[1];
  assert.ok(version, 'delivery.betaRelease.tag must be a valid Beta tag');
  if (betaRelease.historical === true) {
    assert.ok(product.channel === 'stable' && betaRelease.status === 'public-prerelease',
      'Only a published Beta may be retained as stable-release history');
    const prior = version.split('.').map(Number);
    const current = product.version.split('.').map(Number);
    const difference = prior.map((value, index) => value - current[index]).find(value => value !== 0) ?? 0;
    assert.ok(difference <= 0, 'Historical Beta cannot be newer than the current product');
  } else {
    assert.equal(version, product.version, 'Active Beta tag must match product.version');
  }
  return version;
}
