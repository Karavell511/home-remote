package local.akenori.remote;

import android.app.Activity;
import android.net.*;
import android.os.Bundle;
import android.view.WindowManager;
import android.widget.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Serves one bundled APK while this screen is open. No filesystem browsing or uploads. */
public class InstallReceiverActivity extends Activity {
    private volatile ServerSocket listener;
    private volatile boolean closed;
    private final Set<Socket> peers=ConcurrentHashMap.newKeySet();
    private final ThreadPoolExecutor workers=new ThreadPoolExecutor(2,2,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(4));
    private final AtomicInteger downloads=new AtomicInteger();
    private TextView address,status;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state); getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        LinearLayout page=new LinearLayout(this); page.setOrientation(LinearLayout.VERTICAL); int pad=(int)(24*getResources().getDisplayMetrics().density); page.setPadding(pad,pad,pad,pad); page.setBackgroundColor(0xfff3f1e9);
        ScrollView scroll=new ScrollView(this); scroll.addView(page); setContentView(scroll);
        TextView title=new TextView(this); title.setText("Установка на ТВ / проектор"); title.setTextSize(28); page.addView(title);
        TextView help=new TextView(this); help.setText("Оставь этот экран открытым. В браузере телевизора или проектора введи адрес ниже целиком. Оба устройства должны быть в одной домашней сети. Экран может быть подключён к роутеру кабелем."); help.setTextSize(19); page.addView(help);
        address=new TextView(this); address.setTextSize(27); address.setTextIsSelectable(true); page.addView(address);
        status=new TextView(this); status.setTextSize(18); status.setText("Готовим загрузку…"); page.addView(status);
        TextView steps=new TextView(this); steps.setText("На большом экране открой скачанный projector-receiver.apk и нажми «Установить» или «Обновить». При запросе Android разреши установку из браузера. Если браузера нет, перенеси APK приёмника из комплекта на USB-флешке и открой через файловый менеджер. APK подходит для Android 7+, Android TV и Google TV.\n\nВ приёмнике нажми «Разрешить управление» и включи «Управление пультом» в специальных возможностях. Затем вернись и нажми «Разрешить обновления по Wi-Fi».\n\nНа телефоне вернись в пульт → «Подключить» и введи IP и код с большого экрана. Следующие обновления будут поступать из новой версии пульта при подключении. Android иногда требует подтверждения."); steps.setTextSize(18); page.addView(steps);
        Button stop=new Button(this); stop.setText("Закрыть загрузку"); page.addView(stop); stop.setOnClickListener(v -> finish());
        new Thread(this::startServer,"receiver-apk-download").start();
    }
    private String wifiAddress() {
        ConnectivityManager cm=(ConnectivityManager)getSystemService(CONNECTIVITY_SERVICE);
        for(Network n:cm.getAllNetworks()) {
            NetworkCapabilities caps=cm.getNetworkCapabilities(n); LinkProperties links=cm.getLinkProperties(n);
            if(caps==null || links==null || !caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) continue;
            for(LinkAddress a:links.getLinkAddresses()) if(a.getAddress() instanceof Inet4Address && a.getAddress().isSiteLocalAddress()) return a.getAddress().getHostAddress();
        }
        throw new IllegalStateException("Подключи телефон к домашнему Wi-Fi и открой этот экран снова.");
    }
    private void startServer() {
        try {
            String ip=wifiAddress(); byte[] apk;
            try(InputStream in=getAssets().open("projector-receiver.apk"); ByteArrayOutputStream out=new ByteArrayOutputStream()) { byte[] buffer=new byte[8192]; int n; while((n=in.read(buffer))!=-1) out.write(buffer,0,n); apk=out.toByteArray(); }
            ServerSocket server=new ServerSocket(); listener=server; server.setReuseAddress(true);
            if(closed) { server.close(); return; }
            server.bind(new InetSocketAddress(ip,8085));
            runOnUiThread(() -> { if(!closed) { address.setText("http://"+ip+":8085/"); status.setText("Готово. Ожидаем загрузку с телевизора или проектора."); } });
            while(!closed) {
                Socket peer=server.accept(); peers.add(peer);
                try { workers.execute(() -> serve(peer,apk)); } catch(RejectedExecutionException e) { peers.remove(peer); peer.close(); }
            }
        } catch(Exception e) { runOnUiThread(() -> { if(!closed) status.setText("Не удалось открыть загрузку: "+e.getMessage()); }); }
    }
    private void serve(Socket peer,byte[] apk) {
        try(Socket socket=peer) {
            socket.setSoTimeout(5000);
            if(!socket.getInetAddress().isSiteLocalAddress() && !socket.getInetAddress().isLoopbackAddress()) return;
            InputStream in=socket.getInputStream(); ByteArrayOutputStream header=new ByteArrayOutputStream(); int matched=0; int[] end={13,10,13,10};
            while(matched<4) { int b=in.read(); if(b<0 || header.size()>8192) return; header.write(b); matched=b==end[matched]?matched+1:b==13?1:0; }
            String line=new String(header.toByteArray(),StandardCharsets.US_ASCII).split("\r\n")[0]; String[] parts=line.split(" ");
            boolean get=parts.length==3 && parts[0].equals("GET"), head=parts.length==3 && parts[0].equals("HEAD");
            boolean found=parts.length==3 && (parts[1].equals("/") || parts[1].equals("/projector-receiver.apk"));
            OutputStream out=socket.getOutputStream();
            if((!get && !head) || !found) { out.write("HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".getBytes(StandardCharsets.US_ASCII)); return; }
            String response="HTTP/1.1 200 OK\r\nContent-Type: application/vnd.android.package-archive\r\nContent-Disposition: attachment; filename=projector-receiver.apk\r\nContent-Length: "+apk.length+"\r\nCache-Control: no-store\r\nConnection: close\r\n\r\n";
            out.write(response.getBytes(StandardCharsets.US_ASCII)); if(get) out.write(apk); out.flush();
            if(get) { int count=downloads.incrementAndGet(); runOnUiThread(() -> { if(!closed) status.setText("APK отправлен ("+count+"). На большом экране открой загрузки браузера и установи приложение."); }); }
        } catch(IOException ignored) { }
        finally { peers.remove(peer); }
    }
    @Override protected void onDestroy() {
        closed=true; if(listener!=null) try { listener.close(); } catch(IOException ignored) { }
        workers.shutdownNow(); for(Socket s:peers) try { s.close(); } catch(IOException ignored) { } peers.clear(); super.onDestroy();
    }
}
