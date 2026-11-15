package local.akenori.remote;

import android.annotation.SuppressLint;
import android.bluetooth.*;
import android.content.*;
import android.os.*;
import java.io.Closeable;

/** Discovery and system-confirmed bonding; never treats a bond as a HID connection. */
@SuppressLint("MissingPermission")
final class BluetoothDiscovery implements Closeable {
    interface Listener {
        void found(BluetoothDevice device);
        void status(String message);
        void paired(BluetoothDevice device);
    }
    private final Context context;
    private final BluetoothAdapter adapter;
    private final Listener listener;
    private final Handler timer = new Handler(Looper.getMainLooper());
    private BluetoothDevice pending;
    private boolean closed, scanning;
    BluetoothDiscovery(Context context, Listener listener) {
        this.context=context; this.listener=listener;
        BluetoothManager manager=(BluetoothManager)context.getSystemService(Context.BLUETOOTH_SERVICE);
        adapter=manager.getAdapter();
        IntentFilter filter=new IntentFilter(BluetoothDevice.ACTION_FOUND);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        filter.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
        if(Build.VERSION.SDK_INT>=33) context.registerReceiver(receiver,filter,Context.RECEIVER_EXPORTED);
        else context.registerReceiver(receiver,filter);
    }
    private final BroadcastReceiver receiver=new BroadcastReceiver() {
        @Override public void onReceive(Context ignored,Intent intent) {
            if(closed) return;
            String action=intent.getAction();
            BluetoothDevice device=intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
            if(BluetoothDevice.ACTION_FOUND.equals(action) && scanning && device!=null) listener.found(device);
            else if(BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action) && scanning) {
                scanning=false; listener.status("Поиск завершён. Выбери проектор из списка или повтори поиск.");
            } else if(BluetoothDevice.ACTION_BOND_STATE_CHANGED.equals(action) && pending!=null && pending.equals(device)) {
                int state=intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE,BluetoothDevice.ERROR);
                if(state==BluetoothDevice.BOND_BONDED) {
                    pending=null; timer.removeCallbacksAndMessages(null); listener.paired(device);
                } else if(state==BluetoothDevice.BOND_NONE) {
                    pending=null; timer.removeCallbacksAndMessages(null);
                    listener.status("Сопряжение не завершилось. Проверь запрос на проекторе и попробуй снова.");
                }
            }
        }
    };
    void start() {
        if(closed) return;
        if(!adapter.isEnabled()) { listener.status("Включи Bluetooth на телефоне."); return; }
        if(pending!=null) { listener.status("Сначала заверши запрос сопряжения."); return; }
        if(adapter.isDiscovering()) {
            scanning=true; listener.status("Идёт поиск устройств. Проектор должен быть доступен для обнаружения."); return;
        }
        scanning=adapter.startDiscovery();
        listener.status(scanning?"Ищем устройства поблизости… На проекторе оставь открытыми настройки Bluetooth.":"Android не запустил поиск. Проверь разрешение «Устройства поблизости»; на Android 9–11 также нужна включённая геолокация.");
    }
    void stopScan() {
        boolean owned=scanning; scanning=false;
        if(owned && adapter.isDiscovering()) adapter.cancelDiscovery();
    }
    void pair(BluetoothDevice device) {
        if(closed || pending!=null) return;
        stopScan();
        if(device.getBondState()==BluetoothDevice.BOND_BONDED) { listener.paired(device); return; }
        pending=device;
        listener.status("Подтверди сопряжение с «"+BluetoothRemote.name(device)+"» на телефоне и проекторе.");
        if(device.getBondState()!=BluetoothDevice.BOND_BONDING && !device.createBond()) {
            pending=null; listener.status("Android не смог начать сопряжение. Повтори поиск и выбери проектор."); return;
        }
        timer.postDelayed(() -> {
            if(!closed && pending!=null) { pending=null; listener.status("Нет подтверждения сопряжения. Проверь экран проектора и повтори."); }
        },60000);
    }
    @Override public void close() {
        if(closed) return; closed=true; pending=null; timer.removeCallbacksAndMessages(null);
        try { stopScan(); } catch(SecurityException ignored) {}
        context.unregisterReceiver(receiver);
    }
}
