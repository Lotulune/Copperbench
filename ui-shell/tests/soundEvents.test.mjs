import test from 'node:test';
import assert from 'node:assert/strict';
import { defaultSoundEventName, soundFileValue } from '../src/components/soundEvents.ts';

test('a sound event names its file under sounds/ without .ogg', () => {
  assert.equal(soundFileValue('src/main/resources/assets/trail/sounds/lamp_click.ogg'), 'lamp_click');
  assert.equal(soundFileValue('assets/trail/sounds/block/lamp/click.OGG'), 'block/lamp/click');
  assert.equal(soundFileValue('assets/trail/sounds/lamp_click.wav'), null, 'sounds.json only plays .ogg');
  assert.equal(soundFileValue('assets/trail/music/theme.ogg'), null);
});

test('the default event name uses dots between folders', () => {
  assert.equal(defaultSoundEventName('block/lamp/click'), 'block.lamp.click');
  assert.equal(defaultSoundEventName('Lamp Click'), 'lamp_click');
});
