package club.thor.pads;

/** Original Steam Controller BLE reports. Chunk flags and button bits match Valve's SDL driver. */
final class SteamReport {
    private final byte[] packet = new byte[144];
    private int segment;
    private boolean haveAcceleration, haveGyroscope, motionUpdated;
    private final int[] acceleration=new int[3], gyroscope=new int[3];
    private int buttons, triggerL, triggerR, x, y, leftX, leftY, rightX, rightY;

    String accept(byte[] report) {
        // Android GATT strips the HID report number. Some peers include it.
        int header = report.length == 19 ? 0 : report.length == 20 && report[0] == 3 ? 1 : -1;
        if (header < 0 || (report[header] & 0x80) == 0) return null;
        int index = report[header] & 7;
        if (index == 0) segment = 0;
        if (index != segment) { segment = 0; return null; }
        System.arraycopy(report, header + 1, packet, index * 18, 18);
        segment++;
        if ((report[header] & 0x40) == 0) return null;
        int length = segment * 18;
        segment = 0;
        return decode(length);
    }

    private String decode(int length) {
        if ((packet[0] & 15) != 4) return null;
        int flags = (packet[0] & 0xf0) | ((packet[1] & 255) << 8);
        int[] sizes = {3, 2, 3, 4, 4, 4, 6, 6, 8};
        int needed = 2;
        for (int i = 0; i < sizes.length; i++) if ((flags & (0x10 << i)) != 0) needed += sizes[i];
        if (needed > length) return null;

        int offset = 2;
        if ((flags & 0x10) != 0) {
            buttons = (packet[offset] & 255) | ((packet[offset+1] & 255) << 8) | ((packet[offset+2] & 255) << 16);
            offset += 3;
        }
        if ((flags & 0x20) != 0) {
            triggerL = trigger(packet[offset]);
            triggerR = trigger(packet[offset+1]);
            offset += 2;
        }
        if ((flags & 0x40) != 0) offset += 3;
        if ((flags & 0x80) != 0) { x = signed(offset); y = signed(offset+2); offset += 4; }
        if ((flags & 0x100) != 0) { leftX = signed(offset); leftY = signed(offset+2); offset += 4; }
        if ((flags & 0x200) != 0) { rightX = signed(offset); rightY = signed(offset+2); offset += 4; }
        motionUpdated=false;
        if ((flags & 0x400) != 0) {
            for(int i=0;i<3;i++)acceleration[i]=signed(offset+2*i);
            offset+=6;
            haveAcceleration=true;
            motionUpdated=true;
        }
        if ((flags & 0x800) != 0) {
            for(int i=0;i<3;i++)gyroscope[i]=signed(offset+2*i);
            haveGyroscope=true;
            motionUpdated=true;
        }
        int[] masks = {0x80,0x20,0x10,0x40,8,4,2,1,0x1000,0x4000,0x400000,0x40000,0,0,0x2000};
        int keys = 0;
        for (int i = 0; i < masks.length; i++) if ((buttons & masks[i]) != 0) keys |= 1 << i;
        int dx = (pressed(0x200) ? 1 : 0) - (pressed(0x400) ? 1 : 0);
        int dy = (pressed(0x800) ? 1 : 0) - (pressed(0x100) ? 1 : 0);
        if (dx == 0 && dy == 0 && pressed(0x20000)) {
            if (Math.abs(leftX) > Math.abs(leftY)) dx = direction(leftX);
            else dy = -direction(leftY);
        }
        boolean touchingRight = pressed(0x100000);
        return "STEAM INPUT " + keys + " " + x + " " + -y + " "
                + (touchingRight ? rightX : 0) + " " + (touchingRight ? -rightY : 0) + " "
                + (pressed(2) ? 32767 : triggerL) + " " + (pressed(1) ? 32767 : triggerR) + " " + dx + " " + dy;
    }

    String motion(long timestampUs) {
        if(!motionUpdated||!haveAcceleration||!haveGyroscope)return null;
        motionUpdated=false;
        // Match the controller axes and scales used by Valve's SDL driver.
        return "MOTION " + Bridge.STEAM_PAD_ID + " " + timestampUs + " "
            + acceleration[0]/16384.0f + " " + acceleration[2]/16384.0f + " " + -acceleration[1]/16384.0f + " "
            + gyroscope[0]*(2000.0f/32768.0f) + " " + gyroscope[2]*(2000.0f/32768.0f) + " "
            + gyroscope[1]*(2000.0f/32768.0f);
    }

    private boolean pressed(int mask) { return (buttons & mask) != 0; }
    private int signed(int offset) { return (short)((packet[offset] & 255) | ((packet[offset+1] & 255) << 8)); }
    private static int direction(int value) { return Math.abs(value) < 6500 ? 0 : value < 0 ? -1 : 1; }
    private static int trigger(byte value) { return Math.min(32767, (value & 255) * 163); }
}
