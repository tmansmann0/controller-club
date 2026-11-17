package club.thor.pads;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** Checks motion offsets, fragmented BLE reports, and delta packets without sensor data. */
public final class SteamReportTest {
    private static byte[] frame(int index,boolean last,byte[] packet){
        byte[] report=new byte[19];
        report[0]=(byte)(0x80|index|(last?0x40:0));
        System.arraycopy(packet,index*18,report,1,18);
        return report;
    }
    public static void main(String[] args){
        SteamReport decoder=new SteamReport();
        byte[] packet=new byte[36];
        ByteBuffer data=ByteBuffer.wrap(packet).order(ByteOrder.LITTLE_ENDIAN);
        data.putShort((short)0xe04); // right pad, accelerometer, gyro
        data.putShort((short)111).putShort((short)222);
        data.putShort((short)16384).putShort((short)-8192).putShort((short)4096);
        data.putShort((short)16384).putShort((short)-16384).putShort((short)8192);
        if(decoder.accept(frame(0,false,packet))!=null)throw new AssertionError("premature frame");
        if(decoder.accept(frame(1,true,packet))==null)throw new AssertionError("missing controls");
        String expected="MOTION 1000000000 123456 -1.0 -0.25 -0.5 1000.0 -500.0 1000.0";
        if(!expected.equals(decoder.motion(123456)))throw new AssertionError("motion scale or axis mapping");
        if(decoder.motion(123457)!=null)throw new AssertionError("repeated sensor sample");
        byte[] buttons=new byte[36];
        buttons[0]=0x14;
        if(decoder.accept(frame(0,true,buttons))==null)throw new AssertionError("buttons delta rejected");
        if(decoder.motion(123458)!=null)throw new AssertionError("buttons created motion sample");
        if(decoder.accept(new byte[3])!=null)throw new AssertionError("short frame accepted");
        System.out.println("Steam BLE motion offsets, scales, fragments and deltas: pass");
    }
}
