package local.akenori.receiver;

import android.app.*;
import android.content.*;
import android.net.*;
import android.os.*;
import android.provider.Settings;
import org.json.JSONObject;
import java.net.Inet4Address;

/** Owns the LAN connection independently of the receiver screen and accessibility binding. */
public class ReceiverService extends Service {
    static volatile ReceiverService active;
    volatile LocalServer server;
    private DiscoveryServer discovery;
    volatile String failure="";
    private final Handler timer=new Handler(Looper.getMainLooper());
    private final Runnable maintain=new Runnable() { public void run() {
        if(!shouldRun(ReceiverService.this)) { stopSelf(); return; }
        ensureServers(); timer.postDelayed(this,5000);
    }};

    static boolean accessibilityEnabled(Context context) {
        String enabled=Settings.Secure.getString(context.getContentResolver(),Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if(enabled==null) return false;
        ComponentName component=new ComponentName(context,ControlService.class);
        for(String name:enabled.split(":")) if(component.equals(ComponentName.unflattenFromString(name))) return true;
        return false;
    }
    static boolean shouldRun(Context context) {
        return context.getSharedPreferences("receiver",0).getBoolean("enabled",true) && accessibilityEnabled(context);
    }
    static void start(Context context) {
        if(!shouldRun(context)) return;
        try {
            Intent intent=new Intent(context,ReceiverService.class);
            if(Build.VERSION.SDK_INT>=26) context.startForegroundService(intent); else context.startService(intent);
        } catch(RuntimeException e) {
            // An OEM may restrict background starts. The next foreground visit retries safely.
            android.util.Log.w("HomeRemoteReceiver","Receiver start: "+e.getClass().getSimpleName());
        }
    }
    @Override public void onCreate() {
        super.onCreate(); active=this;
        NotificationManager manager=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        if(Build.VERSION.SDK_INT>=26) manager.createNotificationChannel(new NotificationChannel("remote_connection","Связь с пультом",NotificationManager.IMPORTANCE_LOW));
        Notification.Builder notice=Build.VERSION.SDK_INT>=26?new Notification.Builder(this,"remote_connection"):new Notification.Builder(this);
        PendingIntent open=PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        startForeground(8787,notice.setSmallIcon(R.drawable.ic_remote).setContentTitle("Приёмник пульта готов")
            .setContentText("Сохранённый телефон подключается по домашней сети автоматически")
            .setContentIntent(open).setOngoing(true).setShowWhen(false).build());
    }
    @Override public int onStartCommand(Intent intent,int flags,int startId) {
        timer.removeCallbacks(maintain); timer.post(maintain); return START_STICKY;
    }
    private void ensureServers() {
        try {
            if(server==null || !server.isRunning()) {
                if(discovery!=null) { discovery.close(); discovery=null; }
                if(server!=null) server.close();
                server=new LocalServer(this); server.start();
            }
            if(discovery==null || !discovery.isRunning()) {
                if(discovery!=null) discovery.close();
                discovery=new DiscoveryServer(this,server); discovery.start();
            }
            failure="";
        } catch(Exception e) { failure="Не удалось открыть связь: "+e.getClass().getSimpleName(); }
    }
    String wifiAddress() {
        ConnectivityManager cm=(ConnectivityManager)getSystemService(CONNECTIVITY_SERVICE);
        for(Network network:cm.getAllNetworks()) {
            NetworkCapabilities caps=cm.getNetworkCapabilities(network); LinkProperties links=cm.getLinkProperties(network);
            if(caps==null || links==null || caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) || !(caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET))) continue;
            for(LinkAddress address:links.getLinkAddresses()) if(address.getAddress() instanceof Inet4Address && address.getAddress().isSiteLocalAddress()) return address.getAddress().getHostAddress();
        }
        return "";
    }
    JSONObject call(String route,JSONObject request) throws Exception {
        ControlService control=ControlService.active;
        if("/status".equals(route)) {
            JSONObject result=control!=null?control.call(route,request):new JSONObject().put("ok",true).put("model",Build.MODEL)
                .put("android",Build.VERSION.RELEASE).put("sdk",Build.VERSION.SDK_INT).put("accessibility",false)
                .put("receiverVersion",new ReceiverUpdater(this).info().getString("versionName")).put("update",new ReceiverUpdater(this).info());
            boolean focus=new JixinProjector(this).supportsFocus();
            return result.put("autoStart",shouldRun(this)).put("directFocus",focus).put("deviceKind",focus?"projector":"unknown");
        }
        if(control==null) throw new IllegalStateException("Служба управления запускается. Если ожидание затянулось, включи «Управление пультом» в специальных возможностях.");
        return control.call(route,request);
    }
    @Override public IBinder onBind(Intent intent) { return null; }
    @Override public void onDestroy() {
        timer.removeCallbacksAndMessages(null);
        if(discovery!=null) discovery.close(); if(server!=null) server.close();
        if(active==this) active=null; super.onDestroy();
    }
}
