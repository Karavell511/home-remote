package local.akenori.remote;

import android.net.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.json.JSONObject;
import local.homeremote.protocol.DiscoveryProof;

final class ReceiverDiscovery {
    static Map<String,String> find(Network network,LinkProperties links,String token,String savedIp) throws Exception {
        Map<String,String> found=new LinkedHashMap<>();
        if(links==null) return found;
        String nonce=DiscoveryProof.nonce();
        byte[] request=new JSONObject().put("service",DiscoveryProof.SERVICE).put("nonce",nonce).toString().getBytes(StandardCharsets.UTF_8);
        try(DatagramSocket socket=new DatagramSocket()) {
            try { network.bindSocket(socket); }
            catch(SocketException e) { if(e.getMessage()==null || !e.getMessage().contains("EPERM")) throw e; }
            socket.setBroadcast(true); socket.setSoTimeout(250);
            Set<InetAddress> targets=new LinkedHashSet<>();
            if(AdbClient.isPrivateIpv4(savedIp)) targets.add(InetAddress.getByName(savedIp));
            for(LinkAddress link:links.getLinkAddresses()) {
                if(!(link.getAddress() instanceof Inet4Address) || !link.getAddress().isSiteLocalAddress() || link.getPrefixLength()>30) continue;
                byte[] address=link.getAddress().getAddress(); int prefix=link.getPrefixLength();
                for(int bit=prefix;bit<32;bit++) address[bit/8]|=(byte)(1<<(7-bit%8));
                targets.add(InetAddress.getByAddress(address));
            }
            long deadline=android.os.SystemClock.elapsedRealtime()+1800,nextSend=0;
            while(android.os.SystemClock.elapsedRealtime()<deadline && !Thread.currentThread().isInterrupted()) {
                if(android.os.SystemClock.elapsedRealtime()>=nextSend) {
                    for(InetAddress address:targets) socket.send(new DatagramPacket(request,request.length,address,DiscoveryProof.PORT));
                    nextSend=android.os.SystemClock.elapsedRealtime()+600;
                }
                byte[] buffer=new byte[1024]; DatagramPacket reply=new DatagramPacket(buffer,buffer.length);
                try { socket.receive(reply); } catch(SocketTimeoutException ignored) { continue; }
                if(reply.getPort()!=DiscoveryProof.PORT || reply.getLength()>=buffer.length || !reply.getAddress().isSiteLocalAddress()) continue;
                try {
                    JSONObject body=new JSONObject(new String(buffer,0,reply.getLength(),StandardCharsets.UTF_8));
                    if(!DiscoveryProof.SERVICE.equals(body.optString("service"))) continue;
                    String address=body.optString("ip"); if(!AdbClient.isPrivateIpv4(address)) continue;
                    if(!token.isEmpty() && !DiscoveryProof.verify(token,nonce,address,body.optString("proof"))) continue;
                    found.put(address,body.optString("model","Устройство"));
                    if(!token.isEmpty()) return found;
                } catch(org.json.JSONException ignored) {}
            }
        }
        return found;
    }
}
