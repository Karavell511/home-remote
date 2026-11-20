package local.homeremote.protocol;

import java.util.regex.*;

/** NEC: address bytes, command and inverted command, each least-significant bit first. */
public final class NecIr {
    public static final int CARRIER=38000;
    private NecIr() {}
    public static int[] frame(int address,int command) {
        if(address<0 || address>65535 || command<0 || command>255) throw new IllegalArgumentException("Invalid NEC value");
        long bits=(address&65535L)|((long)command<<16)|((long)(command^255)<<24);
        int[] pattern=new int[67]; pattern[0]=9000; pattern[1]=4500;
        for(int i=0;i<32;i++) { pattern[2+i*2]=560; pattern[3+i*2]=((bits>>>i)&1)==0?560:1690; }
        pattern[66]=560; return pattern;
    }
    public static int[] parseAmlogic(String config) {
        if(config==null || config.length()>32768 || !config.toLowerCase(java.util.Locale.ROOT).contains("nec")) return null;
        Matcher custom=Pattern.compile("(?m)^\\s*custom_code\\s*=\\s*0x([0-9a-fA-F]{1,4})\\s*(?:#.*)?$").matcher(config);
        Matcher factory=Pattern.compile("(?m)^\\s*factory_code\\s*=\\s*0x([0-9a-fA-F]{8})\\s*(?:#.*)?$").matcher(config);
        int address;
        if(custom.find()) {
            address=Integer.parseInt(custom.group(1),16);
            if(custom.find() || factory.find()) return null;
        } else {
            if(!factory.find()) return null;
            address=(int)(Long.parseLong(factory.group(1),16)>>>16);
            if(factory.find()) return null;
        }
        Matcher keys=Pattern.compile("(?ms)^\\s*key_begin\\s*$\\n(.*?)^\\s*key_end\\s*$").matcher(config);
        if(!keys.find()) return null;
        String map=keys.group(1); if(keys.find()) return null;
        int power=key(map,116,true),home=key(map,102,false);
        return power<0 || home<0?null:new int[]{address,power,home};
    }
    private static int key(String map,int linuxKey,boolean unique) {
        Matcher match=Pattern.compile("(?m)^\\s*0x([0-9a-fA-F]{1,2})\\s+"+linuxKey+"\\s*(?:#.*)?$").matcher(map);
        if(!match.find()) return -1;
        // Several physical buttons may map to Home; either is safe for a probe.
        int value=Integer.parseInt(match.group(1),16); return unique && match.find()?-1:value;
    }
}
