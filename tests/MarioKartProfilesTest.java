package club.thor.pads;

public final class MarioKartProfilesTest {
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    public static void main(String[] args){
        String original="[Core]\nCPUThread = True\n[Controls]\nWiimoteProfile1 = custom-one\nWiimoteSource0 = 2\nWiimoteProfile3 = custom-three\nWiimoteSource2 = 2\nWiimoteProfile4 = custom-four\nWiimoteSource3 = 2\n[Video]\nQuality = 7\n";
        String two=MarioKartProfiles.selectPlayers(original,2);
        check("custom-three".equals(MarioKartProfiles.controlValue(two,"WiimoteProfile3")),"two-player setup touched P3");
        String three=MarioKartProfiles.selectPlayers(two,3);
        check("Controller-Club-Mario-Kart-P3".equals(MarioKartProfiles.controlValue(three,"WiimoteProfile3")),"third player omitted");
        check("1".equals(MarioKartProfiles.controlValue(three,"WiimoteSource2")),"third remote disabled");
        check("custom-four".equals(MarioKartProfiles.controlValue(three,"WiimoteProfile4")),"P4 custom profile changed");
        check(three.contains("CPUThread = True")&&three.contains("Quality = 7"),"unrelated settings changed");
        check(three.equals(MarioKartProfiles.selectPlayers(three,3)),"repeated setup not idempotent");
        String profile=MarioKartProfiles.profileText(3);
        check(profile.contains("Android/1/Controller Club P3:Button A")&&profile.contains("Android/24/Controller Club P3:Axis 15+"),"profile tied to old Android number");
        check(!profile.contains("Controller Club P2:"),"third player reads another seat");
        String restored=MarioKartProfiles.restorePlayer(three,original,3);
        check("custom-three".equals(MarioKartProfiles.controlValue(restored,"WiimoteProfile3")),"custom third profile not restored");
        check("2".equals(MarioKartProfiles.controlValue(restored,"WiimoteSource2")),"custom source not restored");
        String edited=MarioKartProfiles.setControls(three,"WiimoteProfile3","new-user-profile");
        check(edited.equals(MarioKartProfiles.restorePlayer(edited,original,3)),"restore overwrote user's later edit");
        String four=MarioKartProfiles.selectPlayers(original,4);
        check("Controller-Club-Mario-Kart-P4".equals(MarioKartProfiles.controlValue(four,"WiimoteProfile4")),"fourth player omitted");
        try{MarioKartProfiles.selectPlayers(original,5);throw new AssertionError("invalid count accepted");}catch(IllegalArgumentException expected){}
        System.out.println("Mario Kart Wii 2/3/4 players, stable qualifiers, custom settings and selective restore: pass");
    }
}
