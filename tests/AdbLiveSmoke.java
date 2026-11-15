package local.akenori.remote;
import java.security.*;
import javax.net.SocketFactory;
public class AdbLiveSmoke {
    public static void main(String[] args) throws Exception {
        KeyPairGenerator generator=KeyPairGenerator.getInstance("RSA"); generator.initialize(2048);
        try(AdbClient client=new AdbClient()) {
            client.connect("127.0.0.1",5581,SocketFactory.getDefault(),generator.generateKeyPair(),System.out::println);
            System.out.println("Model: "+client.shell("getprop ro.product.model"));
            System.out.println("Android: "+client.shell("getprop ro.build.version.release"));
            client.key(25);
            System.out.println("PASS: real Android daemon, two shell sessions, volume command");
        }
    }
}
