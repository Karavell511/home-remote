package local.homeremote.protocol;

public final class NecIrTest {
    private static int count;
    private static void check(boolean ok,String label) { if(!ok) throw new AssertionError(label); count++; }
    public static void main(String[] args) {
        String config="# Amlogic NEC remote\nfactory_code = 0xFE010001\nkey_begin\n0x10 116\n0x11 102\nkey_end\n";
        int[] parsed=NecIr.parseAmlogic(config);
        check(parsed!=null && parsed[0]==0xfe01 && parsed[1]==0x10 && parsed[2]==0x11,"factory and exact power/home mappings");
        check(NecIr.parseAmlogic(config.replace("0x10 116","0x10 115"))==null,"missing power rejected");
        check(NecIr.parseAmlogic(config.replace("0x11 102","0x11 103"))==null,"missing home rejected");
        check(NecIr.parseAmlogic(config.replace("NEC","RC5"))==null,"unknown protocol rejected");
        check(NecIr.parseAmlogic(config.replace("key_end","0x12 116\nkey_end"))==null,"ambiguous power rejected");
        check(NecIr.parseAmlogic(config+"factory_code = 0xFF000001\n")==null,"ambiguous address rejected");
        String modern="custom_name = nec-test\ncustom_code = 0xfb04\nkey_begin\n0x1A 116\n0x16 102\n0x1F 102\nkey_end\n";
        int[] tab=NecIr.parseAmlogic(modern);
        check(tab!=null && tab[0]==0xfb04 && tab[1]==0x1a && tab[2]==0x16,"modern table and alternate Home mappings");
        check(NecIr.parseAmlogic(modern+"custom_code = 0xff00\n")==null,"duplicate modern address rejected");
        check(NecIr.parseAmlogic(modern+"factory_code = 0xFE010001\n")==null,"mixed address schemes rejected");
        check(NecIr.parseAmlogic(modern+"key_begin\n0x20 116\nkey_end\n")==null,"multiple key tables rejected");
        int[] frame=NecIr.frame(0xfe01,0x10);
        check(frame.length==67 && frame[0]==9000 && frame[1]==4500 && frame[66]==560,"NEC leader and trailing mark");
        long decoded=0; int duration=0;
        for(int value:frame) duration+=value;
        for(int bit=0;bit<32;bit++) {
            if(frame[2+2*bit]!=560) throw new AssertionError("invalid mark");
            if(frame[3+2*bit]==1690) decoded|=1L<<bit;
            else if(frame[3+2*bit]!=560) throw new AssertionError("invalid space");
        }
        check(decoded==0xef10fe01L,"wire bytes and inversion decode to expected frame");
        check(duration>60000 && duration<75000,"one bounded press, no repeat train");
        try { NecIr.frame(65536,1); throw new AssertionError("address bound"); } catch(IllegalArgumentException expected) { count++; }
        try { NecIr.frame(1,256); throw new AssertionError("command bound"); } catch(IllegalArgumentException expected) { count++; }
        System.out.println("PASS: "+count+" NEC profile, bit order, pulse duration and validation checks");
    }
}
