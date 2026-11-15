package local.akenori.remote;

/** USB HID usages shared by Bluetooth HID keyboards and consumer remotes. */
public final class HidReports {
    private HidReports() {}
    public static final byte[] DESCRIPTOR = new byte[]{
        // Keyboard, report 1: modifiers, reserved, six simultaneous keys.
        0x05,0x01,0x09,0x06,(byte)0xa1,0x01,(byte)0x85,0x01,
        0x05,0x07,0x19,(byte)0xe0,0x29,(byte)0xe7,0x15,0x00,0x25,0x01,0x75,0x01,(byte)0x95,0x08,(byte)0x81,0x02,
        (byte)0x95,0x01,0x75,0x08,(byte)0x81,0x01,
        (byte)0x95,0x06,0x75,0x08,0x15,0x00,0x26,(byte)0xff,0x00,0x05,0x07,0x19,0x00,0x2a,(byte)0xff,0x00,(byte)0x81,0x00,
        // Keyboard LEDs: one output byte.
        (byte)0x95,0x05,0x75,0x01,0x05,0x08,0x19,0x01,0x29,0x05,(byte)0x91,0x02,
        (byte)0x95,0x01,0x75,0x03,(byte)0x91,0x01,(byte)0xc0,
        // Consumer control, report 2: one 16-bit usage (volume, home, back...).
        0x05,0x0c,0x09,0x01,(byte)0xa1,0x01,(byte)0x85,0x02,
        0x15,0x00,0x26,(byte)0xff,0x03,0x19,0x00,0x2a,(byte)0xff,0x03,
        0x75,0x10,(byte)0x95,0x01,(byte)0x81,0x00,(byte)0xc0
    };
    public static final class Report {
        public final int id; public final byte[] data;
        Report(int id,byte[] data) { this.id=id; this.data=data; }
        public byte[] release() { return new byte[data.length]; }
    }
    public static Report forKey(int androidCode) {
        int usage;
        switch(androidCode) {
            case 19: return keyboard(0x52); // up
            case 20: return keyboard(0x51);
            case 21: return keyboard(0x50);
            case 22: return keyboard(0x4f);
            case 23: return keyboard(0x28); // enter
            case 82: return keyboard(0x65); // application menu
            case 4: usage=0x224; break; // AC Back
            case 3: usage=0x223; break; // AC Home
            case 24: usage=0xe9; break;
            case 25: usage=0xea; break;
            case 164: usage=0xe2; break;
            case 85: usage=0xcd; break;
            case 176: usage=0x19f; break; // AL Control Panel, Android SETTINGS where supported
            default: throw new IllegalArgumentException("Эта кнопка недоступна по Bluetooth");
        }
        return new Report(2,new byte[]{(byte)usage,(byte)(usage>>8)});
    }
    private static Report keyboard(int usage) { return new Report(1,new byte[]{0,0,(byte)usage,0,0,0,0,0}); }
}
