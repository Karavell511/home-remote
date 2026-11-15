package local.akenori.remote;

import android.annotation.*;
import android.bluetooth.*;
import android.content.*;
import android.os.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.Executor;

@TargetApi(28)
@SuppressLint("MissingPermission") // Activity requests CONNECT/ADVERTISE before constructing this controller.
public final class BluetoothRemote implements Closeable {
    public interface Listener {
        void state(boolean ready,boolean connected,String title,String message);
    }
    private final Context context;
    private final BluetoothAdapter adapter;
    private final Listener listener;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final Executor callbackExecutor=command -> main.post(command);
    private volatile BluetoothHidDevice hid;
    private volatile BluetoothDevice host;
    private boolean registered, closed;
    private volatile boolean bootProtocol;
    private byte keyboardLeds;
    private final Runnable serviceTimeout=() -> {
        if(!closed && !registered) report(false,false,"Bluetooth-пульт недоступен","Телефон не предоставил профиль Bluetooth HID. Это ограничение его прошивки.");
    };
    private final Runnable connectTimeout=() -> {
        if(!closed && host==null) report(registered,false,"Телевизор не подключился","Включи на телевизоре режим подключения Bluetooth-клавиатуры и повтори. Поддержка только наушников недостаточна.");
    };
    public BluetoothRemote(Context context,Listener listener) {
        this.context=context; this.listener=listener;
        BluetoothManager manager=(BluetoothManager)context.getSystemService(Context.BLUETOOTH_SERVICE);
        adapter=manager==null?null:manager.getAdapter();
    }
    public boolean enabled() { return adapter!=null && adapter.isEnabled(); }
    public void start() {
        if(adapter==null) { report(false,false,"Bluetooth отсутствует","Телефон не предоставил Bluetooth-адаптер."); return; }
        if(!adapter.isEnabled()) { report(false,false,"Включи Bluetooth","Включи Bluetooth на телефоне и нажми «Включить пульт»."); return; }
        main.postDelayed(serviceTimeout,8000);
        if(!adapter.getProfileProxy(context,service,BluetoothProfile.HID_DEVICE)) {
            main.removeCallbacks(serviceTimeout); report(false,false,"Профиль HID недоступен","Эта прошивка телефона не разрешает работать Bluetooth-клавиатурой.");
        }
    }
    private final BluetoothProfile.ServiceListener service=new BluetoothProfile.ServiceListener() {
        @Override public void onServiceConnected(int profile,BluetoothProfile proxy) {
            if(closed) { adapter.closeProfileProxy(profile,proxy); return; }
            hid=(BluetoothHidDevice)proxy;
            BluetoothHidDeviceAppSdpSettings sdp=new BluetoothHidDeviceAppSdpSettings(
                "Home Remote","Keyboard and media remote","Local Remote",BluetoothHidDevice.SUBCLASS1_KEYBOARD,HidReports.DESCRIPTOR);
            try { if(!hid.registerApp(sdp,null,null,callbackExecutor,callback)) {
                main.removeCallbacks(serviceTimeout); report(false,false,"Не удалось включить пульт","Bluetooth HID занят другим приложением или не поддерживается прошивкой.");
            }} catch(SecurityException e) { report(false,false,"Нет разрешения Bluetooth","Разреши доступ к устройствам поблизости в настройках приложения."); }
        }
        @Override public void onServiceDisconnected(int profile) {
            hid=null; host=null; registered=false;
            report(false,false,"Bluetooth отключён","Включи пульт заново.");
        }
    };
    private final BluetoothHidDevice.Callback callback=new BluetoothHidDevice.Callback() {
        @Override public void onAppStatusChanged(BluetoothDevice device,boolean value) {
            if(closed) return; registered=value; main.removeCallbacks(serviceTimeout);
            if(!value) { host=null; report(false,false,"Bluetooth-пульт остановлен","Держи приложение открытым. Нажми «Включить пульт», чтобы подключиться снова."); }
            else if(host!=null) report(true,true,"Bluetooth подключён",name(host));
            else report(true,false,"Телефон готов быть пультом","Нажми «Сделать телефон видимым», затем найди телефон в меню Bluetooth-клавиатур на телевизоре. Для уже сопряжённого ТВ используй список ниже.");
        }
        @Override public void onConnectionStateChanged(BluetoothDevice device,int state) {
            if(closed) return;
            if(state==BluetoothProfile.STATE_CONNECTED) {
                main.removeCallbacks(connectTimeout); host=device; bootProtocol=false;
                report(true,true,"Bluetooth подключён",name(device)+"\nПроверь стрелки и OK на телевизоре.");
            } else if(state==BluetoothProfile.STATE_DISCONNECTED) {
                if(host==null || host.equals(device)) { host=null; main.removeCallbacks(connectTimeout);
                    report(registered,false,"Нет Bluetooth-соединения","Сопряжение и подключение пульта — разные шаги. Выбери телевизор снова; он должен принимать Bluetooth-клавиатуры."); }
            } else if(state==BluetoothProfile.STATE_CONNECTING) report(registered,false,"Подключаемся к телевизору…",name(device));
        }
        @Override public void onSetProtocol(BluetoothDevice device,byte protocol) { bootProtocol=protocol==BluetoothHidDevice.PROTOCOL_BOOT_MODE; }
        @Override public void onGetReport(BluetoothDevice device,byte type,byte id,int bufferSize) {
            BluetoothHidDevice current=hid; if(closed || current==null) return;
            if(type==BluetoothHidDevice.REPORT_TYPE_INPUT && (id==1 || id==2 || (bootProtocol && id==0))) {
                byte[] data=new byte[id==2?2:8];
                if(bufferSize>0 && bufferSize<data.length) data=Arrays.copyOf(data,bufferSize);
                current.replyReport(device,type,id,data);
            } else if(type==BluetoothHidDevice.REPORT_TYPE_OUTPUT && (id==1 || id==0)) current.replyReport(device,type,id,new byte[]{keyboardLeds});
            else current.reportError(device,BluetoothHidDevice.ERROR_RSP_INVALID_RPT_ID);
        }
        @Override public void onSetReport(BluetoothDevice device,byte type,byte id,byte[] data) {
            if(hid==null || closed) return;
            if(type==BluetoothHidDevice.REPORT_TYPE_OUTPUT && (id==1 || id==0) && data.length>0) { keyboardLeds=data[0]; hid.reportError(device,BluetoothHidDevice.ERROR_RSP_SUCCESS); }
            else hid.reportError(device,BluetoothHidDevice.ERROR_RSP_UNSUPPORTED_REQ);
        }
        @Override public void onInterruptData(BluetoothDevice device,byte id,byte[] data) { if((id==1 || id==0) && data.length>0) keyboardLeds=data[0]; }
        @Override public void onVirtualCableUnplug(BluetoothDevice device) { host=null; report(registered,false,"Телевизор отключил пульт","Выбери телевизор снова, чтобы подключиться."); }
    };
    public List<BluetoothDevice> paired() {
        if(adapter==null) return Collections.emptyList();
        List<BluetoothDevice> result=new ArrayList<>(adapter.getBondedDevices());
        result.sort(Comparator.comparing(BluetoothRemote::name)); return result;
    }
    public static String name(BluetoothDevice device) {
        String name=device.getName(); return name==null || name.trim().isEmpty()?"Bluetooth-устройство "+device.getAddress().substring(12):name;
    }
    public void connect(BluetoothDevice device) {
        if(!registered || hid==null) { report(false,false,"Сначала включи пульт","Телефон ещё не зарегистрирован как Bluetooth-клавиатура."); return; }
        if(host!=null && !host.equals(device)) hid.disconnect(host);
        main.removeCallbacks(connectTimeout);
        if(hid.connect(device)) { report(true,false,"Подключаемся…",name(device)); main.postDelayed(connectTimeout,12000); }
        else report(true,false,"Подключение не началось","Телевизор должен поддерживать Bluetooth-клавиатуру и разрешить подключение телефона.");
    }
    public void key(int code) throws IOException {
        BluetoothHidDevice device=hid; BluetoothDevice target=host;
        if(device==null || target==null || closed) throw new IOException("Нет Bluetooth-соединения");
        HidReports.Report report=HidReports.forKey(code);
        if(bootProtocol && report.id!=1) throw new IOException("Телевизор принимает только обычные клавиши. Громкость и медиакнопки в этом режиме недоступны.");
        int id=bootProtocol?0:report.id;
        boolean sent=false;
        try {
            sent=device.sendReport(target,id,report.data);
            if(!sent) throw new IOException("Телефон не смог отправить нажатие");
            try { Thread.sleep(45); } catch(InterruptedException e) { Thread.currentThread().interrupt(); }
        } finally {
            boolean released=device.sendReport(target,id,report.release());
            if(sent && !released) { device.disconnect(target); throw new IOException("Не удалось отпустить кнопку. Соединение разорвано — подключись заново."); }
        }
    }
    private void report(boolean ready,boolean connected,String title,String message) {
        if(!closed) listener.state(ready,connected,title,message);
    }
    @Override public void close() {
        closed=true; main.removeCallbacksAndMessages(null);
        BluetoothHidDevice current=hid; BluetoothDevice target=host; host=null;
        if(current!=null) {
            try {
                if(target!=null) { current.sendReport(target,bootProtocol?0:1,new byte[8]); if(!bootProtocol) current.sendReport(target,2,new byte[2]); current.disconnect(target); }
                current.unregisterApp(); adapter.closeProfileProxy(BluetoothProfile.HID_DEVICE,current);
            } catch(SecurityException ignored) {}
        }
        hid=null; registered=false;
    }
}
