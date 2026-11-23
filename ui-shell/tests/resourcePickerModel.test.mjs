import test from 'node:test';
import assert from 'node:assert/strict';
import { filterResourceChoices, findResourceChoice, sameResourceValue, textureFieldValue } from '../src/components/resourcePickerModel.ts';

test('a texture field stores the name under its texture type, without .png and with subfolders', () => {
  assert.equal(textureFieldValue('src/main/resources/assets/trail/textures/block/copper_lamp.png', 'block'), 'copper_lamp');
  assert.equal(textureFieldValue('src/main/resources/assets/trail/textures/block/ores/copper.png', 'block'), 'ores/copper');
  assert.equal(textureFieldValue('src/main/resources/assets/trail/textures/item/copper_lamp.png', 'block'), null);
  assert.equal(textureFieldValue('src/main/resources/assets/trail/textures/block/copper_lamp.png.mcmeta', 'block'), null);
  assert.equal(textureFieldValue('src\\main\\resources\\assets\\trail\\textures\\Block\\Lamp.PNG', 'block'), 'Lamp');
  // Without a declared type, the folder after textures/ is the type.
  assert.equal(textureFieldValue('assets/trail/textures/item/tools/pick.png'), 'tools/pick');
  assert.equal(textureFieldValue('src/main/pack.png'), null, 'a pack icon is not a texture');
});

test('legacy values with .png select the same texture', () => {
  assert.ok(sameResourceValue('copper_lamp.png', 'copper_lamp'));
  assert.ok(!sameResourceValue('copper_lamp', 'copper_lantern'));
  const choices = [{ value: 'copper_lamp', label: 'copper_lamp', relativePath: 'a/textures/block/copper_lamp.png' }];
  assert.equal(findResourceChoice(choices, 'copper_lamp.png')?.value, 'copper_lamp');
  assert.equal(findResourceChoice(choices, '  '), undefined);
});

test('search matches every term against the value and the path', () => {
  const choices = [
    { value: 'copper_lamp', label: '', relativePath: 'assets/trail/textures/block/copper_lamp.png' },
    { value: 'ores/copper', label: '', relativePath: 'assets/trail/textures/block/ores/copper.png' },
    { value: 'oak_sign', label: '', relativePath: 'assets/trail/textures/block/oak_sign.png' }
  ];
  assert.deepEqual(filterResourceChoices(choices, 'COPPER').map(choice => choice.value), ['copper_lamp', 'ores/copper']);
  assert.deepEqual(filterResourceChoices(choices, 'ores copper').map(choice => choice.value), ['ores/copper']);
  assert.equal(filterResourceChoices(choices, '').length, 3);
});
