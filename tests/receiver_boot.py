"""Real Android boot, process death and update recovery; disposable emulator only."""
import http.client, json, os, re, shutil, subprocess, time
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
SERIAL=os.environ.get('HOME_REMOTE_TEST_SERIAL','emulator-5580')
assert SERIAL.startswith('emulator-'), 'Never run lifecycle tests on a physical device'
ADB=shutil.which('adb') or 'adb'

def adb(*args):
    return subprocess.check_output([ADB,'-s',SERIAL,*args],timeout=45,encoding='utf-8',errors='replace')

def prefs():
    return {n.get('name'):n.text for n in ET.fromstring(adb('exec-out','run-as local.akenori.receiver cat shared_prefs/pairing.xml'))}

def status(token):
    conn=http.client.HTTPConnection('127.0.0.1',8788,timeout=3)
    try:
        conn.request('POST','/status','{}',{'Authorization':'Bearer '+token,'Content-Type':'application/json'})
        response=conn.getresponse(); value=json.loads(response.read())
        return value if response.status==200 else {}
    finally:conn.close()

def online(token):
    end=time.monotonic()+60
    while time.monotonic()<end:
        try:
            value=status(token)
            if value.get('accessibility') and value.get('autoStart'):return
        except (OSError,ValueError,http.client.HTTPException):pass
        time.sleep(1)
    raise AssertionError('Receiver did not recover automatically')

token=prefs()['token']
adb('forward','tcp:8788','tcp:8787');online(token)
adb('shell','input','keyevent','3')
adb('shell','am','force-stop','local.akenori.remote')
pid=adb('shell','pidof','local.akenori.receiver').strip()
assert re.fullmatch(r'\d+',pid)
adb('shell','run-as','local.akenori.receiver','kill','-9',pid)
online(token)
assert prefs()['token']==token
print('PASS: receiver process death recovers with original pairing',flush=True)

adb('install','-r',str(ROOT/'receiver/build/outputs/apk/debug/receiver-debug.apk'))
online(token)
assert prefs()['token']==token
print('PASS: APK replacement recovers with original pairing',flush=True)

adb('reboot')
end=time.monotonic()+100
while time.monotonic()<end:
    try:
        if adb('shell','getprop','sys.boot_completed').strip()=='1':break
    except subprocess.CalledProcessError:pass
    time.sleep(2)
else:raise AssertionError('Emulator did not boot')
adb('forward','tcp:8788','tcp:8787');online(token)
assert prefs()['token']==token
activities=adb('shell','dumpsys','activity','activities')
assert not any('local.akenori.receiver/.MainActivity' in line for line in activities.splitlines() if 'topResumedActivity' in line)
print('PASS: cold Android boot restores receiver without opening its app or entering PIN',flush=True)

adb('shell','settings','put','secure','enabled_accessibility_services','null')
time.sleep(7)
assert not prefs().get('token') is None,'Disabling permission must preserve pairing for later re-enable'
try:
    value=status(token)
    assert not value.get('accessibility'),'Disabled control is still active'
except (OSError,ValueError,http.client.HTTPException):pass
services=adb('shell','dumpsys','activity','services','local.akenori.receiver')
assert 'isForeground=true' not in services,'Network service must stop when control permission is revoked'
print('PASS: revoking accessibility stops the persistent network service',flush=True)
adb('shell','settings','put','secure','enabled_accessibility_services','local.akenori.receiver/.ControlService')
adb('shell','settings','put','secure','accessibility_enabled','1');online(token)
