"""Author the integrated Camp Kit through the installed Native structured contracts."""
from __future__ import annotations
import hashlib
import json
from pathlib import Path


def setup(workspace, root: Path, command, terminal, save):
    """Caller owns preview selection, task authority, lifecycle and acceptance evidence."""
    ids = {}
    for kind in ('item', 'block', 'recipe', 'function', 'code'):
        environment = workspace.query('get_workspace_environment')['data']
        contract = environment['fieldContracts'][kind]
        assert contract['complete' if kind in ('item', 'recipe') else 'supported'], contract
        assert contract['generatorId'] in ('fabric-26.3', 'neoforge-26.3'), contract
        save('scoped-contract-' + kind, contract)

    def create(kind, name, values):
        result = command(workspace, 'create_mod_element', elementType=kind, name=name, initialValues=values)
        assert result['status'] == 'committed', result
        ids[name] = result['data']['element']['id']
        save('scoped-created-' + name, result)

    def update(name, values):
        result = command(workspace, 'update_mod_element', elementId=ids[name],
                         changes=[{'path':'/' + key, 'value':value} for key,value in values.items()])
        assert result['status'] == 'committed', result
        save('scoped-updated-' + name, result)

    create('item', 'trail_kit', {'displayName':'Camp Kit', 'stackSize':16, 'texture':'minecraft:paper'})
    update('trail_kit', {'displayName':'Copper Camp Kit', 'rarity':'RARE', 'immuneToFire':True,
                         'fuelBurnTime':200, 'compostingLayers':1})
    create('block', 'camp_post', {'displayName':'Camp Post', 'texture':'minecraft:cobblestone'})
    update('camp_post', {'hardness':5, 'resistance':10, 'rotationMode':5, 'maxStackSize':16, 'rarity':'UNCOMMON'})
    recipes = {
        'camp_shaped': {'recipeType':'Crafting', 'recipeShapeless':False,
                        'recipeSlots':['Items.STICK','','','Items.STRING','','','','',''], 'recipeReturnStack':'CUSTOM:trail_kit'},
        'camp_shapeless': {'recipeType':'Crafting', 'recipeShapeless':True,
                           'recipeSlots':['Items.FLINT','Items.PAPER'] + ['']*7, 'recipeReturnStack':'CUSTOM:trail_kit'},
        'camp_smelting': {'recipeType':'Smelting', 'smeltingInputStack':'Items.STRING', 'smeltingReturnStack':'CUSTOM:trail_kit', 'cookingTime':200},
        'camp_blasting': {'recipeType':'Blasting', 'blastingInputStack':'Items.FLINT', 'blastingReturnStack':'CUSTOM:trail_kit', 'cookingTime':100},
        'camp_smoking': {'recipeType':'Smoking', 'smokingInputStack':'Items.PAPER', 'smokingReturnStack':'CUSTOM:trail_kit', 'cookingTime':100},
    }
    for name,values in recipes.items(): create('recipe', name, values)
    for name,count in [('camp_shaped',2),('camp_shapeless',3),('camp_smelting',2)]:
        update(name, {'recipeRetstackSize':count})

    function = 'scoreboard objectives add camp_count dummy\nscoreboard players set camp_probe camp_count 7\nsetblock 12 80 12 minecraft:gold_block\n'
    create('function', 'camp_signal', {'namespace':'mod', 'code':function})
    update('camp_signal', {'code':function.replace('camp_count 7','camp_count 11')})
    body = ('package dev.copperbench.campkit;\npublic final class recovery_probe {\n'
            ' public static int bundles(int n) { return dev.copperbench.campkit.parts.BundleMath.count(n); }\n'
            ' public static int externalStamp() { return dev.copperbench.campkit.parts.PreservationStamp.VALUE; }\n}\n')
    helper = ('package dev.copperbench.campkit.parts;\npublic final class BundleMath {\n'
              ' public static int count(int count) {\n'
              '  if (count < 0) throw new IllegalArgumentException("negative");\n'
              '  return count / 16 + (count % 16 == 0 ? 0 : 1);\n }\n}\n')
    stamp = 'package dev.copperbench.campkit.parts;\npublic final class PreservationStamp { public static final int VALUE = 7; }\n'
    create('code', 'recovery_probe', {'code':body, 'codeFiles':[
        {'path':'parts/BundleMath.java','code':helper}, {'path':'parts/PreservationStamp.java','code':stamp}]})
    # Deliberate IDE-style edit, with no simultaneous API mutation. Regeneration must preserve the bytes.
    external = stamp.replace('= 7','= 41').replace('\n','\r\n').encode('utf-8')
    path = root/'src/main/java/dev/copperbench/campkit/parts/PreservationStamp.java'
    path.write_bytes(external)
    update('recovery_probe', {'description':'Integrated scope; preserve the independently edited helper'})
    terminal(workspace, 'scoped-generated', command(workspace, 'generate_workspace'))
    assert path.read_bytes() == external
    save('scoped-external-helper', {'path':str(path), 'sha256':hashlib.sha256(external).hexdigest(),
                                   'scope':'Explicit external edit, then Native metadata update and regeneration'})
    snapshot = {}
    for name,identifier in ids.items():
        save('scoped-editor-' + name, workspace.query('get_mod_element_editor', elementId=identifier))
        file = root/'elements'/(name + '.mod.json')
        snapshot[name] = {'elementId':identifier, 'definition':json.loads(file.read_text(encoding='utf-8'))['definition']}
    save('scoped-elements', snapshot)
    return snapshot


def verify_reopened(workspace, root: Path, expected, save):
    """Requery all five kinds after a new Native process, preserving serialized definitions."""
    for name, prior in expected.items():
        save('scoped-reopened-' + name, workspace.query('get_mod_element_editor', elementId=prior['elementId']))
        stored = json.loads((root/'elements'/(name+'.mod.json')).read_text(encoding='utf-8'))['definition']
        assert stored == prior['definition'], name
    stamp = root/'src/main/java/dev/copperbench/campkit/parts/PreservationStamp.java'
    assert b'VALUE = 41' in stamp.read_bytes()
