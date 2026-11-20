package local.akenori.remote;

import android.app.*;
import android.content.*;
import android.content.res.ColorStateList;
import android.graphics.*;
import android.graphics.drawable.*;
import android.net.*;
import android.os.*;
import android.text.*;
import android.text.style.*;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.util.*;
import java.util.concurrent.*;
import javax.net.SocketFactory;

public class ReceiverActivity extends Activity {
    private static final int BG=0xfff3f2ec,INK=0xff192b24,MUTED=0xff69736b,LINE=0xffdce1d6,LIME=0xffc9e79a,DARK=0xff1a2b24,WHITE=0xfffcfdf8;
    private final ThreadPoolExecutor worker=new ThreadPoolExecutor(1,1,0,TimeUnit.SECONDS,new LinkedBlockingQueue<>());
    private final ExecutorService infraredWorker=Executors.newSingleThreadExecutor();
    private boolean infraredBusy; private long lastInfrared;
    private final Handler timers=new Handler(Looper.getMainLooper());
    private final ArrayList<RemoteButton> controls=new ArrayList<>(),panelControls=new ArrayList<>();
    private final ArrayList<SeekBar> panelSliders=new ArrayList<>();
    private ReceiverClient client; private boolean linked,busy; private volatile int generation; private int pending;
    private TextView status,latency,panelMessage; private RemoteButton connection;
    private Dialog panel; private LinearLayout panelItems; private AlertDialog pairing;
    private boolean foreground,automaticConnection; private int reconnectAttempt; private long attemptedUpdate=-1;
    private ConnectivityManager.NetworkCallback networkCallback;
    private boolean directFocus,probing;
    private final Runnable heartbeat=new Runnable() { public void run() {
        if(!foreground) return;
        if(linked && !busy && pending==0 && !probing) {
            probing=true; int current=generation; ReceiverClient destination=client;
            worker.execute(() -> {
                try {
                    JSONObject response=destination.request("/status",new JSONObject());
                    runOnUiThread(() -> { if(current!=generation || !foreground) return; probing=false;
                        if(!response.optBoolean("accessibility",true)) failure(new java.io.IOException("Служба управления запускается"));
                    });
                } catch(Exception e) { runOnUiThread(() -> { if(current==generation && foreground) { probing=false; automaticConnection=true; failure(e); } }); }
            });
        }
        timers.postDelayed(this,5000);
    }};
    private final Runnable reconnect=this::reconnectSaved;
    private JSONObject receiverStatus; private ReceiverUpdate bundledUpdate;
    private String adjustment="", selectedCorner="lt";
    private JSONObject projectorState;
    private TextView adjustmentValue;
    private RemoteButton nextPage;
    private LinearLayout imageSection,focusSection;
    private RemoteButton imageToggle;
    private boolean imageExpanded;
    private RemoteButton tvSettings;
    private TextView brand;
    private final Runnable refreshPanel=() -> { if(panel!=null && panel.isShowing() && linked && !busy && pending==0) readPanel(); };

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved); getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().setStatusBarColor(BG); getWindow().setNavigationBarColor(BG);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        ScrollView scroll=new ScrollView(this); scroll.setFillViewport(true); scroll.setBackgroundColor(BG);
        LinearLayout page=column(); page.setPadding(dp(22),dp(18),dp(22),dp(28)); scroll.addView(page); setContentView(scroll);
        LinearLayout identity=row(); identity.setGravity(Gravity.CENTER_VERTICAL); page.addView(identity);
        ImageView logo=new ImageView(this); logo.setImageResource(R.drawable.ic_remote); logo.setContentDescription("Домашний пульт"); identity.addView(logo,new LinearLayout.LayoutParams(dp(36),dp(36)));
        brand=text("ДОМАШНИЙ ПУЛЬТ",11,MUTED); brand.setPadding(dp(10),0,0,0); brand.setLetterSpacing(.12f); identity.addView(brand,new LinearLayout.LayoutParams(0,-2,1));
        RemoteButton power=button("","power",WHITE,INK,20,() -> power()); power.setContentDescription("Питание проектора"); identity.addView(power,new LinearLayout.LayoutParams(dp(48),dp(48)));
        LinearLayout header=row(); header.setGravity(Gravity.CENTER_VERTICAL); page.addView(header,lp(-1,54,5));
        TextView title=text("Пульт",32,INK); title.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL)); header.addView(title,new LinearLayout.LayoutParams(0,-2,1));
        connection=button("Подключить","wifi",WHITE,INK,14,() -> connectionOptions()); header.addView(connection,new LinearLayout.LayoutParams(dp(132),dp(48)));
        focusSection=column(); page.addView(focusSection);
        TextView section=text("ФОКУСИРОВКА ЛИНЗЫ",11,MUTED); section.setLetterSpacing(.12f); focusSection.addView(section,lp(-1,-2,14));
        LinearLayout focusKeys=row(); focusSection.addView(focusKeys,lp(-1,58,8));
        quickFocus(focusKeys,"F−","focus_minus"); quickFocus(focusKeys,"F+","focus_plus");
        imageSection=column();
        boolean singleColumn=getResources().getConfiguration().screenWidthDp<340 || getResources().getConfiguration().fontScale>1.2f;
        LinearLayout first=row(); if(singleColumn) first.setOrientation(LinearLayout.VERTICAL); imageSection.addView(first,lp(-1,-2,10));
        tile(first,"Фокус","Резкость линзы","focus",true,() -> openSetting("Фокус",new String[]{"Фокус","Фокусировка","Настройка фокуса","Электронная фокусировка","Focus","Focus adjustment"}));
        tile(first,"Масштаб","Размер кадра","scale",false,() -> openSetting("Масштаб",new String[]{"Масштабирование экрана","Масштаб","Zoom"}));
        LinearLayout second=row(); if(singleColumn) second.setOrientation(LinearLayout.VERTICAL); imageSection.addView(second,lp(-1,-2,10));
        tile(second,"Трапеция","Наклон сторон","keystone",false,() -> openSetting("Трапеция",new String[]{"Настройка трапеции","Коррекция трапеции","Keystone"}));
        tile(second,"Углы","Выравнивание","align",false,() -> openSetting("Выравнивание",new String[]{"Четырехточечное трапециальное искажение","Четырёхточечная коррекция","4-point keystone"}));
        RemoteButton rotate=button("Положение изображения","rotate",WHITE,INK,15,() -> openSetting("Положение изображения",new String[]{"Режим проекции","Метод проецирования","Projection mode"})); controls.add(rotate); imageSection.addView(rotate,lp(-1,48,10));
        tvSettings=button("Настройки телевизора","settings",WHITE,INK,16,() -> allSettings()); controls.add(tvSettings); page.addView(tvSettings,lp(-1,54,16));
        LinearLayout remote=column(); remote.setPadding(dp(16),dp(12),dp(16),dp(12)); remote.setBackground(shape(DARK,28)); page.addView(remote,lp(-1,-2,20));
        LinearLayout meta=row(); TextView remoteLabel=text("ПУЛЬТ",11,0xffa9b7ab); remoteLabel.setLetterSpacing(.14f); meta.addView(remoteLabel,new LinearLayout.LayoutParams(0,-2,1)); latency=text("",11,0xffa9b7ab); meta.addView(latency); remote.addView(meta);
        int padWidth=Math.min(252,Math.max(200,getResources().getConfiguration().screenWidthDp-76));
        FrameLayout pad=new FrameLayout(this); LinearLayout.LayoutParams padLayout=lp(padWidth,Math.round(228f*padWidth/252),0); padLayout.gravity=Gravity.CENTER; remote.addView(pad,padLayout);
        padKey(pad,"Вверх","up",90,0,72,66); padKey(pad,"Влево","left",10,78,72,72);
        RemoteButton ok=button("OK",null,LIME,DARK,22,null); ok.setTypeface(null,Typeface.BOLD); ok.instant(() -> key("ok"),false); controls.add(ok); frame(pad,ok,86,76,80,76);
        padKey(pad,"Вправо","right",170,78,72,72); padKey(pad,"Вниз","down",90,160,72,66);
        status=text("Подключи устройство к домашней сети",13,0xffd3ddcf); status.setGravity(Gravity.CENTER); status.setMinHeight(dp(32)); status.setMaxLines(2); status.setEllipsize(TextUtils.TruncateAt.END); remote.addView(status,lp(-1,-2,0));
        LinearLayout nav=row(); page.addView(nav,lp(-1,52,10)); navKey(nav,"Назад","back"); navKey(nav,"Домой","home"); navKey(nav,"Меню","menu");
        LinearLayout sound=row(); page.addView(sound,lp(-1,52,10));
        soundKey(sound,"Тише","sound_minus","volume_down");
        RemoteButton mute=button("Звук",null,WHITE,INK,14,null); mute.setContentDescription("Включить или выключить звук"); mute.instant(() -> key("mute"),false); controls.add(mute); sound.addView(mute,weighted(52,4));
        soundKey(sound,"Громче","sound_plus","volume_up");
        imageToggle=button("Настройки изображения","settings",WHITE,INK,16,() -> { imageExpanded=!imageExpanded; updateDeviceKind(); }); page.addView(imageToggle,lp(-1,52,14));
        page.addView(imageSection);
        RemoteButton screen=button("Экран устройства","screen",WHITE,INK,16,() -> { if(ready()) { showPanel("Экран устройства"); readPanel(); } }); controls.add(screen); page.addView(screen,lp(-1,52,14));
        LinearLayout footer=row(); footer.setGravity(Gravity.CENTER_VERTICAL); page.addView(footer,lp(-1,52,12));
        Switch haptics=new Switch(this); haptics.setText("Виброотклик"); haptics.setTextSize(14); haptics.setTextColor(MUTED); haptics.setChecked(getSharedPreferences("remote_ui",0).getBoolean("haptics",true)); footer.addView(haptics,new LinearLayout.LayoutParams(0,-1,1));
        haptics.setOnCheckedChangeListener((v,on) -> { getSharedPreferences("remote_ui",0).edit().putBoolean("haptics",on).apply(); if(on) haptics.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK); });
        RemoteButton more=button("Ещё",null,BG,MUTED,14,() -> options()); footer.addView(more,new LinearLayout.LayoutParams(dp(72),dp(48)));
        RemoteButton help=button("Как подключить · Инструкция","wifi",BG,MUTED,14,() -> startActivity(new Intent(this,GuideActivity.class))); page.addView(help,lp(-1,48,2));
        updateDeviceKind(); enabled();
    }
    private void options() {
        new AlertDialog.Builder(this).setTitle("Домашний пульт").setItems(new String[]{"Подключение к устройству","Установить приёмник на ТВ / проектор","Обновления по Wi-Fi","Все настройки устройства","Bluetooth и другие устройства","Инструкция и выбор устройства","Тип устройства: проектор / ТВ"},(d,index) -> {
            if(index==0) connectionOptions(); else if(index==1) startActivity(new Intent(this,InstallReceiverActivity.class));
            else if(index==2) updates();
            else if(index==3) allSettings();
            else if(index==4) startActivity(new Intent(this,MainActivity.class).putExtra("legacy",true));
            else if(index==6) new AlertDialog.Builder(this).setTitle("Тип устройства").setItems(new String[]{"Проектор · с фокусировкой","Телевизор"},(dialog,kind) -> { getSharedPreferences("remote_ui",0).edit().putString("device_kind",kind==0?"projector":"tv").apply(); updateDeviceKind(); }).show();
            else startActivity(new Intent(this,GuideActivity.class));
        }).show();
    }
    private void allSettings() { if(!ready()) return; showPanel("Все настройки"); operation(() -> { client.request("/key",new JSONObject().put("key","settings")); Thread.sleep(180); JSONObject screen=client.request("/ui",new JSONObject()); runOnUiThread(() -> renderPanel(screen)); return "Настройки устройства"; },message -> { if(panelMessage!=null) panelMessage.setText(message); }); }
    private void updateDeviceKind() {
        boolean television=!getPreferences(0).getBoolean("known_projector",false) && getSharedPreferences("remote_ui",0).getString("device_kind","projector").equals("tv");
        focusSection.setVisibility(television?View.GONE:View.VISIBLE); imageToggle.setVisibility(television?View.GONE:View.VISIBLE);
        imageToggle.setText(imageExpanded?"Скрыть настройки изображения":"Настройки изображения");
        imageToggle.setContentDescription(imageExpanded?"Настройки изображения, развёрнуто":"Настройки изображения, свёрнуто");
        imageSection.setVisibility(!television && imageExpanded?View.VISIBLE:View.GONE); tvSettings.setVisibility(television?View.VISIBLE:View.GONE);
        brand.setText(television?"ТЕЛЕВИЗОР":"ПРОЕКТОР");
    }
    private void power() {
        LinearLayout body=column(); body.setPadding(dp(20),dp(8),dp(20),0);
        boolean infrared=InfraredRemote.available(this) && !getPreferences(0).getString("ir_profile","").isEmpty();
        boolean verifiedIr=infrared && getPreferences(0).getString("ir_profile","").equals(getPreferences(0).getString("ir_confirmed_profile",""));
        body.addView(text("По Wi-Fi можно погасить и вернуть проекцию на совместимом Jixin. После полного выключения Android связь по сети недоступна.",14,MUTED));
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Питание проектора").setView(body).setNegativeButton("Закрыть",null).create();
        if(verifiedIr) {
            body.addView(button("Вкл. / выкл. через ИК","power",LIME,INK,16,() -> { dialog.dismiss(); infrared("power"); }),lp(-1,56,12));
        }
        for(boolean on:new boolean[]{true,false}) {
            RemoteButton button=button(on?"Включить проекцию по Wi-Fi":"Выключить проекцию по Wi-Fi",null,WHITE,INK,15,() -> { dialog.dismiss(); projection(on); });
            button.setEnabled(linked && !busy && getPreferences(0).getBoolean("projection_control",false)); body.addView(button,lp(-1,56,8));
        }
        body.addView(text("Выключение проекции гасит свет, но сохраняет Android и Wi-Fi. Звук выключается отдельно кнопкой «Звук».",12,MUTED),lp(-1,-2,10));
        RemoteButton menu=button("Полное выключение · меню Android",null,WHITE,INK,14,() -> {
            dialog.dismiss(); if(!ready()) return; showPanel("Питание Android");
            operation(() -> { JSONObject result=client.request("/power/control",new JSONObject().put("action","menu")); if(!result.optBoolean("ok")) return "Android не открыл меню питания";
                Thread.sleep(250); JSONObject screen=client.request("/ui",new JSONObject()); runOnUiThread(() -> renderPanel(screen)); return "После полного выключения понадобится кнопка проектора или совместимый ИК-пульт";
            },message -> { if(panelMessage!=null) panelMessage.setText(message); });
        }); menu.setEnabled(linked && !busy); body.addView(menu,lp(-1,56,8));
        if(infrared) {
            body.addView(text(verifiedIr?"Направляй верхний торец телефона на ИК-приёмник проектора. Холодное включение нужно проверить отдельно.":"ИК-включение пока не проверено. В прошивке найдены коды, но они могут не подходить к родному пульту.",12,MUTED),lp(-1,-2,12));
            body.addView(button("Проверить ИК: Домой","home",WHITE,INK,14,() -> { dialog.dismiss();
                new AlertDialog.Builder(this).setTitle("Проверка ИК").setMessage("Направь верхний торец телефона на ИК-приёмник проектора. Открой любое меню проектора, чтобы проверить возврат на главный экран.")
                    .setPositiveButton("Отправить «Домой»",(d,w) -> infrared("home")).setNegativeButton("Отмена",null).show();
            }),lp(-1,52,8));
        }
        ScrollView scroll=new ScrollView(this); scroll.addView(body); dialog.setView(scroll); dialog.show();
    }
    private void infrared(String key) {
        if(infraredBusy || SystemClock.elapsedRealtime()-lastInfrared<1200) return;
        String saved=getPreferences(0).getString("ir_profile",""); infraredBusy=true; lastInfrared=SystemClock.elapsedRealtime();
        infraredWorker.execute(() -> {
            try { InfraredRemote.send(this,new JSONObject(saved),key); runOnUiThread(() -> { infraredBusy=false; if(!isDestroyed()) {
                status.setText("ИК-команда отправлена. Проверь реакцию проектора.");
                if(key.equals("home") && foreground) new AlertDialog.Builder(this).setTitle("Проектор перешёл на главный экран?")
                    .setMessage("Подтверди только если сработала команда телефона. Это откроет ИК-кнопку питания; её работу нужно проверить отдельно.")
                    .setPositiveButton("Да, сработало",(d,w) -> getPreferences(0).edit().putString("ir_confirmed_profile",saved).apply())
                    .setNegativeButton("Нет реакции",(d,w) -> { getPreferences(0).edit().remove("ir_confirmed_profile").apply(); status.setText("ИК-коды не подошли. Управление проекцией по Wi-Fi остаётся доступным."); }).show();
            } }); }
            catch(Exception e) { runOnUiThread(() -> { infraredBusy=false; if(!isDestroyed()) status.setText(e.getMessage()); }); }
        });
    }
    private void projection(boolean on) {
        if(!ready()) return;
        operation(() -> client.request("/power/control",new JSONObject().put("action",on?"projection_on":"projection_off")).optString("message","Проверь проекцию"),status::setText);
    }
    private void quickFocus(LinearLayout row,String label,String action) {
        RemoteButton button=button(label,null,LIME,INK,24,null);
        button.setContentDescription(label.equals("F−")?"Фокус ближе F−":"Фокус дальше F+");
        button.instant(() -> { if(directFocus) projectorAction(action); else { button.cancelPress(); openSetting("Фокус",new String[]{"Фокус","Фокусировка","Focus"}); } },true);
        controls.add(button); row.addView(button,weighted(58,5));
    }
    private void detectProjector(JSONObject info) {
        directFocus=info.optBoolean("directFocus") || info.has("focusAuthority");
        if(directFocus || "projector".equals(info.optString("deviceKind"))) {
            getPreferences(0).edit().putBoolean("known_projector",true).apply();
            getSharedPreferences("remote_ui",0).edit().putString("device_kind","projector").apply();
        }
    }
    private void connectionOptions() {
        if(getPreferences(0).getString("token","").isEmpty()) { pairing(); return; }
        new AlertDialog.Builder(this).setTitle(linked?"Устройство подключено":"Ждём сохранённое устройство")
            .setMessage("Включи проектор и подключи его к той же домашней сети. Пульт найдёт его автоматически, вводить код повторно не нужно.")
            .setPositiveButton("Повторить поиск",(dialog,which) -> { if(!busy) { linked=false; reconnectSaved(); } })
            .setNeutralButton("Другое устройство / новый код",(dialog,which) -> pairing()).setNegativeButton("Закрыть",null).show();
    }
    private SocketFactory wifi() {
        return wifiNetwork().getSocketFactory();
    }
    private Network wifiNetwork() {
        ConnectivityManager cm=(ConnectivityManager)getSystemService(CONNECTIVITY_SERVICE);
        for(Network n:cm.getAllNetworks()) { NetworkCapabilities c=cm.getNetworkCapabilities(n); if(c!=null && c.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) && !c.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) return n; }
        throw new IllegalStateException("Подключи телефон к домашнему Wi-Fi");
    }
    private boolean ready() { if(!linked) { if(getPreferences(0).getString("token","").isEmpty()) pairing(); else reconnectSaved(); return false; } return !busy; }
    private void pairing() {
        if(busy || (pairing!=null && pairing.isShowing())) return;
        LinearLayout fields=column(); fields.setPadding(dp(24),dp(8),dp(24),0);
        fields.addView(text("Введи адрес и код из «Приёмника пульта» на телевизоре или проекторе.",15,MUTED));
        EditText address=input("IP с экрана приёмника",android.text.InputType.TYPE_CLASS_PHONE); address.setText(getPreferences(0).getString("ip","")); fields.addView(address,lp(-1,54,12));
        RemoteButton search=button("Найти устройства","wifi",WHITE,INK,15,() -> findReceivers(address)); fields.addView(search,lp(-1,48,8));
        EditText pin=input("Код из 6 цифр",android.text.InputType.TYPE_CLASS_NUMBER); pin.setFilters(new InputFilter[]{new InputFilter.LengthFilter(6)}); fields.addView(pin,lp(-1,54,8));
        fields.addView(text("Код нужен только при первом подключении. Дальше телефон подключается автоматически. Для знакомого устройства оставь код пустым.",12,MUTED),lp(-1,-2,8));
        pairing=new AlertDialog.Builder(this).setTitle("Подключение по Wi-Fi").setView(fields).setNegativeButton("Закрыть",null).setPositiveButton("Подключить",null).create();
        pairing.setOnShowListener(d -> { pairing.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> { if(!busy) connect(address.getText().toString().trim(),pin.getText().toString().trim()); }); if(address.getText().length()==0) findReceivers(address); }); pairing.show();
    }
    private void findReceivers(EditText address) {
        if(busy) return;
        status.setText("Ищем устройства в домашней сети…");
        String currentAddress=address.getText().toString();
        operation(() -> {
            Network network=wifiNetwork(); ConnectivityManager cm=(ConnectivityManager)getSystemService(CONNECTIVITY_SERVICE);
            return new JSONObject(ReceiverDiscovery.find(network,cm.getLinkProperties(network),"",currentAddress)).toString();
        },raw -> {
            try {
                JSONObject found=new JSONObject(raw); ArrayList<String> ips=new ArrayList<>(),names=new ArrayList<>();
                Iterator<String> keys=found.keys(); while(keys.hasNext()) { String ip=keys.next(); ips.add(ip); names.add(found.getString(ip)+" · "+ip); }
                if(pairing==null || !pairing.isShowing()) return;
                if(ips.size()==1) { address.setText(ips.get(0)); status.setText("Устройство найдено. Введи код с его экрана."); }
                else if(ips.isEmpty()) status.setText("Устройство не найдено. Открой приёмник или введи IP вручную.");
                else new AlertDialog.Builder(this).setTitle("Выбери устройство").setItems(names.toArray(new String[0]),(d,index) -> address.setText(ips.get(index))).show();
            } catch(JSONException e) { failure(e); }
        });
    }
    private void connect(String ip,String code) {
        connect(ip,code,false);
    }
    private void reconnectSaved() {
        if(!foreground || busy || linked || getPreferences(0).getString("token","").isEmpty()) return;
        connect(getPreferences(0).getString("ip",""),"",true);
    }
    private void scheduleReconnect() {
        timers.removeCallbacks(reconnect);
        if(!foreground || getPreferences(0).getString("token","").isEmpty()) return;
        long delay=Math.min(15000,1000L << Math.min(reconnectAttempt++,4));
        timers.postDelayed(reconnect,delay);
    }
    private void connect(String ip,String code,boolean automatic) {
        automaticConnection=automatic; timers.removeCallbacks(reconnect);
        try {
            ReceiverClient next=new ReceiverClient(ip,wifi()); if(code.isEmpty()) next.token=getPreferences(0).getString("token","");
            if(next.token.isEmpty() && !code.matches("[0-9]{6}")) { Toast.makeText(this,"Введи код из 6 цифр с экрана приёмника",Toast.LENGTH_LONG).show(); return; }
            generation++; pending=0; probing=false; worker.getQueue().clear(); client=next; linked=false;
            operation(() -> {
                ReceiverClient destination=next;
                if(!next.token.isEmpty()) {
                    Network network=wifiNetwork(); ConnectivityManager cm=(ConnectivityManager)getSystemService(CONNECTIVITY_SERVICE);
                    try {
                        Map<String,String> found=ReceiverDiscovery.find(network,cm.getLinkProperties(network),next.token,ip);
                        if(!found.isEmpty()) { destination=new ReceiverClient(found.keySet().iterator().next(),network.getSocketFactory()); destination.token=next.token; }
                    } catch(java.io.IOException ignored) { /* Older receivers still work at their saved address. */ }
                }
                if(next.token.isEmpty()) {
                    next.token=next.request("/pair",new JSONObject().put("pin",code)).getString("token");
                    // The PIN is consumed now. Persist before any optional network request.
                    if(!getPreferences(0).edit().putString("ip",ip).putString("token",next.token).remove("known_projector").remove("ir_profile").remove("ir_confirmed_profile").remove("projection_control").commit())
                        throw new java.io.IOException("Не удалось сохранить подключение на телефоне");
                }
                receiverStatus=destination.request("/status",new JSONObject());
                if(!receiverStatus.optBoolean("accessibility",true)) throw new java.io.IOException("Служба управления запускается");
                JSONObject deviceInfo=receiverStatus;
                if(!deviceInfo.has("directFocus")) {
                    try { deviceInfo=destination.request("/projector/state",new JSONObject()); }
                    catch(ReceiverClient.RemoteException ignored) { /* Generic TV / older receiver. */ }
                }
                detectProjector(deviceInfo);
                if(receiverStatus.optJSONObject("update")!=null && receiverStatus.getJSONObject("update").optLong("versionCode")>=12) {
                    try { JSONObject powerState=destination.request("/power/state",new JSONObject()); if(powerState.optBoolean("projectionControl")) getPreferences(0).edit().putBoolean("projection_control",true).apply(); } catch(ReceiverClient.RemoteException ignored) {}
                    if(InfraredRemote.available(this)) {
                        try { JSONObject profile=InfraredRemote.profile(destination.request("/diagnostics/power",new JSONObject())); if(profile!=null) getPreferences(0).edit().putString("ir_profile",profile.toString()).apply(); } catch(ReceiverClient.RemoteException ignored) {}
                    }
                }
                if(!getPreferences(0).edit().putString("ip",destination.ip).putString("token",destination.token).commit()) throw new java.io.IOException("Не удалось сохранить подключение на телефоне");
                client=destination; if(bundledUpdate==null) bundledUpdate=new ReceiverUpdate(this);
                return "Подключено · "+receiverStatus.optString("model","устройство");
            },message -> { linked=true; automaticConnection=false; reconnectAttempt=0; if(pairing!=null) pairing.dismiss(); updateDeviceKind(); status.setText(message); enabled(); maybeUpdate(); });
        } catch(Exception e) { failure(e); }
    }
    private void maybeUpdate() {
        JSONObject update=receiverStatus==null?null:receiverStatus.optJSONObject("update");
        if(update!=null && bundledUpdate!=null && update.optLong("versionCode")<bundledUpdate.versionCode && update.optBoolean("canInstall")
            && attemptedUpdate!=bundledUpdate.versionCode && getSharedPreferences("remote_ui",0).getBoolean("auto_receiver_update",true)) installUpdate();
    }
    private void updates() {
        if(!ready()) return;
        operation(() -> { receiverStatus=client.request("/status",new JSONObject()); if(bundledUpdate==null) bundledUpdate=new ReceiverUpdate(this); return ""; },ignored -> {
            JSONObject update=receiverStatus.optJSONObject("update");
            LinearLayout content=column(); content.setPadding(dp(24),dp(8),dp(24),0);
            String current=receiverStatus.optString("receiverVersion","первая версия");
            String message="На устройстве: "+current+"\nВ пульте: "+bundledUpdate.versionName+"\n\n";
            if(update==null) message+="Один раз обнови приёмник через браузер. Следующие версии будут поступать прямо из пульта по Wi-Fi.";
            else if(!update.optBoolean("canInstall")) message+="Один раз разреши приёмнику устанавливать обновления в настройках телевизора или проектора.";
            else if(update.optLong("versionCode")>=bundledUpdate.versionCode) message+="Установлена последняя версия, которая есть в этом пульте.";
            else message+="Новая версия готова. Во время обновления связь на несколько секунд прервётся и восстановится сама.";
            if(update!=null && ("confirmation".equals(update.optString("state")) || "failed".equals(update.optString("state")))) message+="\n\n"+update.optString("message");
            content.addView(text(message,16,INK));
            Switch auto=new Switch(this); auto.setText("Обновлять автоматически"); auto.setTextSize(15); auto.setChecked(getSharedPreferences("remote_ui",0).getBoolean("auto_receiver_update",true)); content.addView(auto,lp(-1,56,12));
            content.addView(text("Когда в обновлённом пульте появится новая версия приёмника, она будет передана при подключении. Android может запросить подтверждение.",13,MUTED));
            auto.setOnCheckedChangeListener((v,on) -> getSharedPreferences("remote_ui",0).edit().putBoolean("auto_receiver_update",on).apply());
            AlertDialog.Builder dialog=new AlertDialog.Builder(this).setTitle("Обновления по Wi-Fi").setView(content).setNegativeButton("Закрыть",(d,w) -> maybeUpdate());
            if(update==null) dialog.setPositiveButton("Первое обновление",(d,w) -> startActivity(new Intent(this,InstallReceiverActivity.class)));
            else if(!update.optBoolean("canInstall")) dialog.setPositiveButton("Разрешить на проекторе",(d,w) -> operation(() -> client.request("/update/allow",new JSONObject()).optString("message"),value -> { status.setText(value); new AlertDialog.Builder(this).setMessage(value).setPositiveButton("Проверить",(dd,ww) -> updates()).setNegativeButton("Позже",null).show(); }));
            else if(update.optLong("versionCode")<bundledUpdate.versionCode) dialog.setPositiveButton("Обновить по Wi-Fi",(d,w) -> installUpdate());
            else dialog.setPositiveButton("Проверить",(d,w) -> updates());
            dialog.show();
        });
    }
    private void installUpdate() {
        if(!linked || busy || bundledUpdate==null) return;
        attemptedUpdate=bundledUpdate.versionCode;
        ReceiverClient destination=client;
        status.setText("Передаём обновление по Wi-Fi…");
        operation(() -> destination.update(bundledUpdate.apk).toString(),raw -> {
            try {
                JSONObject response=new JSONObject(raw); status.setText(response.optString("message"));
                if("installing".equals(response.optString("state"))) { busy=true; enabled(); awaitUpdate(destination,generation,0); }
            } catch(JSONException e) { failure(e); }
        });
    }
    private void awaitUpdate(ReceiverClient destination,int current,int attempt) {
        timers.postDelayed(() -> {
            if(isDestroyed() || current!=generation) return;
            worker.execute(() -> {
                JSONObject next=null; try { next=destination.request("/status",new JSONObject()); } catch(Exception ignored) {}
                final JSONObject result=next;
                runOnUiThread(() -> {
                    if(isDestroyed() || current!=generation) return;
                    JSONObject update=result==null?null:result.optJSONObject("update");
                    if(update!=null && update.optLong("versionCode")>=bundledUpdate.versionCode) { receiverStatus=result; busy=false; linked=true; status.setText("Приёмник обновлён · "+update.optString("versionName")); enabled(); return; }
                    if(update!=null && ("confirmation".equals(update.optString("state")) || "failed".equals(update.optString("state")))) { busy=false; status.setText(update.optString("message")); enabled(); return; }
                    if(attempt>=40) { busy=false; linked=result!=null; status.setText("Обновление пока не подтверждено. Открой «Ещё → Обновления по Wi-Fi»."); enabled(); return; }
                    awaitUpdate(destination,current,attempt+1);
                });
            });
        },1000);
    }
    private interface Work { String run() throws Exception; }
    private interface Done { void accept(String result); }
    private void operation(Work work,Done done) {
        if(busy) return; busy=true; enabled(); int current=generation;
        if(panelMessage!=null && panel!=null && panel.isShowing()) panelMessage.setText("Открываем…");
        worker.execute(() -> { try { String result=work.run(); runOnUiThread(() -> { if(current!=generation || isDestroyed()) return; busy=false; done.accept(result); enabled(); }); }
            catch(Exception e) { runOnUiThread(() -> { if(current!=generation || isDestroyed()) return; busy=false; failure(e); }); } });
    }
    private void key(String key) {
        if(!linked || busy) return;
        if(!adjustment.isEmpty() && Arrays.asList("left","right","up","down","ok").contains(key)) { adjustmentKey(key); return; }
        if(pending>=8) { status.setText("Связь замедлилась. Дождись ответа устройства."); return; }
        int current=generation; ReceiverClient destination=client; long queued=SystemClock.elapsedRealtime(); pending++; timers.removeCallbacks(refreshPanel);
        worker.execute(() -> {
            if(current!=generation) return;
            if(SystemClock.elapsedRealtime()-queued>500) { runOnUiThread(() -> { if(current==generation) { pending=Math.max(0,pending-1); status.setText("Медленный ответ: повтори нажатие"); } }); return; }
            try {
                long started=SystemClock.elapsedRealtime(); JSONObject response=destination.request("/key",new JSONObject().put("key",key)); long duration=SystemClock.elapsedRealtime()-started;
                runOnUiThread(() -> { if(current!=generation || isDestroyed()) return; pending=Math.max(0,pending-1); status.setText(response.optString("message","Команда передана")); latency.setText(duration+" мс"); timers.removeCallbacks(refreshPanel); timers.postDelayed(refreshPanel,180); });
            } catch(Exception e) { runOnUiThread(() -> { if(current!=generation || isDestroyed()) return; pending=Math.max(0,pending-1); failure(e); }); }
        });
    }
    private void failure(Exception e) {
        if((getApplicationInfo().flags&android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE)!=0) android.util.Log.w("HomeRemote",e.getClass().getSimpleName()+": "+e.getMessage());
        boolean automatic=automaticConnection; automaticConnection=false;
        if(e instanceof java.io.IOException || e instanceof SecurityException || !linked) { linked=false; generation++; pending=0; busy=false; worker.getQueue().clear(); }
        // A stale IP can now belong to another receiver. A rejection there must not erase our pairing.
        boolean remembered=!getPreferences(0).getString("token","").isEmpty();
        String message=e instanceof ReceiverClient.AuthenticationException?e.getMessage():e instanceof java.io.IOException?(remembered?"Ждём устройство. Подключение восстановится автоматически.":"Нет ответа от устройства. Проверь сеть и включённый приёмник."):e.getMessage();
        status.setText(message); if(panelMessage!=null) panelMessage.setText(message); if(!automatic) Toast.makeText(this,message,Toast.LENGTH_LONG).show(); enabled();
        if(!linked && remembered) scheduleReconnect();
    }
    private void openSetting(String title,String[] labels) {
        if(!ready()) return; showPanel(title);
        operation(() -> {
            try {
                JSONObject values=client.request("/projector/state",new JSONObject());
                boolean focus=title.equals("Фокус") && (values.optBoolean("directFocus") || values.has("focusAuthority"));
                if(values.optBoolean("ok") && (focus || (!title.equals("Фокус") && values.has("scale")))) { if(focus) directFocus=true; runOnUiThread(() -> renderAdjustment(title,values)); return title.equals("Фокус")?"Нажимай F− / F+. Удержание меняет фокус плавно.":"Настройки читаются прямо из проектора"; }
            } catch(ReceiverClient.RemoteException ignored) { }
            if(title.equals("Фокус")) {
                try { JSONObject focus=client.request("/open_focus",new JSONObject()); if(focus.optBoolean("ok")) { Thread.sleep(120); JSONObject screen=client.request("/ui",new JSONObject()); runOnUiThread(() -> renderPanel(screen)); return "Фокусировка линзы"; } } catch(ReceiverClient.RemoteException ignored) { }
            }
            client.request("/key",new JSONObject().put("key","settings")); JSONObject screen=null,node=null;
            for(int i=0;i<4;i++) { screen=client.request("/ui",new JSONObject()); node=find(screen,labels); if(node!=null) break; Thread.sleep(80); }
            for(int i=0;node==null && i<4;i++) { client.request("/key",new JSONObject().put("key",i==0?"scroll_up":"scroll_down")); screen=client.request("/ui",new JSONObject()); node=find(screen,labels); }
            String note;
            if(node!=null) { JSONObject result=client.request("/tap",target(node)); note=result.optBoolean("ok")?"Настройка на проекторе":result.optString("message","Не удалось открыть пункт"); Thread.sleep(120); screen=client.request("/ui",new JSONObject()); }
            else note="Пункт «"+title+"» пока не найден в доступном меню. Можно открыть его на проекторе и нажать «Обновить».";
            final JSONObject result=screen; runOnUiThread(() -> renderPanel(result)); return note;
        },message -> { if(panelMessage!=null) panelMessage.setText(message); });
    }
    private JSONObject find(JSONObject screen,String[] names) {
        JSONArray nodes=screen.optJSONArray("items"); if(nodes==null) return null;
        for(String name:names) for(int i=0;i<nodes.length();i++) { JSONObject node=nodes.optJSONObject(i); if(node!=null && name.equalsIgnoreCase(node.optString("label"))) return node; }
        return null;
    }
    private JSONObject target(JSONObject node) throws JSONException { return new JSONObject().put("label",node.getString("label")).put("x",node.getInt("x")).put("y",node.getInt("y")); }
    private void showPanel(String title) {
        if(panel!=null) panel.dismiss(); panelControls.clear();
        adjustment=""; adjustmentValue=null;
        panel=new Dialog(this); LinearLayout body=column(); body.setPadding(dp(20),dp(20),dp(20),dp(16)); body.setBackground(shape(BG,28));
        LinearLayout heading=row(); heading.setGravity(Gravity.CENTER_VERTICAL); TextView label=text(title,24,INK); label.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL)); heading.addView(label,new LinearLayout.LayoutParams(0,-2,1));
        RemoteButton close=button("×",null,BG,MUTED,28,() -> panel.dismiss()); close.setContentDescription("Закрыть настройки"); heading.addView(close,new LinearLayout.LayoutParams(dp(48),dp(48))); body.addView(heading);
        panelMessage=text("Читаем меню устройства…",14,MUTED); body.addView(panelMessage,lp(-1,-2,6));
        ScrollView scroll=new ScrollView(this); panelItems=column(); scroll.addView(panelItems); LinearLayout.LayoutParams listLayout=new LinearLayout.LayoutParams(-1,0,1); listLayout.topMargin=dp(12); body.addView(scroll,listLayout);
        LinearLayout arrows=row(); body.addView(arrows,lp(-1,48,12));
        for(String direction:new String[]{"left","up","ok","down","right"}) { RemoteButton b=button(direction.equals("ok")?"OK":"",direction.equals("ok")?null:direction,direction.equals("ok")?LIME:WHITE,INK,16,null); b.setContentDescription(direction.equals("left")?"Влево":direction.equals("right")?"Вправо":direction.equals("up")?"Вверх":direction.equals("down")?"Вниз":"OK"); b.instant(() -> key(direction),!direction.equals("ok")); panelControls.add(b); arrows.addView(b,weighted(48,4)); }
        LinearLayout actions=row(); body.addView(actions,lp(-1,48,8));
        RemoteButton back=button("Назад","back",WHITE,INK,14,() -> { if(!adjustment.isEmpty()) panel.dismiss(); else key("back"); }); panelControls.add(back); actions.addView(back,weighted(48,4));
        RemoteButton refresh=button("Обновить",null,WHITE,INK,14,() -> readPanel()); panelControls.add(refresh); actions.addView(refresh,weighted(48,4));
        nextPage=button("Дальше",null,WHITE,INK,14,() -> key("scroll_down")); panelControls.add(nextPage); actions.addView(nextPage,weighted(48,4));
        panel.setContentView(body); Window window=panel.getWindow(); if(window!=null) { window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT)); window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND); WindowManager.LayoutParams params=window.getAttributes(); params.gravity=Gravity.BOTTOM; params.dimAmount=.32f; window.setAttributes(params); }
        panel.setOnDismissListener(d -> { timers.removeCallbacks(refreshPanel); for(RemoteButton b:panelControls) b.cancelPress(); panelControls.clear(); panelSliders.clear(); panelItems=null; panelMessage=null; adjustment=""; adjustmentValue=null; });
        panel.show(); if(window!=null) window.setLayout(-1,(int)(getResources().getDisplayMetrics().heightPixels*.80)); enabled();
    }
    private void renderAdjustment(String title,JSONObject values) {
        if(isDestroyed() || panelItems==null || panel==null || !panel.isShowing()) return;
        adjustment=title; projectorState=values; panelItems.removeAllViews(); nextPage.setVisibility(View.GONE);
        adjustmentValue=text("",26,INK); adjustmentValue.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL)); panelItems.addView(adjustmentValue,lp(-1,-2,8));
        if(title.equals("Фокус")) {
            panelItems.addView(text("Резкость меняется движением линзы. Смотри на мелкие буквы в проекции и остановись, когда они станут чёткими.",16,MUTED),lp(-1,-2,16));
            LinearLayout actions=row(); panelItems.addView(actions,lp(-1,100,22)); adjustmentButton(actions,"F−","focus_minus",100); adjustmentButton(actions,"F+","focus_plus",100);
            panelItems.addView(text("Короткое нажатие — точный шаг. Удержание — несколько шагов.",14,MUTED),lp(-1,-2,18));
        } else if(title.equals("Масштаб")) {
            panelItems.addView(text("Уменьшить или увеличить изображение без перемещения проектора. Процент показывает площадь кадра с учётом коррекции углов.",16,MUTED),lp(-1,-2,16));
            LinearLayout actions=row(); panelItems.addView(actions,lp(-1,90,22)); adjustmentButton(actions,"− Меньше","scale_down",90); adjustmentButton(actions,"Больше +","scale_up",90);
        } else if(title.equals("Трапеция")) {
            for(String[] axis:new String[][]{{"Вертикаль","vertical"},{"Горизонталь","horizontal"},{"Поворот кадра","rotate"}}) {
                panelItems.addView(text(axis[0],16,MUTED),lp(-1,-2,18)); LinearLayout actions=row(); panelItems.addView(actions,lp(-1,60,6));
                adjustmentButton(actions,"− 1°","angle_"+axis[1]+"_minus",60); adjustmentButton(actions,"+ 1°","angle_"+axis[1]+"_plus",60);
            }
        } else if(title.equals("Выравнивание")) {
            panelItems.addView(text("Выбери угол, затем перемещай его стрелками внизу.",16,MUTED),lp(-1,-2,16));
            String[] ids={"lt","rt","lb","rb"}, labels={"Верхний\nлевый","Верхний\nправый","Нижний\nлевый","Нижний\nправый"};
            for(int i=0;i<2;i++) { LinearLayout choices=row(); panelItems.addView(choices,lp(-1,72,12)); for(int j=0;j<2;j++) { final String chosen=ids[i*2+j]; RemoteButton b=button(labels[i*2+j],null,WHITE,INK,16,() -> { selectedCorner=chosen; updateAdjustment(projectorState); }); b.setTag("item"); panelControls.add(b); choices.addView(b,weighted(72,4)); } }
        } else if(title.equals("Положение изображения")) {
            String[] actions={"orthographicForward","hoistingForward","orthographicRear","hoistingRear"};
            String[] labels={"Спереди · на столе","Спереди · на потолке","Сзади экрана · на столе","Сзади экрана · на потолке"};
            for(int i=0;i<actions.length;i++) { final String action=actions[i]; RemoteButton b=button(labels[i],"rotate",WHITE,INK,16,() -> projectorAction(action)); b.setTag("item"); panelControls.add(b); panelItems.addView(b,lp(-1,64,12)); }
        }
        updateAdjustment(values); enabled();
    }
    private void adjustmentButton(LinearLayout row,String label,String action,int height) {
        RemoteButton b=button(label,null,LIME,INK,23,null); b.instant(() -> projectorAction(action),true); b.setTag("item"); panelControls.add(b); row.addView(b,weighted(height,4));
    }
    private String cornerLabel() { return selectedCorner.equals("lt")?"Верхний левый":selectedCorner.equals("rt")?"Верхний правый":selectedCorner.equals("lb")?"Нижний левый":"Нижний правый"; }
    private void updateAdjustment(JSONObject values) {
        if(values==null || adjustmentValue==null) return; projectorState=values;
        if(adjustment.equals("Масштаб")) adjustmentValue.setText(String.format(Locale.getDefault(),"Площадь кадра\n%.1f %%",values.optDouble("scale",100)));
        else if(adjustment.equals("Фокус")) adjustmentValue.setText("Фокусировка линзы");
        else if(adjustment.equals("Трапеция")) adjustmentValue.setText("Вертикаль  "+values.optInt("angleByVertical")+"°\nГоризонталь  "+values.optInt("angleByHorizontal")+"°\nПоворот  "+values.optInt("angleByRotate")+"°");
        else if(adjustment.equals("Выравнивание")) {
            adjustmentValue.setText(cornerLabel());
            for(RemoteButton button:panelControls) if("item".equals(button.getTag())) {
                boolean selected=button.getText().toString().replace('\n',' ').equals(cornerLabel());
                button.setSelected(selected); button.setBackground(shape(selected?LIME:WHITE,18));
            }
        }
        else if(adjustment.equals("Положение изображения")) {
            String mode=values.optString("projection"); adjustmentValue.setText(mode.equals("orthographicForward")?"Спереди · стол":mode.equals("hoistingForward")?"Спереди · потолок":mode.equals("orthographicRear")?"Сзади · стол":mode.equals("hoistingRear")?"Сзади · потолок":"Выбери положение");
        }
    }
    private void adjustmentKey(String key) {
        if(key.equals("ok")) {
            if(adjustment.equals("Выравнивание")) { String[] corners={"lt","rt","rb","lb"}; selectedCorner=corners[(Arrays.asList(corners).indexOf(selectedCorner)+1)%4]; updateAdjustment(projectorState); }
            return;
        }
        boolean plus=key.equals("right") || key.equals("up");
        if(adjustment.equals("Фокус")) projectorAction(plus?"focus_plus":"focus_minus");
        else if(adjustment.equals("Масштаб")) projectorAction(plus?"scale_up":"scale_down");
        else if(adjustment.equals("Трапеция")) projectorAction("angle_"+(key.equals("up")||key.equals("down")?"vertical":"horizontal")+"_"+(plus?"plus":"minus"));
        else if(adjustment.equals("Выравнивание")) projectorAction("corner_"+selectedCorner+"_"+key);
    }
    private void projectorAction(String action) {
        if(!linked || busy || pending>0) return;
        pending++; int current=generation; ReceiverClient destination=client; long started=SystemClock.elapsedRealtime();
        worker.execute(() -> {
            if(current!=generation) return;
            try {
                JSONObject answer=destination.request("/projector/control",new JSONObject().put("action",action));
                long duration=SystemClock.elapsedRealtime()-started;
                runOnUiThread(() -> { if(current!=generation || isDestroyed()) return; pending=Math.max(0,pending-1); String message=answer.optString("message","Команда передана"); status.setText(message); latency.setText(duration+" мс"); if(panelMessage!=null) panelMessage.setText(message); if(answer.has("after")) updateAdjustment(answer.optJSONObject("after")); });
            } catch(Exception e) { runOnUiThread(() -> { if(current!=generation || isDestroyed()) return; pending=Math.max(0,pending-1); failure(e); }); }
        });
    }
    private void readPanel() {
        if(!linked || busy || panel==null || !panel.isShowing()) return;
        if(!adjustment.isEmpty()) { operation(() -> { JSONObject state=client.request("/projector/state",new JSONObject()); runOnUiThread(() -> updateAdjustment(state)); return "Значения обновлены"; },message -> { if(panelMessage!=null) panelMessage.setText(message); }); return; }
        operation(() -> { JSONObject screen=client.request("/ui",new JSONObject()); runOnUiThread(() -> renderPanel(screen)); return screen.optBoolean("ok")?"Нажимай пункты меню прямо здесь":screen.optString("error","Экран недоступен"); },message -> { if(panelMessage!=null) panelMessage.setText(message); });
    }
    private void renderPanel(JSONObject screen) {
        if(isDestroyed() || panelItems==null || panel==null || !panel.isShowing() || screen==null) return;
        for(int i=panelControls.size()-1;i>=0;i--) if(panelControls.get(i).getTag()!=null) panelControls.remove(i);
        panelItems.removeAllViews(); panelSliders.clear(); JSONArray nodes=screen.optJSONArray("items"); if(nodes==null || nodes.length()==0) { panelItems.addView(text("Этот экран не передаёт названия элементов. Попробуй стрелки и OK.",16,MUTED)); return; }
        for(int i=0;i<nodes.length();i++) {
            JSONObject node=nodes.optJSONObject(i); if(node==null) continue;
            String caption=node.optString("label"); if(node.has("checked")) caption+=node.optBoolean("checked")?"  ·  Вкл":"  ·  Выкл";
            if(node.has("state")) caption+="  ·  "+node.optString("state"); if(node.optBoolean("selected") || node.optBoolean("focused")) caption+="  •";
            if(node.has("value") && node.optDouble("max")>node.optDouble("min")) { rangeRow(node,caption); continue; }
            RemoteButton b=button(caption,"right",WHITE,INK,16,() -> tap(node)); b.setGravity(Gravity.CENTER_VERTICAL|Gravity.START); b.setPadding(dp(16),dp(12),dp(16),dp(12)); b.setMinHeight(dp(52)); b.setTag("item"); panelControls.add(b); panelItems.addView(b,lp(-1,-2,8));
        }
        enabled();
    }
    private void tap(JSONObject node) {
        if(!linked || busy) return;
        operation(() -> { JSONObject response=client.request("/tap",target(node)); if(response.optBoolean("ok")) { Thread.sleep(100); JSONObject screen=client.request("/ui",new JSONObject()); runOnUiThread(() -> renderPanel(screen)); } return response.optString("message","Команда передана"); },message -> { if(panelMessage!=null) panelMessage.setText(message); });
    }
    private void rangeRow(JSONObject node,String caption) {
        LinearLayout card=column(); card.setPadding(dp(16),dp(12),dp(16),dp(12)); card.setBackground(shape(WHITE,18)); panelItems.addView(card,lp(-1,-2,8));
        double min=node.optDouble("min"),max=node.optDouble("max"),value=node.optDouble("value"),step=node.optDouble("step",1);
        TextView label=text(caption+"  ·  "+number(value),16,INK); card.addView(label);
        SeekBar slider=new SeekBar(this); panelSliders.add(slider); slider.setMax(1000); slider.setProgress((int)Math.round(1000*(value-min)/(max-min))); slider.setContentDescription(caption); card.addView(slider,lp(-1,48,4));
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onStartTrackingTouch(SeekBar s) { timers.removeCallbacks(refreshPanel); }
            public void onProgressChanged(SeekBar s,int progress,boolean user) { if(user) label.setText(caption+"  ·  "+number(min+(max-min)*progress/1000)); }
            public void onStopTrackingTouch(SeekBar s) { double raw=min+(max-min)*s.getProgress()/1000; double rounded=min+Math.round((raw-min)/step)*step; setRange(node,Math.max(min,Math.min(max,rounded))); }
        });
        LinearLayout row=row(); card.addView(row); RemoteButton minus=button("−",null,BG,INK,24,() -> setRange(node,Math.max(min,value-step))); RemoteButton plus=button("+",null,BG,INK,24,() -> setRange(node,Math.min(max,value+step))); minus.setContentDescription("Уменьшить "+caption); plus.setContentDescription("Увеличить "+caption);
        minus.setTag("item"); plus.setTag("item"); panelControls.add(minus); panelControls.add(plus); row.addView(minus,weighted(48,4)); row.addView(plus,weighted(48,4));
    }
    private void setRange(JSONObject node,double value) {
        if(!linked || busy) return;
        operation(() -> { JSONObject response=client.request("/range",target(node).put("value",value)); JSONObject screen=client.request("/ui",new JSONObject()); runOnUiThread(() -> renderPanel(screen)); return response.optString("message","Значение передано"); },message -> { if(panelMessage!=null) panelMessage.setText(message); });
    }
    private String number(double value) { return Math.abs(value-Math.rint(value))<.001?String.valueOf((long)Math.rint(value)):String.format(Locale.getDefault(),"%.2f",value); }
    private void enabled() { for(RemoteButton b:controls) { b.setEnabled(linked&&!busy); b.setAlpha(linked&&!busy?1:.45f); } for(RemoteButton b:panelControls) b.setEnabled(linked&&!busy); for(SeekBar slider:panelSliders) slider.setEnabled(linked&&!busy); connection.setEnabled(!busy); connection.setText(busy&&!linked?"Подключаем…":linked?"В сети":"Подключить"); }
    private void tile(LinearLayout row,String title,String subtitle,String icon,boolean accent,Runnable action) {
        RemoteButton b=button(title+"\n"+subtitle,icon,accent?LIME:WHITE,INK,17,action); b.setGravity(Gravity.CENTER_VERTICAL|Gravity.START); b.setPadding(dp(14),dp(12),dp(10),dp(12)); b.setCompoundDrawablePadding(dp(10));
        SpannableString label=new SpannableString(title+"\n"+subtitle); label.setSpan(new StyleSpan(Typeface.BOLD),0,title.length(),0); label.setSpan(new RelativeSizeSpan(.70f),title.length()+1,label.length(),0); label.setSpan(new ForegroundColorSpan(MUTED),title.length()+1,label.length(),0); b.setText(label);
        controls.add(b); b.setMinHeight(dp(90));
        row.addView(b,row.getOrientation()==LinearLayout.VERTICAL?lp(-1,-2,8):weighted(90,5));
    }
    private void padKey(FrameLayout parent,String label,String icon,int x,int y,int width,int height) { RemoteButton b=button("",icon,0xff263b30,WHITE,20,null); b.setContentDescription(label); b.instant(() -> key(icon),true); controls.add(b); frame(parent,b,x,y,width,height); }
    private void frame(FrameLayout parent,RemoteButton b,int x,int y,int width,int height) { float scale=parent.getLayoutParams().width/(float)dp(252); FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(Math.round(dp(width)*scale),Math.round(dp(height)*scale)); p.leftMargin=Math.round(dp(x)*scale); p.topMargin=Math.round(dp(y)*scale); parent.addView(b,p); }
    private void navKey(LinearLayout row,String label,String key) { String icon=getResources().getConfiguration().screenWidthDp<340 || getResources().getConfiguration().fontScale>1.2f?null:key.equals("menu")?"settings":key; RemoteButton b=button(label,icon,WHITE,INK,14,null); b.instant(() -> key(key),false); controls.add(b); row.addView(b,weighted(52,4)); }
    private void soundKey(LinearLayout row,String label,String icon,String key) { RemoteButton b=button(label,null,WHITE,INK,15,null); b.instant(() -> key(key),true); controls.add(b); row.addView(b,weighted(52,4)); }
    private RemoteButton button(String label,String icon,int background,int color,int size,Runnable action) {
        RemoteButton b=new RemoteButton(this); b.setText(label); b.setTextSize(size); b.setTextColor(color); b.setPadding(dp(10),0,dp(10),0); b.setGravity(Gravity.CENTER);
        b.setElevation(0); b.setStateListAnimator(null);
        b.setBackground(new RippleDrawable(ColorStateList.valueOf(background==DARK?0x33ffffff:0x25192b24),shape(background,17),shape(Color.WHITE,17)));
        if(icon!=null) { RemoteIcon drawable=new RemoteIcon(icon,color,dp(label.isEmpty()?28:23)); if(label.isEmpty()) b.centerIcon(drawable); else { b.setCompoundDrawablesRelativeWithIntrinsicBounds(drawable,null,null,null); b.setCompoundDrawablePadding(dp(6)); } }
        if(action!=null) b.setOnClickListener(v -> action.run()); return b;
    }
    private TextView text(String value,int size,int color) { TextView t=new TextView(this); t.setText(value); t.setTextSize(size); t.setTextColor(color); return t; }
    private EditText input(String hint,int type) { EditText e=new EditText(this); e.setHint(hint); e.setTextColor(INK); e.setTextSize(17); e.setSingleLine(); e.setSelectAllOnFocus(true); e.setInputType(type); return e; }
    private GradientDrawable shape(int color,int radius) { GradientDrawable d=new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(radius)); if(color==WHITE) d.setStroke(dp(1),LINE); return d; }
    private LinearLayout column() { LinearLayout v=new LinearLayout(this); v.setOrientation(LinearLayout.VERTICAL); return v; }
    private LinearLayout row() { LinearLayout v=new LinearLayout(this); v.setOrientation(LinearLayout.HORIZONTAL); v.setBaselineAligned(false); return v; }
    private int dp(float value) { return Math.round(value*getResources().getDisplayMetrics().density); }
    private LinearLayout.LayoutParams lp(int w,int h,int top) { LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(w<0?w:dp(w),h<0?h:dp(h)); p.topMargin=dp(top); return p; }
    private LinearLayout.LayoutParams weighted(int height,int margin) { LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(height),1); p.setMargins(dp(margin),0,dp(margin),0); return p; }
    @Override protected void onResume() {
        super.onResume(); foreground=true; updateDeviceKind(); reconnectAttempt=0;
        ConnectivityManager cm=(ConnectivityManager)getSystemService(CONNECTIVITY_SERVICE);
        networkCallback=new ConnectivityManager.NetworkCallback() {
            @Override public void onAvailable(Network network) { runOnUiThread(() -> { if(foreground && !linked && !busy) { timers.removeCallbacks(reconnect); reconnectSaved(); } }); }
            @Override public void onLost(Network network) { runOnUiThread(() -> { if(foreground && !busy) { linked=false; enabled(); scheduleReconnect(); } }); }
        };
        try { cm.registerNetworkCallback(new NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI).addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN).build(),networkCallback); }
        catch(RuntimeException ignored) { networkCallback=null; }
        reconnectSaved();
        timers.removeCallbacks(heartbeat); timers.postDelayed(heartbeat,5000);
    }
    @Override protected void onPause() {
        foreground=false;
        if(networkCallback!=null) { try { ((ConnectivityManager)getSystemService(CONNECTIVITY_SERVICE)).unregisterNetworkCallback(networkCallback); } catch(RuntimeException ignored) {} networkCallback=null; }
        generation++; pending=0; busy=false; probing=false; linked=false; worker.getQueue().clear(); timers.removeCallbacksAndMessages(null);
        for(RemoteButton b:controls) b.cancelPress(); for(RemoteButton b:panelControls) b.cancelPress(); super.onPause();
    }
    @Override protected void onDestroy() { generation++; worker.shutdownNow(); infraredWorker.shutdownNow(); if(panel!=null) panel.dismiss(); if(pairing!=null) pairing.dismiss(); super.onDestroy(); }
}
