import test from 'node:test';
import assert from 'node:assert/strict';
import { offeredElementTypes } from '../src/components/offeredElementTypes.ts';

const catalog = ['block', 'item', 'recipe', 'procedure', 'function', 'loottable', 'achievement', 'damagetype', 'enchantment',
  'biome', 'dimension', 'feature', 'structure', 'code'];

test('the create dialog lists only the types Core reports for the workspace generator', () => {
  const dataPack = ['structure', 'recipe', 'function', 'loottable', 'achievement', 'biome', 'dimension', 'feature', 'damagetype', 'enchantment'];
  const offered = offeredElementTypes(catalog, dataPack);
  assert.deepEqual([...offered].sort(), [...dataPack].sort());
  assert.ok(!offered.includes('block') && !offered.includes('item'), 'a data pack is not offered blocks or items');
  assert.deepEqual(offered, catalog.filter(type => dataPack.includes(type)), 'catalog order is kept');
});

test('before Core reports types the whole catalog stays', () => {
  assert.deepEqual(offeredElementTypes(catalog, undefined), catalog);
  assert.deepEqual(offeredElementTypes(catalog, []), catalog);
});
