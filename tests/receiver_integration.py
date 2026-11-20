import os, shutil
"""Real Android Accessibility + HTTP integration, dedicated emulator only."""
import http.client
import json
import re
import subprocess
import time
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ADB = shutil.which('adb') or 'adb'
SERIAL = os.environ.get('HOME_REMOTE_TEST_SERIAL','emulator-5580')
assert SERIAL.startswith('emulator-'), 'Integration tests require a dedicated emulator'
(ROOT/'verification').mkdir(exist_ok=True)
count = 0

def adb(*args):
    return subprocess.check_output([ADB, '-s', SERIAL, *args], cwd=ROOT, encoding='utf-8', errors='replace', timeout=45)

def check(condition, label):
    global count
    assert condition, label
    count += 1

def ui():
    output = adb('shell','am','instrument','-w','local.akenori.testdriver/local.akenori.receiver.DumpInstrumentation')
    match = re.search(r'INSTRUMENTATION_RESULT: hierarchy=(<hierarchy>.*</hierarchy>)',output,re.S)
    assert match, output
    return ET.fromstring(match.group(1))

def code():
    return next(n.get('text') for n in ui().iter('node') if re.fullmatch(r'\d{6}', n.get('text', '')))

def call(path, data=None, token='', method='POST'):
    conn = http.client.HTTPConnection('127.0.0.1', 8788, timeout=10)
    headers = {'Content-Type': 'application/json', 'Authorization': 'Bearer '+token}
    conn.request(method, path, None if data is None else json.dumps(data).encode(), headers)
    response = conn.getresponse()
    status, payload = response.status, response.read()
    conn.close()
    return status, json.loads(payload)

def wait_screen(token,predicate):
    deadline=time.monotonic()+1.2
    while True:
        screen=call('/ui',{},token)[1]
        if predicate(screen) or time.monotonic()>=deadline:return screen
        time.sleep(.025)

def renew():
    adb('shell', 'am', 'start', '-W', '-n', 'local.akenori.receiver/.MainActivity')
    node = next(n for n in ui().iter('node') if n.get('text', '').casefold() == 'Новый код подключения'.casefold())
    x1,y1,x2,y2 = map(int,re.findall(r'\d+',node.get('bounds')))
    adb('shell','input','tap',str((x1+x2)//2),str((y1+y2)//2))
    confirmation=next((n for n in ui().iter('node') if n.get('text','').casefold()=='Заменить телефон'.casefold()),None)
    if confirmation is not None:
        x1,y1,x2,y2=map(int,re.findall(r'\d+',confirmation.get('bounds')))
        adb('shell','input','tap',str((x1+x2)//2),str((y1+y2)//2))
    time.sleep(1.2)
    return code()

adb('install','-r',str(ROOT/'receiver/build/outputs/apk/debug/receiver-debug.apk'))
adb('install','-r',str(ROOT/'testdriver/build/outputs/apk/debug/testdriver-debug.apk'))
adb('shell','settings','put','secure','enabled_accessibility_services','local.akenori.receiver/.ControlService')
adb('shell','settings','put','secure','accessibility_enabled','1')
adb('forward','tcp:8788','tcp:8787')
time.sleep(1)
pin = renew()
check(call('/health',method='GET')[1].get('service')=='akenori-receiver','health')
check(call('/status',{})[0]==401,'unauthorized status denied')
check(call('/key',{'key':'home'})[0]==401,'unauthorized command denied')
check(call('/diagnostics/power',{})[0]==401,'unauthorized hardware diagnostics denied')
check(call('/power/control',{'action':'projection_off'})[0]==401,'unauthorized power command denied')
wrong = '000000' if pin!='000000' else '000001'
check(call('/pair',{'pin':wrong})[0]==401,'wrong PIN denied')
status, result = call('/pair',{'pin':pin})
check(status==200 and len(result.get('token',''))==64,'pair with PIN')
token = result['token']
check(call('/pair',{'pin':pin})[0]==401,'PIN single use')
check(call('/status',{},token)[1].get('accessibility') is True,'Android service active')
muted=call('/key',{'key':'mute'},token)[1]
restored=call('/key',{'key':'mute'},token)[1]
check(muted.get('ok') is True and isinstance(muted.get('muted'),bool),'mute reports actual audio state')
check(restored.get('muted') is not muted.get('muted'),'second mute restores sound state')
check(call('/status',{},token+'x')[0]==401,'wrong token denied')
check(call('/shell',{'command':'id'},token)[0]==404,'unknown route denied')
check(call('/key',{'key':'factory_reset'},token)[0]==400,'unknown command denied')
check(call('/power/control',{'action':'factory_reset'},token)[0]==400,'unknown power action denied')
check(call('/key',{'key':'home'},token)[1].get('ok') is True,'home accepted')
time.sleep(.5)
check('launcher' in adb('shell','dumpsys','activity','activities').lower(),'launcher opened')
check(call('/key',{'key':'settings'},token)[1].get('ok') is True,'settings accepted')
time.sleep(1)
activity = adb('shell','dumpsys','activity','activities')
check(any('com.android.settings' in line for line in activity.splitlines() if 'topResumedActivity' in line),'settings actually foreground')
status, result = call('/ui',{},token)
check(status==200 and len(result.get('items',[]))>0,'screen labels read')
check(all(len(n['label'])<=160 for n in result['items']),'label size bound')
node = result['items'][0]
check(call('/tap',dict(node,label='stale label that does not exist'),token)[1].get('ok') is False,'stale target rejected')
check(call('/tap',{'x':-1,'y':0,'label':'invalid'},token)[0]==400,'invalid coordinate rejected')
network = next(n for n in result['items'] if n['label']=='Network & internet')
status, click = call('/tap',network,token)
check(status==200 and click.get('ok') is True,'settings category accepts activation: '+str(click))
time.sleep(.7)
screen = call('/ui',{},token)[1]
check(any(n['label'] in ('Internet','Airplane mode') for n in screen.get('items',[])),'clicked category actually opens')
adb('shell','am','start','-W','-n','local.akenori.testdriver/local.akenori.receiver.NavigationFixtureActivity')
check(call('/key',{'key':'down'},token)[1].get('ok') is True,'direction selects next item')
check(call('/key',{'key':'ok'},token)[1].get('ok') is True,'OK activates selection')
second_screen=wait_screen(token,lambda s:any(n['label']=='Clicked Second' for n in s.get('items',[])))
check(any(n['label']=='Clicked Second' for n in second_screen.get('items',[])),'down then OK actually clicked second button: '+str(second_screen))
third_move=call('/key',{'key':'down'},token)[1];third_click=call('/key',{'key':'ok'},token)[1]
third_screen=wait_screen(token,lambda s:any(n['label']=='Clicked Third' for n in s.get('items',[])))
check(any(n['label']=='Clicked Third' for n in third_screen.get('items',[])),'consecutive commands retain order: '+str((third_move,third_click,third_screen)))
slider=next(n for n in call('/ui',{},token)[1]['items'] if n['label']=='Test level')
check(slider.get('value')==50,'slider current value read')
check(call('/range',dict(slider,value=70),token)[1].get('ok') is True,'slider update accepted')
slider_screen=wait_screen(token,lambda s:any(n['label']=='Test level' and n.get('value')==70 for n in s.get('items',[])))
check(next(n for n in slider_screen['items'] if n['label']=='Test level')['value']==70,'actual slider value changed')
check(call('/range',dict(slider,value=101),token)[0]==400,'out of range value denied')
child=next(n for n in call('/ui',{},token)[1]['items'] if n['label']=='Parent action')
check(call('/tap',child,token)[1].get('ok') is True,'child label activation accepted')
check(any(n['label']=='Clicked parent' for n in call('/ui',{},token)[1]['items']),'click works when fixture ignores touch gestures')
adb('shell','am','start','-W','-n','local.akenori.testdriver/local.akenori.receiver.HardwareKeyFixtureActivity')
time.sleep(.6)
check(call('/status',{},token)[1].get('nativeKeys') is True,'native input channel attached without a text field')
check(call('/key',{'key':'left'},token)[1].get('method') in ('system_key','native_key'),'real DPAD key submitted')
native_screen=wait_screen(token,lambda s:any(n['label']=='Scale 99' for n in s.get('items',[])))
check(any(n['label']=='Scale 99' for n in native_screen.get('items',[])),'hardware-key-only scale actually changed')
call('/key',{'key':'right'},token)
native_screen=wait_screen(token,lambda s:any(n['label']=='Scale 100' for n in s.get('items',[])))
check(any(n['label']=='Scale 100' for n in native_screen.get('items',[])),'reverse key restores scale')
call('/key',{'key':'ok'},token)
native_screen=wait_screen(token,lambda s:any(n['label']=='OK received' for n in s.get('items',[])))
check(any(n['label']=='OK received' for n in native_screen.get('items',[])),'hardware-key-only OK received')
call('/key',{'key':'menu'},token)
native_screen=wait_screen(token,lambda s:any(n['label']=='Menu received' for n in s.get('items',[])))
check(any(n['label']=='Menu received' for n in native_screen.get('items',[])),'hardware menu key received')
newpin = renew()
check(call('/status',{},token)[0]==401,'old token revoked by new PIN')
newtoken = call('/pair',{'pin':newpin})[1]['token']
check(newtoken!=token and call('/status',{},newtoken)[0]==200,'new controller paired')
# Leave a fresh PIN for the independent phone-client UI test.
renew()
print(f'PASS: {count} receiver authentication, revocation and real Android control checks')
