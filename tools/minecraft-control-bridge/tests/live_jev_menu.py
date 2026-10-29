"""Opt-in real Jev service + simulated menus; sends no real desktop input."""
import argparse
import json
from pathlib import Path
from test_menu_delegation import CreationBackend, creation_fixture, SETTINGS
from test_menu_flow import fixture_profile
from minecraft_control_bridge.engine import Engine


def main(output):
    output.mkdir(parents=True,exist_ok=False)
    backend=CreationBackend();engine=Engine(backend)
    try:
        sid=engine.attach('100',output/'evidence',game_dir=output)['session_id']
        profile=fixture_profile(output)
        entered=engine.menu_flow(sid,'live-jev-enter','enter_world',profile,world_name='PRD17 Scene C v7',controller='jev',timeout_ms=25000)
        assert entered.get('terminal')=='world_visible',entered
        backend.screen='title'
        created_profile=creation_fixture(output,backend)
        created=engine.menu_flow(sid,'live-jev-create','create_world',created_profile,world_name='New Test World',
                                 creation_settings=SETTINGS,controller='jev',timeout_ms=25000)
        assert created.get('terminal')=='world_visible',created
        report={'scope':'real_jev_with_simulated_menu_screens_not_minecraft','passed':True,
                'enter':entered,'create':created,'created_count':backend.created}
    except Exception as error:
        report={'scope':'real_jev_with_simulated_menu_screens_not_minecraft','passed':False,'error':str(error)}
    finally:engine.close()
    (output/'result.json').write_text(json.dumps(report,indent=2),encoding='utf-8')
    print(json.dumps({'passed':report['passed'],'scope':report['scope'],
        'flows':{name:{'elapsed_ms':report[name]['elapsed_ms'],'jev_calls':len(report[name]['jev_calls']),
                       'steps':len(report[name]['steps'])} for name in ('enter','create') if name in report},
        'error':report.get('error')}),flush=True)
    return 0 if report['passed'] else 1


if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--output',required=True,type=Path)
    raise SystemExit(main(parser.parse_args().output.resolve()))
