"""Native narrow/normal/large-text layout evidence, dedicated emulator only."""
import os,re,shutil,subprocess,time
import xml.etree.ElementTree as ET
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
SERIAL=os.environ.get('HOME_REMOTE_TEST_SERIAL','emulator-5580')
assert SERIAL.startswith('emulator-')
ADB=shutil.which('adb') or 'adb'
def adb(*args):return subprocess.check_output([ADB,'-s',SERIAL,*args],encoding='utf-8',errors='replace',timeout=30)
def ui():
    raw=adb('shell','am','instrument','-w','local.akenori.testdriver/local.akenori.receiver.DumpInstrumentation')
    return list(ET.fromstring(re.search(r'hierarchy=(<hierarchy>.*</hierarchy>)',raw,re.S).group(1)).iter('node'))
def capture(name):
    (ROOT/'verification'/name).write_bytes(subprocess.check_output([ADB,'-s',SERIAL,'exec-out','screencap','-p']))
try:
    for width,height,font,label in [(640,1280,'1.0','320'),(720,1600,'1.0','360'),(840,1800,'1.3','420-large-text')]:
        adb('shell','wm','size',f'{width}x{height}');adb('shell','wm','density','320')
        adb('shell','settings','put','system','font_scale',font)
        adb('shell','am','force-stop','local.akenori.remote')
        adb('shell','am','start','-W','-n','local.akenori.remote/.MainActivity');time.sleep(2)
        nodes=ui()
        for title in ('F−','F+'):
            assert any(n.get('text')==title for n in nodes),f'{label}: focus hidden'
        for node in nodes:
            if node.get('class')!='android.widget.Button':continue
            left,top,right,bottom=map(int,re.findall(r'-?\d+',node.get('bounds')))
            if bottom>top and right>left:assert 0<=left<right<=width,f'{label}: horizontal button overflow'
        capture('layout-'+label+'.png')
        power=next(n for n in nodes if n.get('content-desc')=='Питание проектора')
        left,top,right,bottom=map(int,re.findall(r'\d+',power.get('bounds')))
        adb('shell','input','tap',str((left+right)//2),str((top+bottom)//2));time.sleep(.3)
        nodes=ui();assert any(n.get('text')=='Питание проектора' for n in nodes)
        assert any(n.get('text','').casefold()=='закрыть' for n in nodes),'Power dialog has no close control'
        capture('power-'+label+'.png');adb('shell','input','keyevent','4')
        print('PASS: focus, directional-pad bounds and power dialog at '+label,flush=True)
finally:
    adb('shell','settings','put','system','font_scale','1.0');adb('shell','wm','size','720x1600');adb('shell','wm','density','320')
    adb('shell','am','force-stop','local.akenori.remote');adb('shell','am','start','-W','-n','local.akenori.remote/.MainActivity')
