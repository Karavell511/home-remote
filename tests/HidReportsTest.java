package local.akenori.remote;

import java.util.*;

public class HidReportsTest {
    static int checks;
    static void check(boolean ok,String name) { if(!ok) throw new AssertionError(name); checks++; }
    public static void main(String[] args) {
        Map<Integer,Integer> inputBits=new HashMap<>(),outputBits=new HashMap<>();
        int size=0,count=0,id=0;
        byte[] descriptor=HidReports.DESCRIPTOR;
        for(int i=0;i<descriptor.length;) {
            int prefix=descriptor[i++]&255, n=prefix&3; if(n==3)n=4;
            int value=0; for(int j=0;j<n;j++) value|=(descriptor[i++]&255)<<(8*j);
            int type=(prefix>>2)&3,tag=(prefix>>4)&15;
            if(type==1 && tag==7) size=value;
            if(type==1 && tag==8) id=value;
            if(type==1 && tag==9) count=value;
            if(type==0 && tag==8) inputBits.put(id,inputBits.getOrDefault(id,0)+size*count);
            if(type==0 && tag==9) outputBits.put(id,outputBits.getOrDefault(id,0)+size*count);
        }
        check(inputBits.get(1)==64,"keyboard input is 8 bytes");
        check(inputBits.get(2)==16,"consumer input is 2 bytes");
        check(outputBits.get(1)==8,"keyboard LEDs are one byte");
        int[][] keyboard={{19,0x52},{20,0x51},{21,0x50},{22,0x4f},{23,0x28},{82,0x65}};
        for(int[] entry:keyboard) {
            HidReports.Report r=HidReports.forKey(entry[0]);
            check(r.id==1 && r.data.length==8 && (r.data[2]&255)==entry[1],"keyboard usage "+entry[0]);
            check(Arrays.equals(r.release(),new byte[8]),"keyboard release "+entry[0]);
        }
        int[][] consumer={{4,0x224},{3,0x223},{24,0xe9},{25,0xea},{164,0xe2},{85,0xcd},{176,0x19f}};
        for(int[] entry:consumer) {
            HidReports.Report r=HidReports.forKey(entry[0]);
            check(r.id==2 && r.data.length==2 && ((r.data[0]&255)|((r.data[1]&255)<<8))==entry[1],"consumer usage "+entry[0]);
            check(Arrays.equals(r.release(),new byte[2]),"consumer release "+entry[0]);
        }
        try { HidReports.forKey(223); throw new AssertionError("sleep is unsupported"); } catch(IllegalArgumentException expected) { checks++; }
        System.out.println("PASS: "+checks+" Bluetooth HID descriptor, key and release checks");
    }
}
