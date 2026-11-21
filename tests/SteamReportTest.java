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
    private static int[] controls(SteamReport decoder,int buttons,int leftTrigger,int rightTrigger){
        byte[] packet=new byte[36];packet[0]=0x34;
        packet[2]=(byte)buttons;packet[3]=(byte)(buttons>>8);packet[4]=(byte)(buttons>>16);
        packet[5]=(byte)leftTrigger;packet[6]=(byte)rightTrigger;
        return parsed(decoder.accept(frame(0,true,packet)));
    }
    private static int[] parsed(String report){
        String[] fields=report.split(" ");int[] values=new int[9];
        for(int i=0;i<9;i++)values[i]=Integer.parseInt(fields[i+2]);
        return values;
    }
    private static void rearPaddles(){
        SteamReport decoder=new SteamReport();
        if(controls(decoder,0x18000,0,0)[0]!=0)throw new AssertionError("unassigned paddles generated input");
        decoder.bindRear(SteamReport.Binding.A,SteamReport.Binding.B);
        if(controls(decoder,0x8000,0,0)[0]!=1)throw new AssertionError("left paddle is not A");
        if(controls(decoder,0x10000,0,0)[0]!=2)throw new AssertionError("right paddle is not B");
        if(controls(decoder,0x18000,0,0)[0]!=3)throw new AssertionError("simultaneous paddles lost input");
        if(controls(decoder,0x80,0,0)[0]!=1)throw new AssertionError("paddle release cleared held face button");
        if(controls(decoder,0,0,0)[0]!=0)throw new AssertionError("paddle output stuck");
        decoder.bindRear(SteamReport.Binding.LT,SteamReport.Binding.RT);
        int[] both=controls(decoder,0x18000,0,0);
        if(both[5]!=32767||both[6]!=32767||(both[0]&192)!=192)throw new AssertionError("digital and analog trigger output");
        int[] released=controls(decoder,0,100,150);
        if(released[5]!=16300||released[6]!=24450)throw new AssertionError("paddles destroyed physical trigger values");
        decoder.bindRear(SteamReport.Binding.UP,SteamReport.Binding.RIGHT);
        int[] dpad=controls(decoder,0x18000,0,0);
        if(dpad[7]!=1||dpad[8]!=-1)throw new AssertionError("paddle D-pad diagonal");
        int[] opposed=controls(decoder,0x18800,0,0); // paddle up + physical down
        if(opposed[8]!=0)throw new AssertionError("opposite D-pad directions were not neutral");
        decoder.bindRear(SteamReport.Binding.LS,SteamReport.Binding.RS);
        if(controls(decoder,0x18000,0,0)[0]!=3072)throw new AssertionError("stick clicks");
        decoder.bindRear(SteamReport.Binding.A,SteamReport.Binding.B);
        controls(decoder,0x8000,0,0);
        int[] rebound=parsed(decoder.bindRear(SteamReport.Binding.X,SteamReport.Binding.NONE));
        if(rebound[0]!=8)throw new AssertionError("live rebind failed to release old output");
        byte[] delta=new byte[36];delta[0]=4;
        if(parsed(decoder.accept(frame(0,true,delta)))[0]!=8)throw new AssertionError("delta lost held paddle");
        if(SteamReport.Binding.saved("invalid")!=SteamReport.Binding.NONE)throw new AssertionError("invalid saved binding");
        if(controls(new SteamReport(),0,0,0)[0]!=0)throw new AssertionError("new decoder inherited pressed state");
    }
    public static void main(String[] args){
        rearPaddles();
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
        System.out.println("Steam BLE motion, fragments, rear paddle bindings, holds, releases and live rebinds: pass");
    }
}
