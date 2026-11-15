package local.homeremote.protocol;

import java.nio.charset.StandardCharsets;
import java.security.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** Discovery never broadcasts a PIN or bearer token. Each response proves a fresh challenge. */
public final class DiscoveryProof {
    public static final int PORT=8788;
    public static final String SERVICE="home-remote-v1";
    private DiscoveryProof() {}
    public static String nonce() { byte[] bytes=new byte[32]; new SecureRandom().nextBytes(bytes); return hex(bytes); }
    public static String sign(String token,String nonce) throws GeneralSecurityException {
        return sign(token,nonce,"");
    }
    public static String sign(String token,String nonce,String address) throws GeneralSecurityException {
        if(token==null || !token.matches("[0-9a-f]{64}") || nonce==null || !nonce.matches("[0-9a-f]{64}")) throw new GeneralSecurityException("Invalid discovery challenge");
        byte[] key=new byte[32]; for(int i=0;i<32;i++) key[i]=(byte)Integer.parseInt(token.substring(i*2,i*2+2),16);
        Mac mac=Mac.getInstance("HmacSHA256"); mac.init(new SecretKeySpec(key,"HmacSHA256"));
        return hex(mac.doFinal((SERVICE+":"+nonce+(address.isEmpty()?"":":"+address)).getBytes(StandardCharsets.US_ASCII)));
    }
    public static boolean verify(String token,String nonce,String proof) {
        return verify(token,nonce,"",proof);
    }
    public static boolean verify(String token,String nonce,String address,String proof) {
        if(proof==null || !proof.matches("[0-9a-f]{64}")) return false;
        try { return MessageDigest.isEqual(sign(token,nonce,address).getBytes(StandardCharsets.US_ASCII),proof.getBytes(StandardCharsets.US_ASCII)); }
        catch(GeneralSecurityException e) { return false; }
    }
    private static String hex(byte[] value) { StringBuilder out=new StringBuilder(); for(byte b:value) out.append(String.format(java.util.Locale.US,"%02x",b&255)); return out.toString(); }
}
