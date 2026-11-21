package club.thor.pads;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Creates optional profile files only; never opens or rewrites Eden's configuration. */
final class EdenProfiles {
    static final String DEFAULT_SERVERS="127.0.0.1:26760,127.0.0.1:37821";
    static final String CLUB_SERVER="127.0.0.1:37821";
    static final String GUID="0000000000000000000000007f000001";
    static final class Servers {
        final String list;
        final int clubIndex;
        Servers(String list,int clubIndex){this.list=list;this.clubIndex=clubIndex;}
    }
    static Servers servers(String input){
        if(input==null)throw new IllegalArgumentException("Enter Eden's UDP server list in its current order.");
        input=input.trim();
        if(input.startsWith("\"")&&input.endsWith("\"")&&input.length()>=2)input=input.substring(1,input.length()-1);
        String[] entries=input.split(",",-1);
        if(entries.length>8)throw new IllegalArgumentException("Eden supports at most eight UDP servers.");
        List<String> normalized=new ArrayList<>();int club=-1;
        for(String entry:entries){
            String[] endpoint=entry.trim().split(":",-1);
            if(endpoint.length!=2)throw new IllegalArgumentException("Use IPv4:port entries separated by commas.");
            String[] octets=endpoint[0].split("\\.",-1);
            if(octets.length!=4)throw new IllegalArgumentException("Use an IPv4 address for each UDP server.");
            StringBuilder address=new StringBuilder();
            try{
                for(String octet:octets){
                    if(!octet.matches("[0-9]{1,3}"))throw new NumberFormatException();
                    int value=Integer.parseInt(octet);if(value>255)throw new NumberFormatException();
                    if(address.length()>0)address.append('.');address.append(value);
                }
                if(!endpoint[1].matches("[0-9]{1,5}"))throw new NumberFormatException();
                int port=Integer.parseInt(endpoint[1]);if(port<1||port>65535)throw new NumberFormatException();
                address.append(':').append(port);
            }catch(NumberFormatException invalid){throw new IllegalArgumentException("Check the IPv4 addresses and port numbers.");}
            String server=address.toString();
            if(server.equals(CLUB_SERVER)){
                if(club>=0)throw new IllegalArgumentException("List Club's server only once.");
                club=normalized.size();
            }
            normalized.add(server);
        }
        if(club<0)throw new IllegalArgumentException("Add 127.0.0.1:37821 to Eden's server list, then include it here in the same position.");
        return new Servers(String.join(",",normalized),club);
    }
    static String name(int slot){
        if(slot<0||slot>3)throw new IllegalArgumentException("Choose player 1 through 4.");
        return "Controller Club P"+(slot+1)+" Stable";
    }
    static String profile(int slot,Servers servers){
        name(slot);
        String input="engine:cemuhookudp,guid:"+GUID+",port:37821,pad:"+(4*servers.clubIndex+slot)+",";
        StringBuilder out=new StringBuilder("[Controls]\ntype\\default=true\ntype=0\n");
        String[] buttons={"a","b","x","y","lstick","rstick","l","r","zl","zr","plus","minus","dleft","dup","dright","ddown"};
        int[] codes={16384,8192,32768,4096,2,4,1024,2048,256,512,8,1,128,16,32,64};
        for(int i=0;i<buttons.length;i++)binding(out,"button_"+buttons[i],"\""+input+"button:"+codes[i]+"\"");
        for(String button:new String[]{"slleft","srleft","home","screenshot","slright","srright"})binding(out,"button_"+button,"[empty]");
        binding(out,"lstick","\""+input+"axis_x:0,axis_y:1\"");
        binding(out,"rstick","\""+input+"axis_x:2,axis_y:3\"");
        // Keep the verified motion serialization, including its field order.
        String motion="\"engine:cemuhookudp,guid:"+GUID+",pad:"+(4*servers.clubIndex+slot)+",port:37821,motion:0\"";
        binding(out,"motionleft",motion);binding(out,"motionright",motion);
        out.append("use_system_vibrator\\default=true\nuse_system_vibrator=").append(slot==0?"true":"false").append('\n');
        return out.toString();
    }
    private static void binding(StringBuilder out,String key,String value){
        out.append(key).append("\\default=false\n").append(key).append('=').append(value).append('\n');
    }
    static String readme(Servers servers){
        return "Controller Club — stable Eden profiles\n\n"
            +"These optional profiles use Club's loopback input for buttons, sticks and gyro.\n"
            +"They preserve the verified Xbox-position button layout.\n\n"
            +"Eden UDP server list, in this exact order:\n"+servers.list+"\n\n"
            +"Club is server "+(servers.clubIndex+1)+". P1–P4 use pad IDs "+(4*servers.clubIndex)+"–"+(4*servers.clubIndex+3)+".\n"
            +"Keep the server order unchanged, or export new profiles for the new order.\n\n"
            +"1. Save any custom Eden player layout as its own profile first.\n"
            +"2. Extract this archive. Copy only the four .ini files into Eden's config/input folder, alongside existing profiles.\n"
            +"   Do not replace config.ini or existing profiles. If a matching Stable filename already exists, rename the new file before copying.\n"
            +"3. In Eden Settings → Controls, enable the UDP/Cemuhook input option and include Club's server above.\n"
            +"4. For each player you want to use with Club, open Profile and load Controller Club P1 Stable, P2 Stable, P3 Stable or P4 Stable.\n"
            +"   Leave other players and profiles as you prefer. Use the Profile dialog, not the device Auto-map picker.\n"
            +"5. Start Club routing and launch your game. The physical controllers follow your saved Club lineup.\n\n"
            +"Eden can keep duplicate Android device entries or change their numeric ports after reconnects.\n"
            +"Those device-picker entries belong to Eden. These profiles bypass that Android GUID/port path.\n"
            +"Generic UDP labels are also Eden's labels; do not use them to replace the loaded profile with Auto-map.\n\n"
            +"Club exports files only. It does not select a profile, change player assignments, or rewrite Eden's settings.\n";
    }
    static void writeZip(OutputStream output,Servers servers) throws IOException {
        try(ZipOutputStream zip=new ZipOutputStream(output)){
            for(int slot=0;slot<4;slot++)entry(zip,name(slot)+".ini",profile(slot,servers));
            entry(zip,"README.txt",readme(servers));
        }
    }
    private static void entry(ZipOutputStream zip,String name,String text) throws IOException {
        ZipEntry entry=new ZipEntry(name);entry.setTime(0);zip.putNextEntry(entry);
        zip.write(text.getBytes(StandardCharsets.UTF_8));zip.closeEntry();
    }
    private EdenProfiles(){}
}
