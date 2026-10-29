import json
import tempfile
import threading
import time
import unittest
from pathlib import Path
from unittest.mock import patch

from PIL import Image, ImageDraw
from test_menu_flow import MenuBackend, fixture_profile, BOXES, ENTRY
from minecraft_control_bridge.engine import Engine
from minecraft_control_bridge.errors import BridgeError
from minecraft_control_bridge.menu_profile import build_profile

SETTINGS = {"game_mode":"creative", "world_type":"superflat", "allow_commands":True, "generate_structures":False}


class Advice:
    choice = "PROCEED"
    hook = None
    def __init__(self, **_): pass
    def __enter__(self): return self
    def __exit__(self,*_): pass
    def choose(self,state,goal,candidates):
        if self.hook: self.hook(state)
        return {"choice":self.choice,"confidence":.9,"probabilities":{k:float(k==self.choice) for k in candidates},
                "model":"test-jev","elapsed_ms":1,"executed":False}


def creation_fixture(root, backend):
    captures=root/'creation-captures';captures.mkdir()
    # Empty filtered list: no name is fabricated for a world that doesn't exist yet.
    image=backend.pictures['world_list'].copy()
    ImageDraw.Draw(image).rectangle((225,100,675,351),fill=(20,30,40))
    backend.pictures['world_list']=image
    states={}
    for name in BOXES:
        states[name]={"markers":{role:{"box":box} for role,box in BOXES[name].items()}}
    for name,role,y in [('create_default','name_label',70),('create_named','mode',180),('create_ready','submit',350)]:
        image=Image.new('RGB',(854,480),(20,30,40));draw=ImageDraw.Draw(image)
        draw.text((300,30),name,fill='white')
        draw.text((300,y+3),role,fill='white')
        markers={'heading':{'box':[290,25,230,25]},role:{'box':[290,y,230,25]}}
        if name=='create_ready':
            draw.text((230,110),'New Test World',fill='white')
            markers['world_name']={'box':[225,105,250,25]}
        backend.pictures[name]=image
        states[name]={'markers':markers}
    states['playing']['title_suffix']=' - Singleplayer'
    for name in states:
        path=captures/(name+'.png');backend.pictures[name].save(path)
        states[name]['source']=str(path)
    spec={'world_name':'New Test World','capture_size':[854,480],'states':states,
          'world_search_box':[225,45,400,35],'empty_below_entry':[225,176,450,175],
          'empty_world_list':[225,95,450,256],
          'creation':{'settings':SETTINGS,'steps':[
              {'state':'create_default','action':'set_world_name','name_box':[225,105,350,30]},
              {'state':'create_named','action':'click','role':'mode'},
              {'state':'create_ready','action':'submit','role':'submit'}]}}
    return build_profile(spec,root/'creation-profile')['profile_path']


class CreationBackend(MenuBackend):
    def __init__(self):
        super().__init__();self.created=0
    def button(self,name,down):
        screen=self.screen
        super().button(name,down)
        if down or name!='left' or self.frozen:return
        y=self.cursor[1]
        if screen=='world_list' and 430<=y<455:self.screen='create_default'
        elif screen=='create_named' and 180<=y<205:self.screen='create_ready'
        elif screen=='create_ready' and 350<=y<375:
            self.created+=1;self.screen='playing'
    def type_character(self,character):
        super().type_character(character)
        if self.screen=='create_default':self.screen='create_named'


class DelegationTests(unittest.TestCase):
    def setUp(self):
        self.tmp=tempfile.TemporaryDirectory();self.root=Path(self.tmp.name)
        self.backend=CreationBackend();self.profile=fixture_profile(self.root)
        self.engine=Engine(self.backend)
        self.sid=self.engine.attach('100',self.root/'evidence',game_dir=self.root)['session_id']
        Advice.choice='PROCEED';Advice.hook=None
    def tearDown(self):
        self.engine.close();self.tmp.cleanup()
    def enter(self):
        return self.engine.menu_flow(self.sid,'enter','enter_world',self.profile,
                    world_name='PRD17 Scene C v7',controller='jev')
    def create(self,**kwargs):
        return self.engine.menu_flow(self.sid,'create','create_world',self.profile,
                    world_name='New Test World',creation_settings=SETTINGS,**kwargs)

    @patch('minecraft_control_bridge.menu_jev.JevClient',Advice)
    def test_jev_runs_entire_navigation_locally_and_keeps_provenance(self):
        result=self.enter()
        self.assertEqual('world_visible',result.get('terminal'),result)
        self.assertEqual(3,len(result['jev_calls']))
        self.assertTrue(all(x['frame_id'] and x['choice']=='PROCEED' for x in result['jev_calls']))
        self.assertFalse(result['gameplay_verified'])

    @patch('minecraft_control_bridge.menu_jev.JevClient',Advice)
    def test_wait_never_dispatches_input_or_silently_falls_back(self):
        Advice.choice='WAIT'
        result=self.enter()
        self.assertEqual('JEV_REQUESTED_STOP',result['error']['code'])
        self.assertFalse(self.backend.events)

    @patch('minecraft_control_bridge.menu_jev.JevClient',Advice)
    def test_screen_changed_during_jev_wait_is_recaptured_and_blocked(self):
        Advice.hook=staticmethod(lambda _:setattr(self.backend,'screen','unknown'))
        result=self.enter()
        self.assertEqual('MENU_STATE_CHANGED',result['error']['code'])
        self.assertFalse(self.backend.events)

    @patch('minecraft_control_bridge.menu_jev.JevClient',Advice)
    def test_f8_is_responsive_while_jev_request_is_pending(self):
        Advice.hook=staticmethod(lambda _:time.sleep(.5))
        timer=threading.Timer(.05,lambda:setattr(self.backend,'emergency',True));timer.start()
        started=time.monotonic()
        result=self.enter();timer.join()
        self.assertEqual('STOPPED',result['error']['code'])
        self.assertLess(time.monotonic()-started,.4)
        self.assertFalse(self.backend.events)

    @patch('minecraft_control_bridge.menu_jev.JevClient',Advice)
    def test_jev_error_is_returned_without_input(self):
        def fail(_):raise BridgeError('JEV_KEY_REQUIRED','missing test key')
        Advice.hook=staticmethod(fail)
        result=self.enter()
        self.assertEqual('JEV_KEY_REQUIRED',result['error']['code'])
        self.assertFalse(self.backend.events)

    @patch('minecraft_control_bridge.menu_jev.JevClient',Advice)
    def test_create_world_once_with_exact_name_and_reviewed_settings(self):
        self.profile=creation_fixture(self.root,self.backend)
        result=self.create(controller='jev')
        self.assertEqual('world_visible',result.get('terminal'),result)
        self.assertTrue(result['creation_submitted'])
        self.assertEqual(1,self.backend.created)
        self.assertEqual(6,len(result['steps']))
        self.assertEqual(6,len(result['jev_calls']))
        replay=self.create(controller='jev')
        self.assertTrue(replay['replayed']);self.assertEqual(1,self.backend.created)

    def test_create_rejects_settings_mismatch_before_input(self):
        self.profile=creation_fixture(self.root,self.backend)
        with self.assertRaises(BridgeError) as caught:
            self.engine.menu_flow(self.sid,'bad','create_world',self.profile,world_name='New Test World',creation_settings={})
        self.assertEqual('MENU_CREATION_SETTINGS_MISMATCH',caught.exception.code)
        self.assertFalse(self.backend.events)

    def test_existing_filtered_world_blocks_creation(self):
        self.profile=creation_fixture(self.root,self.backend)
        ImageDraw.Draw(self.backend.pictures['world_list']).text((230,110),'New Test World',fill='white')
        result=self.create()
        self.assertEqual('MENU_WORLD_ALREADY_EXISTS',result['error']['code'])
        self.assertEqual(0,self.backend.created)

    def test_unrecognized_final_form_never_submits(self):
        self.profile=creation_fixture(self.root,self.backend)
        self.backend.screen='create_ready'
        image=self.backend.pictures['create_ready'];ImageDraw.Draw(image).rectangle((225,105,475,130),fill=(20,30,40))
        result=self.create(timeout_ms=3000)
        self.assertEqual('blocked',result['status']);self.assertEqual(0,self.backend.created)
        self.assertFalse(self.backend.events)

    def test_wait_for_world_never_clicks_or_recreates(self):
        self.backend.screen='playing'
        result=self.engine.menu_flow(self.sid,'wait','wait_for_world',self.profile,world_name='PRD17 Scene C v7')
        self.assertEqual('world_visible',result['terminal']);self.assertFalse(self.backend.events)


if __name__=='__main__':unittest.main()
