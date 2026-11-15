package local.homeremote.protocol;

public class DiscoveryProofTest {
    private static int count;
    private static void check(boolean value,String message) { if(!value) throw new AssertionError(message); count++; }
    public static void main(String[] args) throws Exception {
        String token="01".repeat(32), nonce="02".repeat(32);
        String expected="2b431be6d2e8f8da7b4df626face7c1e7e20ba3bcb4cbebfcd8e6ea53c481974";
        check(DiscoveryProof.sign(token,nonce).equals(expected),"Independent HMAC vector");
        check(DiscoveryProof.verify(token,nonce,expected),"Valid paired receiver");
        check(!DiscoveryProof.verify("03".repeat(32),nonce,expected),"Another receiver rejected");
        check(!DiscoveryProof.verify(token,"04".repeat(32),expected),"Replayed response rejected");
        check(!DiscoveryProof.verify(token,nonce,"00".repeat(32)),"Forged response rejected");
        check(!DiscoveryProof.verify(token,nonce,null),"Missing proof rejected");
        check(!DiscoveryProof.verify(token,"",expected),"Empty challenge rejected");
        check(!DiscoveryProof.verify("123456",nonce,expected),"PIN cannot replace pairing key");
        String addressProof=DiscoveryProof.sign(token,nonce,"10.0.2.16");
        check(DiscoveryProof.verify(token,nonce,"10.0.2.16",addressProof),"Advertised address authenticated");
        check(!DiscoveryProof.verify(token,nonce,"10.0.2.200",addressProof),"Tampered address rejected");
        String fresh=DiscoveryProof.nonce();
        check(fresh.matches("[0-9a-f]{64}")&&!fresh.equals(DiscoveryProof.nonce()),"Fresh random challenge");
        System.out.println("PASS: "+count+" discovery proof checks");
    }
}
