package local.akenori.remote;

import android.app.*;
import android.Manifest;
import android.bluetooth.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.*;
import android.os.*;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import java.net.*;
import java.security.*;
import java.security.spec.*;
import java.util.*;
import java.util.concurrent.*;
import javax.net.SocketFactory;

public class MainActivity extends Activity {
    private final int BG=0xfff3f1e9, INK=0xff192c25, MUTED=0xff59645e, PANEL=0xffe4e8df, ACCENT=0xffc7e6a0, ERROR=0xffa33f2d;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final Handler timers=new Handler(Looper.getMainLooper());
    private final ArrayList<Button> controls=new ArrayList<>();
    private EditText address, port;
    private TextView status, detail;
    private Button connect, diagnose, btVisible, btPaired, btFind, wifiTab, bluetoothTab, focusHelp;
    private LinearLayout wifiFields, bluetoothFields;
    private BluetoothRemote bluetooth;
    private BluetoothDiscovery discovery;
    private BluetoothDevice pendingBluetooth;
    private boolean bluetoothWanted;
    private boolean bluetoothMode, btReady;
    private volatile AdbClient client;
    private volatile int generation;
    private boolean connected, busy;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        if(!getIntent().getBooleanExtra("legacy",false)) {
            boolean ready=getSharedPreferences("ReceiverActivity",0).contains("token") || getSharedPreferences("remote_ui",0).getBoolean("onboarding_done",false);
            startActivity(new Intent(this,ready?ReceiverActivity.class:GuideActivity.class)); finish(); return;
        }
        getWindow().setStatusBarColor(BG); getWindow().setNavigationBarColor(BG);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        ScrollView scroll=new ScrollView(this); scroll.setFillViewport(true); scroll.setBackgroundColor(BG);
        LinearLayout page=column(); page.setPadding(dp(24),dp(20),dp(24),dp(28));
        scroll.addView(page); setContentView(scroll);
        TextView eyebrow=label("ПРОЕКТОРЫ И ТЕЛЕВИЗОРЫ",12,MUTED); eyebrow.setLetterSpacing(.12f); page.addView(eyebrow);
        TextView title=label("Домашний пульт",32,INK); title.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));
        page.addView(title,params(-1,-2,8));
        page.addView(label("Навигация, звук и воспроизведение",15,MUTED),params(-1,-2,4));
        Button receiver=button("Приёмник по Wi-Fi",ACCENT,INK); page.addView(receiver,params(-1,52,12));
        receiver.setOnClickListener(v -> { disconnect(); startActivity(new Intent(this,ReceiverActivity.class)); });
        Button installer=button("Установить приёмник на проектор",PANEL,INK); page.addView(installer,params(-1,52,6));
        installer.setOnClickListener(v -> { disconnect(); startActivity(new Intent(this,InstallReceiverActivity.class)); });
        LinearLayout tabs=row(); page.addView(tabs,params(-1,50,18));
        wifiTab=button("Wi‑Fi",ACCENT,INK); bluetoothTab=button("Bluetooth",PANEL,INK);
        tabs.addView(wifiTab,new LinearLayout.LayoutParams(0,-1,1));
        LinearLayout.LayoutParams tabParams=new LinearLayout.LayoutParams(0,-1,1); tabParams.leftMargin=dp(8); tabs.addView(bluetoothTab,tabParams);
        wifiTab.setOnClickListener(v -> switchMode(false)); bluetoothTab.setOnClickListener(v -> switchMode(true));

        LinearLayout card=column(); card.setPadding(dp(18),dp(16),dp(18),dp(16)); card.setBackground(shape(PANEL,20));
        page.addView(card,params(-1,-2,22));
        status=label("Сначала подключим проектор",17,INK); status.setTypeface(null,Typeface.BOLD); card.addView(status);
        detail=label("Эта версия работает через ADB по сети. На проекторе нужно разрешить этот способ управления.",14,MUTED);
        card.addView(detail,params(-1,-2,6));
        LinearLayout fields=row(); wifiFields=fields; card.addView(fields,params(-1,-2,12));
        LinearLayout ipField=column(); fields.addView(ipField,new LinearLayout.LayoutParams(0,-2,1));
        ipField.addView(label("IP проектора",12,MUTED)); address=input("192.168.1.100");
        address.setText(getPreferences(0).getString("ip","")); ipField.addView(address,new LinearLayout.LayoutParams(-1,dp(52)));
        LinearLayout portField=column(); LinearLayout.LayoutParams pf=new LinearLayout.LayoutParams(dp(76),-2); pf.leftMargin=dp(12); fields.addView(portField,pf);
        portField.addView(label("Порт ADB",12,MUTED)); port=input("5555"); port.setInputType(InputType.TYPE_CLASS_NUMBER);
        port.setText(getPreferences(0).getString("port","5555")); portField.addView(port,new LinearLayout.LayoutParams(-1,dp(52)));
        bluetoothFields=column(); card.addView(bluetoothFields,params(-1,-2,12)); bluetoothFields.setVisibility(View.GONE);
        bluetoothFields.addView(label("Телевизор должен поддерживать Bluetooth-клавиатуры. Оставь приложение открытым во время управления.",14,MUTED));
        btVisible=button("Сделать телефон видимым",Color.WHITE,INK); bluetoothFields.addView(btVisible,params(-1,52,10)); btVisible.setOnClickListener(v -> discoverable());
        btPaired=button("Выбрать связанное устройство",Color.WHITE,INK); bluetoothFields.addView(btPaired,params(-1,52,8)); btPaired.setOnClickListener(v -> chooseBluetooth());
        btFind=button("Найти проектор",ACCENT,INK); bluetoothFields.addView(btFind,params(-1,52,8)); btFind.setOnClickListener(v -> findBluetooth());
        connect=button("Подключить",INK,Color.WHITE); card.addView(connect,params(-1,52,14)); connect.setOnClickListener(v -> {
            if(connected || busy || (bluetoothMode && btReady)) disconnect(); else if(bluetoothMode) startBluetooth(); else connect();
        });
        diagnose=button("Проверить доступность",Color.TRANSPARENT,INK); card.addView(diagnose,params(-1,48,4)); diagnose.setOnClickListener(v -> diagnose());

        LinearLayout dpad=column(); dpad.setGravity(Gravity.CENTER); page.addView(dpad,params(-1,-2,24));
        addControl(dpad,"↑",19,78,64);
        LinearLayout middle=row(); middle.setGravity(Gravity.CENTER); dpad.addView(middle,params(-1,-2,7));
        addControl(middle,"←",21,78,64); addControl(middle,"OK",23,84,72); addControl(middle,"→",22,78,64);
        addControl(dpad,"↓",20,78,64);
        LinearLayout nav=row(); page.addView(nav,params(-1,-2,18));
        addControl(nav,"Назад",4,0,54); addControl(nav,"Домой",3,0,54); addControl(nav,"Меню",82,0,54);
        LinearLayout settings=row(); page.addView(settings,params(-1,-2,9));
        addControl(settings,"Настройки",176,0,54);
        focusHelp=button("Фокус в меню",PANEL,INK); LinearLayout.LayoutParams focusParams=new LinearLayout.LayoutParams(0,dp(54),1); focusParams.setMargins(dp(4),dp(3),dp(4),dp(3)); settings.addView(focusHelp,focusParams);
        focusHelp.setOnClickListener(v -> focusHelp());
        LinearLayout sound=row(); page.addView(sound,params(-1,-2,9));
        addControl(sound,"Звук −",25,0,56); addControl(sound,"Без звука",164,0,56); addControl(sound,"Звук +",24,0,56);
        LinearLayout media=row(); page.addView(media,params(-1,-2,9));
        addControl(media,"Пауза / пуск",85,0,52); Button sleep=addControl(media,"Сон",223,0,52);
        sleep.setOnClickListener(v -> new AlertDialog.Builder(this).setTitle("Перевести Android в сон?")
            .setMessage("Проектор может перестать отвечать по Wi‑Fi. Для включения может понадобиться кнопка на корпусе. Это не команда отключения питания лампы.")
            .setNegativeButton("Отмена",null).setPositiveButton("Сон",(dialog,which) -> send(223)).show());
        Button help=button("Как подключить",Color.TRANSPARENT,INK); page.addView(help,params(-1,52,20)); help.setOnClickListener(v -> help());
        TextView footer=label("Версия 0.5 · Совместимость зависит от техники и её прошивки.",12,MUTED); footer.setGravity(Gravity.CENTER); page.addView(footer);
        if(getPreferences(0).getBoolean("bluetoothMode",false)) switchMode(true);
        updateControls();
    }

    private void focusHelp() {
        new AlertDialog.Builder(this).setTitle("Настройка фокуса")
            .setMessage("Нажми «Открыть настройки», затем стрелками и OK выбери на проекторе пункт «Фокус» или «Резкость». Регулируй его теми стрелками, которые указаны на экране проектора.\n\nЕсли кнопка настроек не сработала, используй «Домой» или «Меню» и перейди к настройкам вручную.\n\nПрямые команды мотора «Фокус +/−» для Akenori LED-320 New пока не определены. Этот пульт управляет фокусом через доступное меню проектора.")
            .setNegativeButton("Понятно",null).setPositiveButton("Открыть настройки",(dialog,which) -> send(176)).show();
    }
    private void switchMode(boolean useBluetooth) {
        disconnect(); bluetoothMode=useBluetooth;
        getPreferences(0).edit().putBoolean("bluetoothMode",useBluetooth).apply();
        wifiFields.setVisibility(useBluetooth?View.GONE:View.VISIBLE); bluetoothFields.setVisibility(useBluetooth?View.VISIBLE:View.GONE);
        diagnose.setVisibility(useBluetooth?View.GONE:View.VISIBLE);
        wifiTab.setBackground(shape(useBluetooth?PANEL:ACCENT,16)); bluetoothTab.setBackground(shape(useBluetooth?ACCENT:PANEL,16));
        showStatus(useBluetooth?"Bluetooth-пульт":"Подключение по Wi‑Fi",useBluetooth?
            "Включи пульт, затем подключи телефон в меню Bluetooth-клавиатур на телевизоре. Wi‑Fi для этого режима не нужен.":
            "Введи IP проектора. Нужен Android с включённым классическим ADB по сети.",false); updateControls();
    }
    private void startBluetooth() {
        bluetoothWanted=true;
        if(Build.VERSION.SDK_INT<28) { showStatus("Нужен Android 9 или новее","Этот телефон не поддерживает API Bluetooth-пульта.",true); return; }
        if(Build.VERSION.SDK_INT>=31 && (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)!=PackageManager.PERMISSION_GRANTED || checkSelfPermission(Manifest.permission.BLUETOOTH_ADVERTISE)!=PackageManager.PERMISSION_GRANTED)) {
            requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT,Manifest.permission.BLUETOOTH_ADVERTISE},90); return;
        }
        if(bluetooth!=null) bluetooth.close();
        int token=++generation; connected=false; btReady=false; busy=true; updateControls();
        bluetooth=new BluetoothRemote(this,(ready,linked,title,message) -> ui(token,() -> {
            btReady=ready; connected=linked; busy=false; showStatus(title,message,false); updateControls();
            if(ready && !linked && pendingBluetooth!=null) {
                BluetoothDevice target=pendingBluetooth; pendingBluetooth=null;
                try { bluetooth.connect(target); } catch(SecurityException e) { showError(e); }
            }
        }));
        if(!bluetooth.enabled()) {
            busy=false; updateControls();
            try { startActivityForResult(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE),91); }
            catch(ActivityNotFoundException e) { showStatus("Bluetooth недоступен","На телефоне нет доступного Bluetooth-адаптера.",true); }
            return;
        }
        showStatus("Включаем Bluetooth-пульт…","Проверяем, поддерживает ли телефон режим клавиатуры.",false); bluetooth.start();
    }
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] results) {
        super.onRequestPermissionsResult(request,permissions,results);
        if(request==90 && bluetoothMode) {
            boolean allowed=results.length==2; for(int value:results) allowed&=value==PackageManager.PERMISSION_GRANTED;
            if(allowed) startBluetooth(); else { bluetoothWanted=false; showStatus("Нужно разрешение","Разреши «Устройства поблизости», чтобы телефон мог подключиться к телевизору.",true); }
        }
        if(request==93 && bluetoothMode) {
            if(results.length>0 && results[0]==PackageManager.PERMISSION_GRANTED) findBluetooth();
            else showStatus("Поиск не разрешён","Разреши поиск устройств поблизости. На Android 9–11 поиск требует разрешения геолокации.",true);
        }
    }
    @Override protected void onActivityResult(int request,int result,Intent data) {
        super.onActivityResult(request,result,data);
        if(request==91 && bluetoothMode) {
            if(result==RESULT_OK) startBluetooth(); else showStatus("Bluetooth выключен","Без Bluetooth этот режим пульта недоступен.",true);
        } else if(request==92 && bluetoothMode) {
            if(result>0) {
                showStatus("Телефон виден для сопряжения","В течение "+result+" секунд выбери этот телефон в меню Bluetooth-клавиатур телевизора и подтверди сопряжение на обоих устройствах.",false);
                int token=generation;
                timers.postDelayed(() -> { if(generation==token && bluetoothMode && btReady && !connected) showStatus("Время видимости истекло","Для нового сопряжения снова нажми «Сделать телефон видимым». Уже связанный телевизор можно выбрать из списка.",false); },result*1000L);
            }
            else showStatus("Видимость не включена","Нажми «Сделать телефон видимым» и разреши видимость, либо выбери ранее связанный телевизор.",false);
        }
    }
    private void discoverable() {
        if(Build.VERSION.SDK_INT<28 || !btReady) return;
        try { Intent intent=new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE); intent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION,120); startActivityForResult(intent,92); }
        catch(SecurityException | ActivityNotFoundException e) { showStatus("Не удалось включить видимость","Проверь разрешение «Устройства поблизости» и Bluetooth на телефоне.",true); }
    }
    private void chooseBluetooth() {
        if(Build.VERSION.SDK_INT<28 || bluetooth==null || !btReady) return;
        try {
            java.util.List<BluetoothDevice> devices=bluetooth.paired();
            if(devices.isEmpty()) { showStatus("Пока нет связанных устройств","Сделай телефон видимым и найди его через меню Bluetooth-клавиатур на телевизоре.",false); return; }
            String[] labels=new String[devices.size()]; for(int i=0;i<labels.length;i++) labels[i]=BluetoothRemote.name(devices.get(i));
            new AlertDialog.Builder(this).setTitle("Выбери телевизор или проектор").setItems(labels,(dialog,index) -> {
                if(bluetooth!=null) bluetooth.connect(devices.get(index));
            }).setNegativeButton("Отмена",null).show();
        } catch(SecurityException e) { showStatus("Нет разрешения Bluetooth","Разреши «Устройства поблизости» в настройках приложения.",true); }
    }

    private void findBluetooth() {
        if(Build.VERSION.SDK_INT<28 || !btReady) return;
        String permission=Build.VERSION.SDK_INT>=31?Manifest.permission.BLUETOOTH_SCAN:Manifest.permission.ACCESS_FINE_LOCATION;
        if(checkSelfPermission(permission)!=PackageManager.PERMISSION_GRANTED) { requestPermissions(new String[]{permission},93); return; }
        if(discovery!=null) discovery.close();
        ArrayList<BluetoothDevice> devices=new ArrayList<>();
        ArrayAdapter<String> items=new ArrayAdapter<>(this,android.R.layout.simple_list_item_1,new ArrayList<>());
        LinearLayout panel=column(); panel.setPadding(dp(20),dp(10),dp(20),0);
        TextView scanState=label("Ищем проектор…",14,MUTED); panel.addView(scanState);
        ListView list=new ListView(this); list.setAdapter(items); panel.addView(list,new LinearLayout.LayoutParams(-1,dp(280)));
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Устройства поблизости").setView(panel)
            .setNegativeButton("Закрыть",null).setNeutralButton("Повторить поиск",null).create();
        try {
            discovery=new BluetoothDiscovery(this,new BluetoothDiscovery.Listener() {
                public void found(BluetoothDevice device) {
                    if(!dialog.isShowing() || devices.contains(device)) return;
                    devices.add(device);
                    items.add(BluetoothRemote.name(device)+"\n"+device.getAddress());
                }
                public void status(String message) {
                    if(isDestroyed()) return;
                    scanState.setText(message); showStatus("Bluetooth: поиск и сопряжение",message,false);
                }
                public void paired(BluetoothDevice device) {
                    if(isDestroyed() || !bluetoothMode || !bluetoothWanted) return;
                    pendingBluetooth=device;
                    if(btReady && bluetooth!=null) {
                        pendingBluetooth=null; bluetooth.connect(device);
                    } else startBluetooth();
                }
            });
            BluetoothDiscovery active=discovery;
            list.setOnItemClickListener((parent,view,position,id) -> {
                BluetoothDevice device=devices.get(position); dialog.dismiss();
                new AlertDialog.Builder(this).setTitle("Подключить «"+BluetoothRemote.name(device)+"»?")
                    .setMessage("Выбери это устройство, если это твой проектор или телевизор. Подтверди системный запрос сопряжения на обоих устройствах.")
                    .setNegativeButton("Отмена",null).setPositiveButton("Сопрячь",(d,w) -> {
                        try { active.pair(device); } catch(SecurityException e) { showError(e); }
                    }).show();
            });
            dialog.setOnDismissListener(d -> active.stopScan());
            dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> active.start()));
            dialog.show(); active.start();
        } catch(SecurityException e) { showError(e); }
    }

    private void help() {
        if(bluetoothMode) {
            new AlertDialog.Builder(this).setTitle("Подключение по Bluetooth")
                .setMessage("1. Нажми «Включить пульт» и разреши доступ к устройствам поблизости.\n\n2. Нажми «Сделать телефон видимым».\n\n3. На телевизоре или проекторе открой меню Bluetooth-клавиатур / устройств ввода. Найди телефон и подтверди сопряжение на обоих устройствах.\n\n4. Если устройства уже сопряжены, нажми «Выбрать связанное устройство» и выбери ТВ.\n\nПосле надписи «Bluetooth подключён» проверь стрелки и OK. Работоспособность громкости, «Домой» и «Назад» зависит от телевизора.\n\nНаличие Bluetooth для наушников не означает поддержку клавиатуры. Некоторые телевизоры и прошивки телефона не поддерживают этот способ. Включение выключенного телевизора и сон через Bluetooth не реализованы.\n\nДержи приложение открытым: Android может отключить режим клавиатуры в фоне. Сопряжение само по себе ещё не означает подключение пульта.")
                .setPositiveButton("Понятно",null).show(); return;
        }
        new AlertDialog.Builder(this).setTitle("Подключение по Wi‑Fi")
            .setMessage("1. Включи проектор кнопкой на корпусе. Подключи проектор и телефон к одной домашней сети.\n\n2. Найди IP проектора в настройках Wi‑Fi или в разделе «Об устройстве». Введи его здесь.\n\n3. На проекторе должен быть включён классический ADB по сети (TCP/IP), обычно на порту 5555. Одного включения Wi‑Fi или USB-отладки может быть недостаточно. Доступность этого режима зависит от прошивки.\n\n4. Нажми «Подключить». Если проектор покажет запрос от projector-remote@phone — разреши его.\n\nЕсли в настройках только сопряжение по шестизначному коду, эта версия его не поддерживает. Если сетевой отладки нет, потребуется другой способ управления.\n\nСетевая отладка даёт доступ к управлению Android. Используй её только в своей домашней сети и выключай, когда она не нужна.\n\nBluetooth доступен на отдельной вкладке. ИК-коды, включение из розетки, фокус и коррекция изображения не реализованы.")
            .setPositiveButton("Понятно",null).show();
    }
    private String[] endpoint() {
        String ip=address.getText().toString().trim(), p=port.getText().toString().trim();
        if(!AdbClient.isPrivateIpv4(ip)) { address.setError("Нужен локальный IP, например 192.168.1.100"); address.requestFocus(); return null; }
        try { int n=Integer.parseInt(p); if(n<1 || n>65535) throw new NumberFormatException(); }
        catch(NumberFormatException e) { port.setError("Порт от 1 до 65535"); return null; }
        return new String[]{ip,p};
    }
    private SocketFactory wifi() {
        ConnectivityManager cm=(ConnectivityManager)getSystemService(CONNECTIVITY_SERVICE);
        for(Network network:cm.getAllNetworks()) {
            NetworkCapabilities caps=cm.getNetworkCapabilities(network);
            if(caps!=null && caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) && !caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) return network.getSocketFactory();
        }
        throw new IllegalStateException("Подключи телефон к той же сети Wi‑Fi, что и проектор");
    }
    private void connect() {
        String[] endpoint=endpoint(); if(endpoint==null) return;
        final SocketFactory sockets;
        try { sockets=wifi(); } catch(Exception e) { showError(e); return; }
        getPreferences(0).edit().putString("ip",endpoint[0]).putString("port",endpoint[1]).apply();
        int token=++generation; AdbClient candidate=new AdbClient(); client=candidate; busy=true; connected=false;
        showStatus("Подключаемся…","Проверяем ADB на "+endpoint[0]+":"+endpoint[1],false); updateControls();
        worker.execute(() -> {
            try {
                candidate.connect(endpoint[0],Integer.parseInt(endpoint[1]),sockets,keyPair(),message -> ui(token,() -> detail.setText(message)));
                String model=candidate.shell("getprop ro.product.model");
                String version=candidate.shell("getprop ro.build.version.release");
                if(model.isEmpty() || version.isEmpty()) throw new java.io.IOException("Устройство не подтвердило Android");
                ui(token,() -> { connected=true; busy=false;
                    showStatus("Android подключён",model+" · Android "+version+"\nНажми стрелку и проверь реакцию проектора.",false); updateControls(); });
            } catch(Exception e) { candidate.close(); ui(token,() -> { client=null; busy=false; connected=false; showError(e); updateControls(); }); }
        });
    }
    private void diagnose() {
        String[] endpoint=endpoint(); if(endpoint==null) return;
        final SocketFactory sockets;
        try { sockets=wifi(); } catch(Exception e) { showError(e); return; }
        int token=++generation; busy=true; showStatus("Проверяем адрес…","Это проверка доступности портов, без отправки команд управления.",false); updateControls();
        worker.execute(() -> {
            int adbPort=Integer.parseInt(endpoint[1]);
            LinkedHashSet<Integer> ports=new LinkedHashSet<>(Arrays.asList(adbPort,6466,6467));
            StringBuilder found=new StringBuilder(); boolean adbOpen=false, tvOpen=false;
            for(int n:ports) {
                if(generation!=token) return;
                try(Socket socket=sockets.createSocket()) {
                    socket.connect(new InetSocketAddress(endpoint[0],n),1200);
                    found.append("Порт ").append(n).append(" доступен.\n");
                    if(n==adbPort) adbOpen=true; if(n==6466 || n==6467) tvOpen=true;
                } catch(Exception ignored) {}
            }
            String result=found.toString();
            if(adbOpen) result+="\nНажми «Подключить», чтобы проверить протокол ADB. Открытый порт сам по себе не подтверждает совместимость.";
            else if(tvOpen) result+="\nВозможно, доступен сервис Android TV. Это приложение пока поддерживает только классический ADB/TCP.";
            else result="Проверенные порты не отвечают. Проверь IP, общую сеть и настройки сетевой отладки. Это не означает, что у проектора нет Wi‑Fi.";
            String message=result; ui(token,() -> { busy=false; showStatus("Проверка завершена",message,false); updateControls(); });
        });
    }
    private void send(int code) {
        if(bluetoothMode) { sendBluetooth(code); return; }
        AdbClient active=client; if(!connected || busy || active==null) return;
        int token=generation; busy=true; updateControls();
        worker.execute(() -> { try { active.key(code); ui(token,() -> { busy=false; detail.setText("Команда передана Android. Проверь реакцию на экране проектора."); updateControls(); }); }
            catch(Exception e) { active.close(); ui(token,() -> { client=null; connected=false; busy=false; showError(e); updateControls(); }); }
        });
    }
    private void sendBluetooth(int code) {
        BluetoothRemote active=bluetooth; if(Build.VERSION.SDK_INT<28 || !connected || busy || active==null) return;
        int token=generation; busy=true; updateControls();
        worker.execute(() -> {
            try { active.key(code); ui(token,() -> { busy=false; detail.setText("Нажатие отправлено по Bluetooth. Проверь реакцию телевизора."); updateControls(); }); }
            catch(Exception e) { ui(token,() -> { busy=false; showStatus("Команда не отправлена",e.getMessage()==null?"Проверь Bluetooth-соединение":e.getMessage(),true); updateControls(); }); }
        });
    }
    private KeyPair keyPair() throws Exception {
        android.content.SharedPreferences prefs=getPreferences(0);
        String priv=prefs.getString("privateKey",null), pub=prefs.getString("publicKey",null);
        if(priv!=null && pub!=null) {
            KeyFactory factory=KeyFactory.getInstance("RSA");
            return new KeyPair(factory.generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(pub))),factory.generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(priv))));
        }
        KeyPairGenerator generator=KeyPairGenerator.getInstance("RSA"); generator.initialize(2048); KeyPair pair=generator.generateKeyPair();
        if(!prefs.edit().putString("privateKey",Base64.getEncoder().encodeToString(pair.getPrivate().getEncoded()))
            .putString("publicKey",Base64.getEncoder().encodeToString(pair.getPublic().getEncoded())).commit())
            throw new java.io.IOException("Не удалось сохранить ключ подключения");
        return pair;
    }
    private void disconnect() {
        bluetoothWanted=false; pendingBluetooth=null;
        if(discovery!=null) { discovery.close(); discovery=null; }
        timers.removeCallbacksAndMessages(null);
        ++generation; AdbClient active=client; client=null; if(active!=null) active.close();
        if(bluetooth!=null && Build.VERSION.SDK_INT>=28) bluetooth.close(); bluetooth=null; btReady=false;
        connected=false; busy=false; showStatus("Нет подключения","Введи IP проектора и нажми «Подключить».",false); updateControls();
    }
    private void ui(int token,Runnable action) { runOnUiThread(() -> { if(generation==token && !isDestroyed() && !isFinishing()) action.run(); }); }
    private void showError(Exception e) {
        String message=e.getMessage();
        if(e instanceof SocketTimeoutException) message="Проектор не ответил вовремя. Проверь IP и разреши подключение на его экране, если появился запрос.";
        else if(e instanceof ConnectException) message="Порт недоступен. Проверь IP и включи ADB по сети на проекторе. Одного Wi‑Fi недостаточно.";
        showStatus("Подключение не готово",message==null?"Соединение прервано. Повтори подключение.":message,true);
    }
    private void showStatus(String title,String text,boolean error) { status.setText(title); status.setTextColor(error?ERROR:INK); detail.setText(text); }
    private void updateControls() {
        for(Button b:controls) { boolean available=connected&&!busy&&!(bluetoothMode&&"Сон".contentEquals(b.getText())); b.setEnabled(available); b.setAlpha(available?1f:.38f); }
        focusHelp.setEnabled(connected&&!busy); focusHelp.setAlpha(connected&&!busy?1f:.38f);
        wifiFields.setVisibility(!bluetoothMode&&!connected?View.VISIBLE:View.GONE);
        bluetoothFields.setVisibility(bluetoothMode&&!connected?View.VISIBLE:View.GONE);
        diagnose.setVisibility(!bluetoothMode&&!connected?View.VISIBLE:View.GONE);
        address.setEnabled(!connected&&!busy); port.setEnabled(!connected&&!busy);
        diagnose.setEnabled(!connected&&!busy);
        btVisible.setEnabled(bluetoothMode&&btReady&&!connected); btPaired.setEnabled(bluetoothMode&&btReady&&!busy);
        btFind.setEnabled(bluetoothMode&&btReady&&!busy&&!connected);
        connect.setText(connected?"Отключить":busy?"Отменить":bluetoothMode?(btReady?"Выключить пульт":"Включить пульт"):"Подключить");
    }
    @Override protected void onStop() { super.onStop(); if(!bluetoothMode && status!=null) disconnect(); }
    @Override protected void onResume() {
        super.onResume();
        timers.postDelayed(() -> { if(!isDestroyed() && bluetoothMode && bluetoothWanted && !btReady && !busy) startBluetooth(); },400);
    }
    @Override protected void onDestroy() { ++generation; timers.removeCallbacksAndMessages(null); if(discovery!=null) discovery.close(); if(client!=null) client.close(); if(bluetooth!=null && Build.VERSION.SDK_INT>=28) bluetooth.close(); worker.shutdownNow(); super.onDestroy(); }
    private int dp(float value) { return Math.round(value*getResources().getDisplayMetrics().density); }
    private LinearLayout column() { LinearLayout l=new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); return l; }
    private LinearLayout row() { LinearLayout l=new LinearLayout(this); l.setOrientation(LinearLayout.HORIZONTAL); return l; }
    private LinearLayout.LayoutParams params(int width,int height,int top) { LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(width<0?width:dp(width),height<0?height:dp(height)); p.topMargin=dp(top); return p; }
    private GradientDrawable shape(int color,int radius) { GradientDrawable d=new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(radius)); return d; }
    private TextView label(String text,int size,int color) { TextView t=new TextView(this); t.setText(text); t.setTextSize(size); t.setTextColor(color); t.setLineSpacing(dp(2),1); return t; }
    private EditText input(String hint) { EditText e=new EditText(this); e.setSingleLine(true); e.setTextColor(INK); e.setHintTextColor(MUTED); e.setHint(hint); e.setTextSize(17); e.setInputType(InputType.TYPE_CLASS_PHONE); e.setSelectAllOnFocus(true); return e; }
    private Button button(String title,int color,int text) {
        Button b=new RemoteButton(this); b.setText(title); b.setTextSize(15); b.setTextColor(text); b.setAllCaps(false); b.setMinWidth(0); b.setMinimumWidth(0); b.setPadding(dp(4),dp(4),dp(4),dp(4));
        b.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x22192c25),shape(color,16),shape(Color.WHITE,16))); return b;
    }
    private Button addControl(LinearLayout parent,String text,int code,int width,int height) {
        Button b=button(text,code==23?ACCENT:PANEL,INK); if(text.length()==1) b.setTextSize(27);
        if(code==23) b.setTypeface(null,Typeface.BOLD);
        String name=code==19?"Вверх":code==20?"Вниз":code==21?"Влево":code==22?"Вправо":text; b.setContentDescription(name);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(width==0?0:dp(width),dp(height),width==0?1:0); p.setMargins(dp(4),dp(3),dp(4),dp(3));
        parent.addView(b,p); controls.add(b); b.setOnClickListener(v -> send(code)); return b;
    }
}
