import os, shutil
"""Client UI and APK delivery against the dedicated emulator, no physical-device writes."""
import re
import subprocess
import time
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
ADB = shutil.which('adb') or 'adb'
SERIAL = os.environ.get('HOME_REMOTE_TEST_SERIAL','emulator-5580')
assert SERIAL.startswith('emulator-'), 'Integration tests require a dedicated emulator'

(ROOT/'verification').mkdir(exist_ok=True)

def adb(*args):
    return subprocess.check_output([ADB,'-s',SERIAL,*args],cwd=ROOT,encoding='utf-8',errors='replace',timeout=30)

def ui():
    result=adb('shell','am','instrument','-w','local.akenori.testdriver/local.akenori.receiver.DumpInstrumentation')
    return ET.fromstring(re.search(r'hierarchy=(<hierarchy>.*</hierarchy>)',result,re.S).group(1))

def tap_node(node):
    assert node is not None and node.get('enabled')=='true'
    x1,y1,x2,y2=map(int,re.findall(r'-?\d+',node.get('bounds')))
    assert x2>x1 and y2>y1 and y1>=0
    adb('shell','input','tap',str((x1+x2)//2),str((y1+y2)//2))

def tap(text):
    for _ in range(5):
        for n in ui().iter('node'):
            if n.get('text','').casefold()==text.casefold():
                b=list(map(int,re.findall(r'-?\d+',n.get('bounds'))))
                if b[3]>b[1] and b[1]>=0 and b[3]<=1552:
                    tap_node(n);return
        adb('shell','input','swipe','705','1400','705','650','250')
    raise AssertionError('Missing '+text)

adb('install','-r',str(ROOT/'app/build/outputs/apk/debug/app-debug.apk'))
adb('shell','pm','clear','local.akenori.remote')
adb('shell','am','start','-W','-n','local.akenori.receiver/.MainActivity')
tap('Новый код подключения')
if any(n.get('text','').casefold()=='Заменить телефон'.casefold() for n in ui().iter('node')):tap('Заменить телефон')
time.sleep(1)
pin=next(n.get('text') for n in ui().iter('node') if re.fullmatch(r'\d{6}',n.get('text','')))
ip=re.search(r'inet (\d+\.\d+\.\d+\.\d+)',adb('shell','ip','-f','inet','addr','show','wlan0')).group(1)
adb('shell','am','start','-W','-n','local.akenori.remote/.MainActivity')
tree=ui()
assert any(n.get('text')=='Что подключаем?' for n in tree.iter('node')),'Fresh install must show guide'
tap('Телевизор');tap('Открыть пульт')
tree=ui()
assert any(n.get('text')=='Настройки телевизора' for n in tree.iter('node')),'TV profile missing'
assert not any(n.get('text','').startswith('Фокус\n') for n in tree.iter('node')),'Lens controls shown on TV'
tap('Подключить')
fields=[n for n in ui().iter('node') if n.get('class')=='android.widget.EditText']
assert len(fields)==2
assert not re.search(r'\d+\.\d+\.\d+\.\d+',fields[0].get('text','')),'Private user IP in fresh install'
tap_node(fields[0]);adb('shell','input','text',ip)
fields=[n for n in ui().iter('node') if n.get('class')=='android.widget.EditText']
tap_node(fields[1]);adb('shell','input','text',pin)
adb('shell','input','keyevent','4')
tap('Подключить');time.sleep(1)
tree=ui()
assert any(n.get('text')=='В сети' for n in tree.iter('node')),'Native Wi-Fi client did not connect'
assert any(n.get('text')=='OK' and n.get('enabled')=='true' for n in tree.iter('node')),'Controls remain disabled'
tap('Как подключить · Инструкция');tap('Проектор');tap('Открыть пульт');time.sleep(.5)
tree=ui()
for label in ('F−','F+'):
    assert any(n.get('text')==label and n.get('enabled')=='true' for n in tree.iter('node')),'Missing main-screen focus key '+label
png=subprocess.check_output([ADB,'-s',SERIAL,'exec-out','screencap','-p'],timeout=12)
(ROOT/'verification/remote-current-emulator.png').write_bytes(png)
tap('Настройки изображения')
visible_titles=set()
for _ in range(3):
    visible_titles.update(n.get('text','').split('\n')[0] for n in ui().iter('node'))
    if all(title in visible_titles for title in ('Фокус','Масштаб','Трапеция','Углы')):break
    adb('shell','input','swipe','705','1400','705','650','250')
assert all(title in visible_titles for title in ('Фокус','Масштаб','Трапеция','Углы')),'Image setting missing from expanded section'
tap('Экран устройства');time.sleep(.5)
tree=ui()
assert any('Нажимай пункты меню' in n.get('text','') for n in tree.iter('node')),'No UI returned to native client'
tap('×')
preferences=adb('exec-out','run-as local.akenori.remote cat shared_prefs/remote_ui.xml')
initial='name="haptics" value="false"' not in preferences
tap('Виброотклик')
for _ in range(10):
    preferences=adb('exec-out','run-as local.akenori.remote cat shared_prefs/remote_ui.xml')
    current='name="haptics" value="false"' not in preferences
    if current!=initial:break
    time.sleep(.1)
assert current!=initial,'Haptic preference not saved'
if not current:tap('Виброотклик')
tap('Ещё')
tap('Установить приёмник на ТВ / проектор');time.sleep(.5)
assert any('http://'+ip+':8085/' in n.get('text','') for n in ui().iter('node')),'Download listener not ready'
request=ROOT/'verification/emulator-download-request.txt'
request.write_bytes(('GET /projector-receiver.apk HTTP/1.1\r\nHost: '+ip+':8085\r\nConnection: close\r\n\r\n').encode())
adb('push',str(request),'/data/local/tmp/receiver-download-request.txt')
raw=subprocess.check_output([ADB,'-s',SERIAL,'exec-out','toybox nc -w 5 -q 3 '+ip+' 8085 < /data/local/tmp/receiver-download-request.txt'],timeout=12)
head,body=raw.split(b'\r\n\r\n',1)
assert head.startswith(b'HTTP/1.1 200 ')
assert body==(ROOT/'receiver/build/outputs/apk/debug/receiver-debug.apk').read_bytes(),'Downloaded APK differs'
adb('shell','input','keyevent','4')
print('PASS: onboarding, TV/projector profiles, no private IP, pairing, image settings, screen panel, haptics and exact APK download')
