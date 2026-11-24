import test from 'node:test';
import assert from 'node:assert/strict';
import { archiveDisplayName, importGenerator } from '../src/components/dataPackImport.ts';

test('an imported pack is named after its ZIP file', () => {
  assert.equal(archiveDisplayName('C:\\Users\\me\\Downloads\\Ember Tweaks.zip'), 'Ember Tweaks');
  assert.equal(archiveDisplayName('/home/me/packs/ember.ZIP'), 'ember');
  assert.equal(archiveDisplayName('/packs/' + 'x'.repeat(80) + '.zip').length, 64);
});

test('the newest available generator that the pack declares is selected', () => {
  const inspection = { supportedGeneratorIds: ['datapack-1.21.1', 'datapack-1.20.1'] };
  assert.equal(importGenerator(inspection, ['datapack-26.1.x', 'datapack-1.21.1', 'datapack-1.20.1']), 'datapack-1.21.1');
  assert.equal(importGenerator(inspection, ['datapack-1.20.1']), 'datapack-1.20.1');
  assert.equal(importGenerator({ supportedGeneratorIds: [] }, ['datapack-1.21.1']), undefined);
});
