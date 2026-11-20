"""Phone UI against a focus-only projector fixture, dedicated emulator only."""
import http.server, json, os, re, shutil, subprocess, tempfile, threading, time
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
SERIAL=os.environ.get('HOME_REMOTE_TEST_SERIAL','emulator-5580')
assert SERIAL.startswith('emulator-')
ADB=shutil.which('adb') or 'adb'
actions=[]
class Projector(http.server.BaseHTTPRequestHandler):
    protocol_version='HTTP/1.1'
    def log_message(self,*args):pass
    def do_POST(self):
        value=json.loads(self.rfile.read(int(self.headers['Content-Length'])))
        if self.path=='/status':body={'ok':True,'accessibility':True,'directFocus':True,'deviceKind':'projector','model':'Focus fixture'}
        elif self.path=='/projector/state':body={'ok':True,'directFocus':True} # Deliberately no scale / geometry.
        elif self.path=='/projector/control':actions.append(value['action']);body={'ok':True,'message':'Focus step'}
        else:body={'ok':True}
        payload=json.dumps(body).encode();self.send_response(200);self.send_header('Content-Length',str(len(payload)));self.end_headers();self.wfile.write(payload)

def adb(*args):return subprocess.check_output([ADB,'-s',SERIAL,*args],encoding='utf-8',errors='replace',timeout=30)
def ui():
    raw=adb('shell','am','instrument','-w','local.akenori.testdriver/local.akenori.receiver.DumpInstrumentation')
    return list(ET.fromstring(re.search(r'hierarchy=(<hierarchy>.*</hierarchy>)',raw,re.S).group(1)).iter('node'))
def tap(node):
    x1,y1,x2,y2=map(int,re.findall(r'\d+',node.get('bounds')))
    adb('shell','input','tap',str((x1+x2)//2),str((y1+y2)//2))
def tap_text(label):
    for _ in range(6):
        for node in ui():
            if node.get('text','')==label or node.get('text','').startswith(label+'\n'):
                bounds=list(map(int,re.findall(r'\d+',node.get('bounds'))))
                if bounds[3]>bounds[1] and 48<=bounds[1] and bounds[3]<=1552:tap(node);return
        adb('shell','input','swipe','700','1400','700','650','200')
    raise AssertionError('Missing visible control '+label)
def write(name,data):
    with tempfile.TemporaryDirectory() as directory:
        path=Path(directory)/name;path.write_text(data,encoding='utf-8')
        adb('push',str(path),'/data/local/tmp/focus-fixture.xml')
        adb('shell',f'cat /data/local/tmp/focus-fixture.xml | run-as local.akenori.remote tee shared_prefs/{name} >/dev/null')
        adb('shell','rm','-f','/data/local/tmp/focus-fixture.xml')

saved={name:adb('exec-out',f'run-as local.akenori.remote cat shared_prefs/{name}') for name in ('ReceiverActivity.xml','remote_ui.xml')}
server=http.server.ThreadingHTTPServer(('127.0.0.1',8787),Projector)
threading.Thread(target=server.serve_forever,daemon=True).start()
try:
    adb('shell','am','force-stop','local.akenori.remote')
    write('ReceiverActivity.xml','<map><string name="ip">10.0.2.2</string><string name="token">'+'a'*64+'</string></map>')
    write('remote_ui.xml','<map><string name="device_kind">tv</string><boolean name="onboarding_done" value="true" /></map>')
    adb('shell','am','start','-W','-n','local.akenori.remote/.MainActivity')
    end=time.monotonic()+25
    while time.monotonic()<end:
        nodes=ui()
        if any(n.get('text')=='В сети' for n in nodes):break
        time.sleep(.3)
    else:raise AssertionError('Fixture connection failed')
    for label,action in [('F−','focus_minus'),('F+','focus_plus')]:
        node=next(n for n in nodes if n.get('text')==label)
        assert node.get('enabled')=='true';tap(node);time.sleep(.3)
        assert action in actions
    print('PASS: known projector overrides mistaken TV profile and main F-/F+ send both commands',flush=True)
    tap_text('Настройки изображения');tap_text('Фокус')
    end=time.monotonic()+8
    while time.monotonic()<end:
        nodes=ui()
        if any(n.get('text')=='Фокусировка линзы' for n in nodes) and any(n.get('text')=='F−' and n.get('enabled')=='true' for n in nodes):break
        time.sleep(.2)
    (ROOT/'verification/focus-fixture.xml').write_text('\n'.join(str({k:n.get(k) for k in ('text','enabled','bounds')}) for n in nodes),encoding='utf-8')
    (ROOT/'verification/focus-fixture.png').write_bytes(subprocess.check_output([ADB,'-s',SERIAL,'exec-out','screencap','-p']))
    assert any(n.get('text')=='F−' and n.get('enabled')=='true' for n in nodes)
    assert any(n.get('text')=='F+' and n.get('enabled')=='true' for n in nodes)
    assert any(n.get('text')=='Фокусировка линзы' for n in nodes)
    print('PASS: focus panel works when geometry/scale is absent',flush=True)
    adb('shell','am','force-stop','local.akenori.remote')
    write('remote_ui.xml','<map><string name="device_kind">tv</string><boolean name="onboarding_done" value="true" /></map>')
    server.shutdown();server.server_close()
    adb('shell','am','start','-W','-n','local.akenori.remote/.MainActivity');time.sleep(1)
    nodes=ui()
    assert all(any(n.get('text')==label for n in nodes) for label in ('F−','F+'))
    assert not any(n.get('text')=='Код из 6 цифр' for n in nodes)
    print('PASS: remembered projector keeps focus keys visible offline despite TV profile; no PIN dialog',flush=True)
finally:
    adb('shell','am','force-stop','local.akenori.remote')
    for name,data in saved.items():write(name,data)
    server.shutdown();server.server_close()
    adb('shell','am','start','-W','-n','local.akenori.remote/.MainActivity')
