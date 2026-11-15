package local.akenori.receiver;

import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import java.util.concurrent.*;

final class LocalServer implements Closeable {
    private final ControlService owner;
    private final ServerSocket listener;
    private final ThreadPoolExecutor workers=new ThreadPoolExecutor(2,2,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(8));
    private final SecureRandom random=new SecureRandom();
    private final Set<Socket> peers=ConcurrentHashMap.newKeySet();
    private volatile boolean closed;
    private String pin,token;
    private long expires,blockedUntil;
    private int failures;
    private boolean paired;
    LocalServer(ControlService owner) throws IOException {
        this.owner=owner; listener=new ServerSocket(); listener.setReuseAddress(true); listener.bind(new InetSocketAddress(8787));
        String saved=owner.getSharedPreferences("pairing",0).getString("token","");
        if(saved.matches("[0-9a-f]{64}")) { token=saved; paired=true; } else newPairing();
    }
    synchronized void newPairing() {
        owner.getSharedPreferences("pairing",0).edit().clear().commit();
        pin=String.format(Locale.US,"%06d",random.nextInt(1000000)); byte[] bytes=new byte[32]; random.nextBytes(bytes);
        StringBuilder b=new StringBuilder(); for(byte value:bytes) b.append(String.format(Locale.US,"%02x",value&255)); token=b.toString();
        expires=System.currentTimeMillis()+300000; failures=0; blockedUntil=0; paired=false;
    }
    synchronized String pairingCode() { return paired?"Подключено":System.currentTimeMillis()>expires?"Код истёк":pin; }
    synchronized String discoveryProof(String nonce,String address) throws GeneralSecurityException {
        return paired?local.homeremote.protocol.DiscoveryProof.sign(token,nonce,address):null;
    }
    private synchronized String pair(String value) {
        long now=System.currentTimeMillis();
        if(paired || now>expires || now<blockedUntil) return null;
        if(!equal(pin,value)) { if(++failures>=5) { blockedUntil=now+60000; failures=0; } return null; }
        if(!owner.getSharedPreferences("pairing",0).edit().putString("token",token).commit()) return null;
        paired=true; return token;
    }
    private synchronized boolean authorized(String value) { return paired && equal(token,value); }
    private static boolean equal(String a,String b) { return b!=null && MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8),b.getBytes(StandardCharsets.UTF_8)); }
    void start() {
        new Thread(() -> {
            while(!closed) try {
                Socket socket=listener.accept();
                peers.add(socket);
                try { workers.execute(() -> serve(socket)); } catch(RejectedExecutionException e) { peers.remove(socket); socket.close(); }
            } catch(IOException e) { if(!closed) owner.failure="Сервер остановился"; break; }
        },"projector-receiver").start();
    }
    private void serve(Socket socket) {
        try(Socket peer=socket) {
            peer.setSoTimeout(3000); peer.setTcpNoDelay(true);
            if(!peer.getInetAddress().isLoopbackAddress() && (!peer.getInetAddress().isSiteLocalAddress() || !peer.getLocalAddress().getHostAddress().equals(owner.wifiAddress()))) { respond(peer,403,error("Доступ только из домашней Wi-Fi сети")); return; }
            InputStream in=peer.getInputStream(); ByteArrayOutputStream head=new ByteArrayOutputStream(); int match=0;
            while(match<4) {
                int value=in.read(); if(value<0) throw new EOFException(); head.write(value);
                if(head.size()>8192) throw new IOException("Слишком большой запрос");
                int[] end={13,10,13,10}; match=value==end[match]?match+1:value==13?1:0;
            }
            String[] lines=new String(head.toByteArray(),StandardCharsets.US_ASCII).split("\r\n"); String[] first=lines[0].split(" ");
            if(first.length!=3) { respond(peer,400,error("Неверный запрос")); return; }
            Map<String,String> headers=new HashMap<>();
            for(int i=1;i<lines.length;i++) { int colon=lines[i].indexOf(':'); if(colon>0) headers.put(lines[i].substring(0,colon).toLowerCase(Locale.US),lines[i].substring(colon+1).trim()); }
            if(headers.containsKey("transfer-encoding")) { respond(peer,400,error("Неподдерживаемый запрос")); return; }
            int length=Integer.parseInt(headers.containsKey("content-length")?headers.get("content-length"):"0");
            String route=first[1];
            if("POST".equals(first[0]) && "/update/install".equals(route)) {
                String authorization=headers.get("authorization");
                if(authorization==null || !authorization.startsWith("Bearer ") || !authorized(authorization.substring(7))) { respond(peer,401,error("Нужно подключиться заново по коду проектора")); return; }
                if(length<=0 || length>ReceiverUpdater.MAX_APK) { respond(peer,413,error("Неверный размер APK")); return; }
                if(!"application/vnd.android.package-archive".equals(headers.get("content-type"))) { respond(peer,400,error("Нужен APK приёмника")); return; }
                peer.setSoTimeout(10000);
                try { respond(peer,200,new ReceiverUpdater(owner).receive(in,length,headers.get("x-apk-sha256"))); }
                catch(Exception e) { respond(peer,400,error(e.getMessage()==null?"Обновление не принято":e.getMessage())); }
                return;
            }
            if(length<0 || length>4096) { respond(peer,413,error("Слишком большой запрос")); return; }
            byte[] data=new byte[length]; int offset=0; while(offset<length) { int n=in.read(data,offset,length-offset); if(n<0) throw new EOFException(); offset+=n; }
            if("GET".equals(first[0]) && "/health".equals(route)) { respond(peer,200,new JSONObject().put("service","akenori-receiver").put("version",1)); return; }
            if(!"POST".equals(first[0])) { respond(peer,405,error("Нужен POST")); return; }
            JSONObject request=length==0?new JSONObject():new JSONObject(new String(data,StandardCharsets.UTF_8));
            if("/pair".equals(route)) {
                String approved=pair(request.optString("pin",""));
                if(approved==null) respond(peer,401,error("Код неверен, истёк или уже использован. На проекторе нажми «Новый код подключения»."));
                else respond(peer,200,new JSONObject().put("ok",true).put("token",approved).put("model",android.os.Build.MODEL));
                return;
            }
            String authorization=headers.get("authorization");
            if(authorization==null || !authorization.startsWith("Bearer ") || !authorized(authorization.substring(7))) { respond(peer,401,error("Нужно подключиться заново по коду проектора")); return; }
            if("/diagnostics/settings-apk".equals(route) || "/diagnostics/projector-apk".equals(route)) {
                java.io.File archive="/diagnostics/settings-apk".equals(route)?new ProjectorCapabilities(owner).settingsArchive():new JixinProjector(owner).providerArchive(request.getString("service"));
                String header="HTTP/1.1 200 OK\r\nContent-Type: application/vnd.android.package-archive\r\nContent-Length: "+archive.length()+"\r\nCache-Control: no-store\r\nConnection: close\r\n\r\n";
                OutputStream out=peer.getOutputStream(); out.write(header.getBytes(StandardCharsets.US_ASCII));
                try(InputStream file=new FileInputStream(archive)) { byte[] buffer=new byte[32768]; int n; while((n=file.read(buffer))!=-1) out.write(buffer,0,n); } out.flush(); return;
            }
            if(!Arrays.asList("/status","/key","/ui","/tap","/range","/capabilities","/open_focus","/update/allow","/keyboard/setup","/keyboard/select","/projector/state","/projector/control").contains(route)) { respond(peer,404,error("Неизвестная команда")); return; }
            try { respond(peer,200,owner.call(route,request)); } catch(Exception e) { respond(peer,400,error("Android не выполнил запрос: "+(e.getCause()!=null?e.getCause().getMessage():e.getMessage()))); }
        } catch(Exception ignored) { /* Malformed or abandoned request; never record credentials or screen content. */
            if((owner.getApplicationInfo().flags&android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE)!=0) android.util.Log.w("HomeRemoteServer",ignored.getClass().getSimpleName());
        }
        finally { peers.remove(socket); }
    }
    private static JSONObject error(String message) throws JSONException { return new JSONObject().put("ok",false).put("error",message); }
    private static void respond(Socket socket,int status,JSONObject body) throws IOException {
        byte[] data=body.toString().getBytes(StandardCharsets.UTF_8);
        String header="HTTP/1.1 "+status+" Response\r\nContent-Type: application/json; charset=utf-8\r\nContent-Length: "+data.length+"\r\nCache-Control: no-store\r\nConnection: close\r\n\r\n";
        OutputStream out=socket.getOutputStream(); out.write(header.getBytes(StandardCharsets.US_ASCII)); out.write(data); out.flush();
    }
    @Override public void close() { closed=true; try { listener.close(); } catch(IOException ignored) {} workers.shutdownNow(); for(Socket peer:peers) try { peer.close(); } catch(IOException ignored) {} peers.clear(); }
}
