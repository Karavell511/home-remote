package local.akenori.receiver;

import java.io.Closeable;
import java.net.*;
import java.nio.charset.StandardCharsets;
import org.json.JSONObject;
import local.homeremote.protocol.DiscoveryProof;

final class DiscoveryServer implements Closeable {
    private final DatagramSocket socket;
    private final ControlService owner;
    private final LocalServer server;
    DiscoveryServer(ControlService owner,LocalServer server) throws Exception {
        this.owner=owner; this.server=server; socket=new DatagramSocket(DiscoveryProof.PORT);
    }
    void start() { new Thread(this::listen,"remote-discovery").start(); }
    private void listen() {
        long window=0; int count=0;
        while(!socket.isClosed()) try {
            byte[] buffer=new byte[1024]; DatagramPacket packet=new DatagramPacket(buffer,buffer.length); socket.receive(packet);
            if(packet.getLength()>=buffer.length || !packet.getAddress().isSiteLocalAddress() || owner.wifiAddress().isEmpty()) continue;
            long now=android.os.SystemClock.elapsedRealtime(); if(now-window>1000) { window=now; count=0; } if(++count>20) continue;
            JSONObject request=new JSONObject(new String(packet.getData(),0,packet.getLength(),StandardCharsets.UTF_8));
            if(!DiscoveryProof.SERVICE.equals(request.optString("service"))) continue;
            String address=owner.wifiAddress();
            String proof=server.discoveryProof(request.optString("nonce"),address);
            JSONObject response=new JSONObject().put("service",DiscoveryProof.SERVICE).put("model",android.os.Build.MODEL).put("ip",address);
            if(proof!=null) response.put("proof",proof);
            byte[] reply=response.toString().getBytes(StandardCharsets.UTF_8);
            socket.send(new DatagramPacket(reply,reply.length,packet.getAddress(),packet.getPort()));
        } catch(Exception ignored) { /* Ignore malformed packets and shutdown; no secret logging. */ }
    }
    @Override public void close() { socket.close(); }
}
