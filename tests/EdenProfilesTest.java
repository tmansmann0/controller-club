package club.thor.pads;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;
import java.util.zip.*;

public final class EdenProfilesTest {
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    static void bad(String list){
        try{EdenProfiles.servers(list);throw new AssertionError("accepted "+list);}
        catch(IllegalArgumentException expected){}
    }
    static byte[] archive(EdenProfiles.Servers servers) throws Exception {
        ByteArrayOutputStream output=new ByteArrayOutputStream();EdenProfiles.writeZip(output,servers);return output.toByteArray();
    }
    public static void main(String[] args) throws Exception {
        EdenProfiles.Servers defaults=EdenProfiles.servers(EdenProfiles.DEFAULT_SERVERS);
        check(defaults.clubIndex==1,"default Club server is second");
        for(int slot=0;slot<2;slot++){
            String golden=new String(Files.readAllBytes(Paths.get("tests/fixtures/eden/p"+(slot+1)+"-verified.ini")),StandardCharsets.UTF_8);
            check(EdenProfiles.profile(slot,defaults).equals(golden),"preserve verified P"+(slot+1)+" file byte-for-byte");
        }
        for(int index:new int[]{0,1,3,7}){
            StringBuilder servers=new StringBuilder();
            for(int i=0;i<index;i++)servers.append("127.0.0.1:").append(26760+i).append(',');
            servers.append(EdenProfiles.CLUB_SERVER);
            EdenProfiles.Servers parsed=EdenProfiles.servers(servers.toString());
            for(int slot=0;slot<4;slot++){
                String profile=EdenProfiles.profile(slot,parsed);
                Matcher pads=Pattern.compile("pad:([0-9]+)").matcher(profile);int count=0;
                while(pads.find()){check(Integer.parseInt(pads.group(1))==index*4+slot,"all fields use correct pad");count++;}
                check(count==20,"16 buttons, 2 sticks, 2 motion fields");
                check(!profile.contains("engine:android"),"no unstable Android GUID/port bindings");
                check(profile.contains("button_a=\"engine:cemuhookudp,guid:"+EdenProfiles.GUID+",port:37821,pad:"+(index*4+slot)+",button:16384\""),"Xbox-position A");
            }
        }
        check(EdenProfiles.servers(" \"127.0.0.1:26760, 127.0.0.1:37821\" ").list.equals(EdenProfiles.DEFAULT_SERVERS),"pasted INI list accepted");
        for(String bad:new String[]{"","127.0.0.1:26760","localhost:37821","256.0.0.1:37821","127.0.0.1:65536","127.0.0.1:0","127.0.0.1:37821,","127.0.0.1:37821,127.0.0.1:37821","127.0.0.1:37821,127.0.0.1:1,127.0.0.1:2,127.0.0.1:3,127.0.0.1:4,127.0.0.1:5,127.0.0.1:6,127.0.0.1:7,127.0.0.1:8"})bad(bad);
        byte[] zip=archive(defaults);check(Arrays.equals(zip,archive(defaults)),"reproducible ZIP");
        Map<String,String> entries=new LinkedHashMap<>();
        try(ZipInputStream input=new ZipInputStream(new ByteArrayInputStream(zip))){
            for(ZipEntry entry;(entry=input.getNextEntry())!=null;){
                ByteArrayOutputStream content=new ByteArrayOutputStream();byte[] buffer=new byte[1024];
                for(int n;(n=input.read(buffer))!=-1;)content.write(buffer,0,n);
                entries.put(entry.getName(),new String(content.toByteArray(),StandardCharsets.UTF_8));
            }
        }
        check(entries.size()==5,"four profiles plus instructions");
        for(int slot=0;slot<4;slot++)check(entries.get(EdenProfiles.name(slot)+".ini").equals(EdenProfiles.profile(slot,defaults)),"ZIP profile contents");
        check(entries.get("README.txt").contains(EdenProfiles.DEFAULT_SERVERS),"instructions include exact server order");
        check(!entries.containsKey("config.ini"),"never exports a replacement global configuration");
        System.out.println("Verified Eden P1/P2 parity; four-player server ordering, Xbox-position controls, ZIP export and invalid lists: pass");
    }
}
