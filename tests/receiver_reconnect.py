"""Pairing persistence, IP discovery and reconnection on the dedicated emulator only.
Run after receiver_client_ui.py. Never uses a physical device or prints pairing keys.
"""
import os, re, shutil, subprocess, tempfile, time
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
ADB=shutil.which('adb') or 'adb'
SERIAL=os.environ.get('HOME_REMOTE_TEST_SERIAL','emulator-5580')
assert SERIAL.startswith('emulator-'), 'Use a dedicated emulator'
count=0

def adb(*args):
    return subprocess.check_output([ADB,'-s',SERIAL,*args],cwd=ROOT,encoding='utf-8',errors='replace',timeout=30)

def prefs():
    return ET.fromstring(adb('exec-out','run-as local.akenori.remote cat shared_prefs/ReceiverActivity.xml'))

def values():
    return {n.get('name'):n.text for n in prefs()}

def write_prefs(root):
    adb('shell','am','force-stop','local.akenori.remote')
    with tempfile.TemporaryDirectory() as directory:
        path=Path(directory)/'prefs.xml'; path.write_bytes(ET.tostring(root,encoding='utf-8',xml_declaration=True))
        adb('push',str(path),'/data/local/tmp/remote-test-prefs.xml')
        adb('shell','cat /data/local/tmp/remote-test-prefs.xml | run-as local.akenori.remote tee shared_prefs/ReceiverActivity.xml >/dev/null')
        adb('shell','rm -f /data/local/tmp/remote-test-prefs.xml')

def start():
    adb('shell','am','start','-W','-n','local.akenori.remote/.MainActivity')

def ui():
    raw=adb('shell','am','instrument','-w','local.akenori.testdriver/local.akenori.receiver.DumpInstrumentation')
    return ET.fromstring(re.search(r'hierarchy=(<hierarchy>.*</hierarchy>)',raw,re.S).group(1))

def online(timeout=25):
    deadline=time.monotonic()+timeout
    while time.monotonic()<deadline:
        nodes=list(ui().iter('node'))
        if any(n.get('text')=='В сети' for n in nodes) and any(n.get('text')=='OK' and n.get('enabled')=='true' for n in nodes):return
        time.sleep(.3)
    raise AssertionError('Automatic connection did not recover')

def check(ok,label):
    global count
    assert ok,label
    count+=1
    print('PASS:',label,flush=True)

baseline=values(); assert baseline.get('token')
adb('shell','am','force-stop','local.akenori.remote'); start(); online()
check(values()['token']==baseline['token'],'Process restart retains pairing and connects without PIN')

adb('shell','input','keyevent','3'); start(); online()
check(values()['token']==baseline['token'],'Return from Home reconnects without PIN')

root=prefs()
for item in root:
    if item.get('name')=='ip':item.text='10.0.2.200'
write_prefs(root); start(); online()
check(values()['ip']==baseline['ip'] and values()['token']==baseline['token'],'Authenticated discovery replaces stale IP without new pairing')

adb('shell','svc','wifi','disable')
try:
    adb('shell','input','keyevent','3'); start(); time.sleep(2)
    check(values()['token']==baseline['token'],'Offline resume keeps pairing')
finally:
    adb('shell','svc','wifi','enable')
online(40)
check(values()['token']==baseline['token'],'WiFi restoration reconnects while remote stays open')

adb('shell','settings','put','secure','enabled_accessibility_services','null')
time.sleep(1)
adb('shell','am','force-stop','local.akenori.remote'); start(); time.sleep(3)
check(values()['token']==baseline['token'],'Unavailable receiver keeps pairing')
adb('shell','settings','put','secure','enabled_accessibility_services','local.akenori.receiver/.ControlService')
adb('shell','settings','put','secure','accessibility_enabled','1')
online(35)
check(values()['token']==baseline['token'],'Receiver service restart reconnects without PIN')
print('PASS:',count,'Android reconnection regressions')
