package club.thor.pads;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.hardware.display.DisplayManager;
import android.os.Build;
import android.os.Handler;
import android.view.Display;
import android.view.InputDevice;
import android.view.Surface;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Reads only the sensors belonging to the controller assigned to each source. */
final class ControllerMotion {
    private final Bridge bridge;
    private final Handler worker;
    private final Map<Integer,Source> sources=new HashMap<>();

    ControllerMotion(Bridge bridge,Handler worker){this.bridge=bridge;this.worker=worker;}

    void sync(ArrayList<Bridge.Pad> pads,Set<Integer> wanted){
        Set<Integer> remaining=new HashSet<>(sources.keySet());
        for(Bridge.Pad pad:pads){
            if(!wanted.contains(pad.id)||pad.id==Bridge.STEAM_PAD_ID)continue;
            remaining.remove(pad.id);
            if(sources.containsKey(pad.id))continue;
            SensorManager manager=manager(pad);
            if(manager==null)continue;
            Sensor accel=manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            Sensor gyro=manager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
            if(accel==null||gyro==null)continue;
            Source source=new Source(pad.id,pad.builtin,manager);
            boolean registered=manager.registerListener(source,accel,10000,worker)
                &&manager.registerListener(source,gyro,10000,worker);
            if(!registered){manager.unregisterListener(source);continue;}
            sources.put(pad.id,source);
            android.util.Log.i("ClubMotion",pad.name+" gyro and accelerometer enabled");
        }
        for(int id:remaining){Source source=sources.remove(id);source.manager.unregisterListener(source);}
    }

    private SensorManager manager(Bridge.Pad pad){
        if(pad.builtin)return bridge.getSystemService(SensorManager.class);
        if(Build.VERSION.SDK_INT<31)return null;
        InputDevice matching=null;
        for(int id:InputDevice.getDeviceIds()){
            InputDevice device=InputDevice.getDevice(id);
            if(device==null||!device.getName().equals(pad.name)
                ||!device.supportsSource(InputDevice.SOURCE_JOYSTICK))continue;
            if(matching!=null)return null;
            matching=device;
        }
        return matching==null?null:matching.getSensorManager();
    }

    void close(){
        for(Source source:sources.values())source.manager.unregisterListener(source);
        sources.clear();
    }

    private final class Source implements SensorEventListener {
        final int id;
        final boolean builtin;
        final SensorManager manager;
        final float[] acceleration=new float[3], gyroscope=new float[3];
        boolean haveAcceleration,haveGyroscope;
        long lastSent;
        Source(int id,boolean builtin,SensorManager manager){this.id=id;this.builtin=builtin;this.manager=manager;}

        @Override public void onSensorChanged(SensorEvent event){
            if(bridge.closed||bridge.socket==null)return;
            float[] target=event.sensor.getType()==Sensor.TYPE_ACCELEROMETER?acceleration:gyroscope;
            float scale=target==acceleration?1.0f/SensorManager.GRAVITY_EARTH:(float)(180.0/Math.PI);
            for(int i=0;i<3;i++)target[i]=event.values[i]*scale;
            if(builtin){
                Display display=bridge.getSystemService(DisplayManager.class).getDisplay(Display.DEFAULT_DISPLAY);
                int rotation=display==null?Surface.ROTATION_0:display.getRotation();
                float x=target[0],y=target[1];
                switch(rotation){
                    case Surface.ROTATION_90: target[0]=-y;target[1]=x;break;
                    case Surface.ROTATION_180: target[0]=-x;target[1]=-y;break;
                    case Surface.ROTATION_270: target[0]=y;target[1]=-x;break;
                    default: break;
                }
            }
            if(target==acceleration)haveAcceleration=true;else haveGyroscope=true;
            if(!haveAcceleration||!haveGyroscope||event.timestamp-lastSent<10000000)return;
            lastSent=event.timestamp;
            bridge.sendSteam("MOTION "+id+" "+event.timestamp/1000+" "+(-acceleration[0])+" "+(-acceleration[2])+" "
                +acceleration[1]+" "+gyroscope[0]+" "+(-gyroscope[2])+" "+gyroscope[1]);
        }
        @Override public void onAccuracyChanged(Sensor sensor,int accuracy){}
    }
}
