package club.thor.pads;

import android.os.Build;
import android.os.IBinder;
import android.os.Parcel;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/** Uses the same firmware permission service as the Thor's built-in settings app. */
final class ThorAccess {
    private ThorAccess() {}

    static boolean restore() {
        if(!Build.MODEL.toLowerCase(java.util.Locale.ROOT).contains("thor"))return false;
        File[] nodes=new File("/sys/class/input").listFiles();
        if(nodes==null)return false;
        for(File node:nodes){
            String name=node.getName();
            if(!name.matches("event[0-9]+"))continue;
            try{
                String deviceName=new String(Files.readAllBytes(new File(node,"device/name").toPath()),StandardCharsets.UTF_8).trim();
                if(!"Odin Controller".equals(deviceName))continue;
                File device=new File("/dev/input",name);
                if(device.canRead()&&!device.canWrite())return true;
                IBinder service=(IBinder)Class.forName("android.os.ServiceManager")
                    .getMethod("getService",String.class).invoke(null,"PServerBinder");
                if(service==null)return false;
                Parcel request=Parcel.obtain(), reply=Parcel.obtain();
                try{
                    // Event names are strictly validated above; no user supplied shell content.
                    request.writeStringArray(new String[]{"chmod 664 /dev/input/"+name,"0"});
                    if(!service.transact(0,request,reply,0))return false;
                    reply.createByteArray();
                }finally{
                    request.recycle();
                    reply.recycle();
                }
                return device.canRead();
            }catch(Exception e){
                android.util.Log.w("ClubAccess","Thor input permission is unavailable",e);
                // Nodes can disappear or be unreadable during boot. Keep looking.
            }
        }
        return false;
    }
}
