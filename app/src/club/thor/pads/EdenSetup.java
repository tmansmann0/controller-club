package club.thor.pads;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.util.AtomicFile;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.json.JSONException;
import org.json.JSONObject;

/** Installs Eden's global bindings for the built-in Thor and Club's loopback controller slots. */
final class EdenSetup {
    static final String PACKAGE="dev.eden.eden_emulator";
    static final String AUTHORITY=PACKAGE+".user";
    private static final String LOOPBACK="127.0.0.1:37821";
    private static final String LOOPBACK_GUID="0000000000000000000000007f000001";
    private static final String THOR_GUID="00000000000001110000000000002020";
    private static final Pattern CONTROLS=Pattern.compile("(?m)^\\[Controls][ \\t]*\\r?\\n");
    private static final Pattern NEXT_SECTION=Pattern.compile("(?m)^\\[");
    private final AtomicFile backup;
    private final ContentResolver resolver;
    private final Uri config;

    EdenSetup(Context context,Uri tree){
        backup=new AtomicFile(new File(context.getFilesDir(),"eden-player-one.original.json"));
        resolver=context.getContentResolver();
        config=DocumentsContract.buildDocumentUriUsingTree(tree,"root/config/config.ini");
    }

    static boolean isEdenRoot(Uri tree){
        return AUTHORITY.equals(tree.getAuthority())&&DocumentsContract.isTreeUri(tree)
            &&DocumentsContract.getTreeDocumentId(tree).matches("root/?");
    }

    boolean update(boolean restore) throws IOException,JSONException {
        JSONObject original;
        JSONObject applied;
        try{
            JSONObject journal=new JSONObject(new String(backup.readFully(),StandardCharsets.UTF_8));
            original=journal.optJSONObject("original");
            applied=journal.optJSONObject("applied");
            if(original==null||applied==null){
                original=journal;
                applied=new JSONObject(thorMapping());
            }
        }catch(FileNotFoundException missing){
            original=new JSONObject();
            applied=new JSONObject();
        }
        if(restore&&original.length()==0)return false;
        String before=read();
        int[] section=section(before);
        String controls=before.substring(section[0],section[1]);
        String next=controls;
        if(restore){
            for(java.util.Iterator<String> keys=original.keys();keys.hasNext();){
                String key=keys.next();
                if(applied.has(key)&&Objects.equals(value(controls,key),applied.getString(key)))
                    next=setValue(next,key,original.isNull(key)?null:original.getString(key));
            }
        }else{
            for(Map.Entry<String,String> entry:configuredMapping(controls).entrySet()){
                String key=entry.getKey(), value=entry.getValue();
                String current=value(controls,key);
                if(Objects.equals(current,value))continue;
                if(!applied.has(key)||!Objects.equals(current,applied.getString(key)))
                    original.put(key,current==null?JSONObject.NULL:current);
                applied.put(key,value);
                next=setValue(next,key,value);
            }
        }
        if(next.equals(controls)){
            if(restore)backup.delete();
            return false;
        }
        if(!restore){
            FileOutputStream file=backup.startWrite();
            try{
                file.write(new JSONObject().put("original",original).put("applied",applied).toString().getBytes(StandardCharsets.UTF_8));
                backup.finishWrite(file);
            }catch(IOException e){backup.failWrite(file);throw e;}
        }
        if(!before.equals(read()))throw new IOException("Eden changed its settings. Close Eden and try again.");
        try{
            write(before.substring(0,section[0])+next+before.substring(section[1]));
        }catch(IOException failure){
            try{write(before);}catch(IOException rollback){failure.addSuppressed(rollback);}
            throw failure;
        }
        if(restore)backup.delete();
        return true;
    }

    private String read() throws IOException {
        try(InputStream input=resolver.openInputStream(config)){
            if(input==null)throw new IOException("Eden’s settings are unavailable.");
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();
            byte[] buffer=new byte[8192];
            for(int n;(n=input.read(buffer))!=-1;){
                if(bytes.size()+n>1024*1024)throw new IOException("Eden’s settings file is too large.");
                bytes.write(buffer,0,n);
            }
            return new String(bytes.toByteArray(),StandardCharsets.UTF_8);
        }
    }

    private void write(String text) throws IOException {
        try(OutputStream output=resolver.openOutputStream(config,"wt")){
            if(output==null)throw new IOException("Eden’s settings could not be saved.");
            output.write(text.getBytes(StandardCharsets.UTF_8));
        }
    }

    static Map<String,String> configuredMapping(String controls) throws IOException {
        Map<String,String> fields=thorMapping();
        String current=value(controls,"udp_input_servers");
        if(current==null||"true".equals(value(controls,"udp_input_servers\\default")))current="127.0.0.1:26760";
        current=current.replaceAll("^\"|\"$","").trim();
        java.util.LinkedHashSet<String> servers=new java.util.LinkedHashSet<>();
        if(!current.isEmpty()){
            for(String server:current.split(",")){
                String endpoint=server.trim();
                if(!endpoint.matches("[0-9]+(\\.[0-9]+){3}:[0-9]+"))
                    throw new IOException("Eden uses a custom UDP server list that Club could not read.");
                servers.add(endpoint);
            }
        }
        servers.add(LOOPBACK);
        if(servers.size()>8)throw new IOException("Eden's UDP server list is full.");
        int serverIndex=new java.util.ArrayList<>(servers).indexOf(LOOPBACK);
        fields.put("udp_input_servers\\default","false");
        fields.put("udp_input_servers","\""+String.join(",",servers)+"\"");
        fields.put("enable_udp_controller\\default","false");
        fields.put("enable_udp_controller","true");

        String[] buttons={"a","b","x","y","lstick","rstick","l","r","zl","zr","plus","minus",
            "dleft","dup","dright","ddown","slleft","srleft","slright","srright","home","screenshot"};
        int[] codes={0x2000,0x4000,0x1000,0x8000,2,4,0x400,0x800,0x100,0x200,8,1,
            0x80,0x10,0x20,0x40,0x100,0x200,0x100,0x200,0x40000,0x80000};
        for(int slot=1;slot<4;slot++){
            String prefix="player_"+slot+"_";
            fields.put(prefix+"type\\default","false");
            fields.put(prefix+"type","0");
            fields.put(prefix+"profile_name\\default","true");
            fields.put(prefix+"profile_name","");
            fields.put(prefix+"connected\\default","false");
            fields.put(prefix+"connected","true");
            String input="\"engine:cemuhookudp,guid:"+LOOPBACK_GUID+",port:37821,pad:"+(4*serverIndex+slot)+",";
            for(int i=0;i<buttons.length;i++)
                udpBinding(fields,prefix+"button_"+buttons[i],input+"button:"+codes[i]+"\"");
            udpBinding(fields,prefix+"lstick",input+"axis_x:0,axis_y:1\"");
            udpBinding(fields,prefix+"rstick",input+"axis_x:2,axis_y:3\"");
        }
        return fields;
    }

    private static void udpBinding(Map<String,String> fields,String key,String value){
        fields.put(key+"\\default","false");
        fields.put(key,value);
    }

    static Map<String,String> thorMapping(){
        Map<String,String> fields=new LinkedHashMap<>();
        setting(fields,"profile_name","",true);
        setting(fields,"connected","true",false);
        String[] buttons={"a","b","x","y","lstick","rstick","l","r","zl","zr","plus","minus"};
        int[] keys={96,97,99,100,106,107,102,103,104,105,108,109};
        for(int i=0;i<buttons.length;i++)binding(fields,"button_"+buttons[i],"button:"+keys[i]);
        binding(fields,"button_dleft","axis:15,threshold:0.5,invert:-");
        binding(fields,"button_dup","axis:16,threshold:0.5,invert:-");
        binding(fields,"button_dright","axis:15,threshold:0.5,invert:+");
        binding(fields,"button_ddown","axis:16,threshold:0.5,invert:+");
        binding(fields,"lstick","axis_x:0,axis_y:1,offset_x:0,offset_y:0,invert_x:+,invert_y:-");
        binding(fields,"rstick","axis_x:11,axis_y:14,offset_x:0,offset_y:0,invert_x:+,invert_y:-");
        return fields;
    }

    private static void binding(Map<String,String> fields,String key,String binding){
        setting(fields,key,"\"engine:android,port:0,guid:"+THOR_GUID+","+binding+",display:Odin Controller 0\"",false);
    }

    private static void setting(Map<String,String> fields,String key,String value,boolean useDefault){
        fields.put("player_0_"+key+"\\default",Boolean.toString(useDefault));
        fields.put("player_0_"+key,value);
    }

    static int[] section(String text) throws IOException {
        Matcher controls=CONTROLS.matcher(text);
        if(!controls.find())throw new IOException("Open Eden once to create its Controls settings.");
        Matcher next=NEXT_SECTION.matcher(text);
        return new int[]{controls.end(),next.find(controls.end())?next.start():text.length()};
    }

    private static Matcher line(String text,String key){
        return Pattern.compile("(?m)^"+Pattern.quote(key)+"=([^\\r\\n]*)(?:\\r?\\n|$)").matcher(text);
    }

    private static String value(String text,String key){
        Matcher line=line(text,key);
        return line.find()?line.group(1):null;
    }

    static String setValue(String text,String key,String value){
        Matcher line=line(text,key);
        String newline=text.contains("\r\n")?"\r\n":"\n";
        String replacement=value==null?"":key+"="+value+newline;
        if(line.find())return text.substring(0,line.start())+replacement+text.substring(line.end());
        if(value==null)return text;
        return text+(text.isEmpty()||text.endsWith("\n")?"":newline)+replacement;
    }
}
