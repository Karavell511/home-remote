package local.akenori.remote;

import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.interfaces.RSAPublicKey;
import java.util.*;
import java.util.concurrent.*;
import javax.crypto.Cipher;
import javax.net.SocketFactory;

public final class AdbClientTest {
    static KeyPair key;
    static int checks;
    interface Peer { void run(Socket s) throws Exception; }
    interface Client { void run(AdbClient c, int port) throws Exception; }
    static void check(boolean ok, String name) { if(!ok) throw new AssertionError(name); checks++; }
    static void expectFailure(RunnableWithException action,String name) throws Exception {
        try { action.run(); throw new AssertionError(name); } catch(IOException expected) { checks++; }
    }
    interface RunnableWithException { void run() throws Exception; }
    static void exchange(Peer peer, Client client) throws Exception {
        try(ServerSocket server=new ServerSocket(0,1,InetAddress.getLoopbackAddress()); AdbClient adb=new AdbClient()) {
            ExecutorService executor=Executors.newSingleThreadExecutor();
            Future<?> future=executor.submit(() -> { try(Socket s=server.accept()) { s.setSoTimeout(5000); peer.run(s); } catch(Exception e) { throw new RuntimeException(e); } });
            try { client.run(adb,server.getLocalPort()); future.get(8,TimeUnit.SECONDS); }
            finally { executor.shutdownNow(); }
        }
    }
    static void send(Socket s,int cmd,int a,int b,byte[] payload) throws Exception { s.getOutputStream().write(AdbClient.encode(cmd,a,b,payload)); s.getOutputStream().flush(); }
    static AdbClient.Packet read(Socket s) throws Exception { return AdbClient.read(s.getInputStream()); }
    static void hello(Socket s) throws Exception {
        AdbClient.Packet p=read(s); check(p.command==0x4e584e43 && p.arg0==0x01000000,"classic CNXN");
        send(s,AdbClient.CNXN,0x01000000,4096,AdbClient.utf8("device::ro.product.model=Test;\0"));
    }
    static void connect(AdbClient c,int port) throws Exception { c.connect("127.0.0.1",port,SocketFactory.getDefault(),key,m -> {}); }
    static String text(byte[] data) { return new String(data,StandardCharsets.UTF_8); }
    public static void main(String[] args) throws Exception {
        KeyPairGenerator gen=KeyPairGenerator.getInstance("RSA"); gen.initialize(2048); key=gen.generateKeyPair();
        check(AdbClient.isPrivateIpv4("192.168.1.43"),"private IPv4");
        check(AdbClient.isPrivateIpv4("172.16.0.2"),"private 172");
        for(String ip:Arrays.asList("8.8.8.8","127.0.0.1","172.32.0.1","192.168.0.999","192.168.1.1;id","host.local","192.168.1.255"))
            check(!AdbClient.isPrivateIpv4(ip),"reject "+ip);
        byte[] wire=AdbClient.encode(0x4e584e43,0x01000000,4096,new byte[]{1,2,3});
        check(Arrays.equals(Arrays.copyOf(wire,4),new byte[]{0x43,0x4e,0x58,0x4e}),"little endian magic");
        check(ByteBuffer.wrap(wire).order(ByteOrder.LITTLE_ENDIAN).getInt(16)==6,"wire checksum");
        wire[23]^=1; expectFailure(() -> AdbClient.read(new ByteArrayInputStream(wire)),"reject bad magic");
        byte[] corrupt=AdbClient.encode(AdbClient.CNXN,0,0,new byte[]{9}); corrupt[24]=8;
        expectFailure(() -> AdbClient.read(new ByteArrayInputStream(corrupt)),"reject bad checksum");
        byte[] oversized=AdbClient.encode(AdbClient.CNXN,0,0,new byte[0]); ByteBuffer.wrap(oversized).order(ByteOrder.LITTLE_ENDIAN).putInt(12,Integer.MAX_VALUE);
        expectFailure(() -> AdbClient.read(new ByteArrayInputStream(oversized)),"reject oversized packet");
        byte[] publicKey=AdbClient.encodePublicKey(key); ByteBuffer pub=ByteBuffer.wrap(publicKey).order(ByteOrder.LITTLE_ENDIAN);
        check(publicKey.length==524 && pub.getInt()==64,"ADB public key format");
        int n0=pub.getInt(), lowWord=pub.getInt();
        check((int)((long)n0*(long)lowWord)==-1,"Montgomery inverse");
        byte[] modulus=Arrays.copyOfRange(publicKey,8,264); reverse(modulus);
        check(new java.math.BigInteger(1,modulus).equals(((RSAPublicKey)key.getPublic()).getModulus()),"public modulus");
        check(pub.getInt(520)==65537,"public exponent");

        exchange(s -> { hello(s); AdbClient.Packet open=read(s);
            check(text(open.data).equals("shell:getprop ro.product.model\0"),"shell open service");
            send(s,AdbClient.OKAY,10,open.arg0,new byte[0]);
            send(s,AdbClient.WRTE,10,open.arg0,AdbClient.utf8("Test ")); check(read(s).command==AdbClient.OKAY,"ack first chunk");
            send(s,AdbClient.WRTE,10,open.arg0,AdbClient.utf8("Projector\r\n")); check(read(s).command==AdbClient.OKAY,"ack second chunk");
            send(s,AdbClient.CLSE,10,open.arg0,new byte[0]); check(read(s).command==AdbClient.CLSE,"close reply");
        },(c,p) -> { connect(c,p); check(c.shell("getprop ro.product.model").equals("Test Projector"),"assembled output"); });

        exchange(s -> { read(s); byte[] challenge=new byte[20]; new SecureRandom().nextBytes(challenge);
            send(s,AdbClient.AUTH,1,0,challenge); AdbClient.Packet signed=read(s);
            Cipher verify=Cipher.getInstance("RSA/ECB/PKCS1Padding"); verify.init(Cipher.DECRYPT_MODE,key.getPublic()); byte[] digest=verify.doFinal(signed.data);
            check(signed.arg0==2 && digest.length==35 && Arrays.equals(Arrays.copyOfRange(digest,15,35),challenge),"RSA challenge signature");
            check(digest[0]==0x30 && digest[14]==0x14,"SHA1 digest prefix");
            send(s,AdbClient.AUTH,1,0,challenge); AdbClient.Packet pubPacket=read(s);
            check(pubPacket.arg0==3 && text(pubPacket.data).endsWith(" projector-remote@phone\0"),"public key authorization prompt");
            String b64=text(pubPacket.data).split(" ")[0]; check(Arrays.equals(Base64.getDecoder().decode(b64),publicKey),"sent public key");
            send(s,AdbClient.CNXN,0x01000000,4096,AdbClient.utf8("device::\0"));
        },AdbClientTest::connect);

        exchange(s -> { hello(s); AdbClient.Packet open=read(s); send(s,AdbClient.CLSE,0,open.arg0,new byte[0]); read(s); },
            (c,p) -> { connect(c,p); expectFailure(() -> c.shell("getprop ro.product.model"),"refused shell"); });
        exchange(s -> { hello(s);
            for(int i=0;i<2;i++) {
                AdbClient.Packet open=read(s); send(s,AdbClient.OKAY,20+i,open.arg0,new byte[0]);
                if(i==1) send(s,AdbClient.CLSE,20,1,new byte[0]); // delayed duplicate close from the prior stream
                send(s,AdbClient.WRTE,20+i,open.arg0,AdbClient.utf8("value"+i)); read(s);
                send(s,AdbClient.CLSE,20+i,open.arg0,new byte[0]); read(s);
            }
        },(c,p) -> { connect(c,p); check(c.shell("getprop ro.product.model").equals("value0"),"first sequential stream");
            check(c.shell("getprop ro.build.version.release").equals("value1"),"ignore duplicate close of finished stream"); });
        exchange(s -> { hello(s); AdbClient.Packet open=read(s); send(s,AdbClient.OKAY,10,open.arg0,new byte[0]);
            check(text(open.data).contains("input keyevent 19; echo __AR_EXIT:$?"),"direction key command");
            send(s,AdbClient.WRTE,10,open.arg0,AdbClient.utf8("SecurityException\n__AR_EXIT:0\n")); read(s);
            send(s,AdbClient.CLSE,10,open.arg0,new byte[0]); read(s);
        },(c,p) -> { connect(c,p); expectFailure(() -> c.key(19),"permission error not reported as success"); });
        exchange(s -> { hello(s); AdbClient.Packet open=read(s); send(s,AdbClient.OKAY,10,open.arg0,new byte[0]);
            send(s,AdbClient.WRTE,10,open.arg0,AdbClient.utf8("__AR_EXIT:0\n")); read(s); send(s,AdbClient.CLSE,10,open.arg0,new byte[0]); read(s);
        },(c,p) -> { connect(c,p); c.key(24); checks++; expectFailure(() -> c.key(999),"unsupported key rejected"); });
        exchange(s -> { read(s); s.getInputStream().read(); },(c,p) -> {
            ExecutorService exec=Executors.newSingleThreadExecutor(); Future<?> task=exec.submit(() -> { try { connect(c,p); throw new AssertionError("closed connection succeeded"); } catch(Exception expected) {} });
            Thread.sleep(100); c.close(); task.get(2,TimeUnit.SECONDS); checks++; exec.shutdownNow();
        });
        System.out.println("PASS: "+checks+" protocol, authorization, error and cancellation checks");
    }
    static void reverse(byte[] bytes) { for(int i=0;i<bytes.length/2;i++) { byte b=bytes[i]; bytes[i]=bytes[bytes.length-1-i]; bytes[bytes.length-1-i]=b; } }
}
