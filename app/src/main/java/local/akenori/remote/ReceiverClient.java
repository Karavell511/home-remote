package local.akenori.remote;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import javax.net.SocketFactory;
import org.json.*;

final class ReceiverClient {
    final String ip;
    private final SocketFactory sockets;
    String token="";
    ReceiverClient(String ip,SocketFactory sockets) { if(!AdbClient.isPrivateIpv4(ip)) throw new IllegalArgumentException("Введи локальный IP устройства"); this.ip=ip; this.sockets=sockets; }
    JSONObject request(String route,JSONObject body) throws Exception {
        return exchange(route,body.toString().getBytes(StandardCharsets.UTF_8),"application/json","",6000);
    }
    JSONObject update(byte[] apk) throws Exception {
        byte[] digest=java.security.MessageDigest.getInstance("SHA-256").digest(apk); StringBuilder hash=new StringBuilder();
        for(byte b:digest) hash.append(String.format(Locale.US,"%02x",b&255));
        return exchange("/update/install",apk,"application/vnd.android.package-archive","X-Apk-Sha256: "+hash+"\r\n",20000);
    }
    private JSONObject exchange(String route,byte[] payload,String type,String extra,int timeout) throws Exception {
        try(Socket socket=openSocket()) {
            socket.setTcpNoDelay(true); socket.connect(new InetSocketAddress(ip,8787),2500); socket.setSoTimeout(timeout);
            String header="POST "+route+" HTTP/1.1\r\nHost: "+ip+":8787\r\nContent-Type: "+type+"\r\nContent-Length: "+payload.length+"\r\nAuthorization: Bearer "+token+"\r\n"+extra+"Connection: close\r\n\r\n";
            ByteArrayOutputStream packet=new ByteArrayOutputStream(); packet.write(header.getBytes(StandardCharsets.US_ASCII)); packet.write(payload);
            socket.getOutputStream().write(packet.toByteArray()); socket.getOutputStream().flush();
            InputStream in=socket.getInputStream(); ByteArrayOutputStream raw=new ByteArrayOutputStream(); byte[] buffer=new byte[4096]; int n;
            while((n=in.read(buffer))!=-1) { raw.write(buffer,0,n); if(raw.size()>131072) throw new IOException("Слишком большой ответ"); }
            String response=new String(raw.toByteArray(),StandardCharsets.UTF_8); int split=response.indexOf("\r\n\r\n");
            if(split<0) throw new IOException("Это не приёмник пульта");
            JSONObject result=new JSONObject(response.substring(split+4));
            if(response.startsWith("HTTP/1.1 401 ")) throw new AuthenticationException(result.optString("error","Нужен новый код подключения"),"/pair".equals(route)?"":token);
            if(!response.startsWith("HTTP/1.1 200 ")) throw new RemoteException(result.optString("error","Запрос не выполнен"));
            return result;
        }
    }
    private Socket openSocket() throws IOException {
        try { return sockets.createSocket(); }
        catch(SocketException e) {
            // Some VPNs forbid explicit network binding but route local destinations normally.
            if(e.getMessage()!=null && e.getMessage().contains("EPERM")) return SocketFactory.getDefault().createSocket();
            throw e;
        }
    }
    static final class RemoteException extends Exception { RemoteException(String message) { super(message); } }
    static final class AuthenticationException extends SecurityException {
        final String rejectedToken;
        AuthenticationException(String message,String rejectedToken) { super(message); this.rejectedToken=rejectedToken; }
    }
}
