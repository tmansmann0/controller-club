package club.thor.pads;

import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.BatteryState;
import android.os.BatteryManager;
import android.os.Build;
import android.os.SystemClock;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.InputDevice;
import java.util.*;

/** Physical-controller feedback only: never vibrate a virtual Club player recursively. */
final class ControllerFeedback {
    private final Bridge bridge;
    private final Map<Integer,Source> sources=new HashMap<>();
    private volatile Map<Integer,String> battery=Collections.emptyMap();
    private volatile Set<Integer> capable=Collections.emptySet();
    private long lastBattery;
    private final Map<Integer,Long> testing=new HashMap<>();
    private final Map<Integer,Vibrator> testVibrators=new HashMap<>();
    private static final class Source {
        final int device,slot; final Vibrator vibrator;
        int lastLevel;
        Source(int device,int slot,Vibrator vibrator){this.device=device;this.slot=slot;this.vibrator=vibrator;}
    }
    ControllerFeedback(Bridge bridge){this.bridge=bridge;}
    String battery(int id){String value=battery.get(id);return value==null?"Battery unavailable":value;}
    boolean canRumble(int id){return capable.contains(id);}

    static InputDevice device(Bridge.Pad pad,List<Bridge.Pad> roster){
        // Linux event identities and Android IDs differ. Never guess between twins.
        int count=0;
        for(Bridge.Pad other:roster)if(other.name.equals(pad.name))count++;
        if(count!=1)return null;
        InputDevice match=null;
        for(int id:InputDevice.getDeviceIds()){
            InputDevice d=InputDevice.getDevice(id);
            if(d==null||d.isVirtual()||d.getName().startsWith("Controller Club")
                    ||d.getName().startsWith("Xbox Controller P")||!d.getName().equals(pad.name))continue;
            if(!d.supportsSource(InputDevice.SOURCE_GAMEPAD)&&!d.supportsSource(InputDevice.SOURCE_JOYSTICK))continue;
            if(match!=null)return null;
            match=d;
        }
        return match;
    }
    void sync(ArrayList<Bridge.Pad> roster,Map<Integer,Integer> slots,boolean active){
        Map<Integer,Source> next=new HashMap<>();
        Set<Integer> supported=new HashSet<>();
        boolean refresh=SystemClock.elapsedRealtime()-lastBattery>=10000||!battery.keySet().equals(slots.keySet());
        Map<Integer,String> readings=refresh?new HashMap<>():new HashMap<>(battery);
        for(Bridge.Pad pad:roster){
            InputDevice d=device(pad,roster);
            Vibrator vibrator=null;
            try{
                if(d!=null){
                    vibrator=Build.VERSION.SDK_INT>=31?d.getVibratorManager().getDefaultVibrator():d.getVibrator();
                    if(!vibrator.hasVibrator())vibrator=null;
                }
                if(refresh)readings.put(pad.id,readBattery(pad,d));
            }catch(RuntimeException ignored){if(refresh)readings.put(pad.id,"Battery unavailable");}
            if(vibrator!=null){
                supported.add(pad.id);
                int slot=slots.containsKey(pad.id)?slots.get(pad.id):-1;
                if(active&&slot>=0){
                    Source old=sources.get(pad.id);
                    next.put(pad.id,old!=null&&old.device==d.getId()&&old.slot==slot?old:new Source(d.getId(),slot,vibrator));
                }
            }
        }
        for(Map.Entry<Integer,Source> old:sources.entrySet()){
            Source replacement=next.get(old.getKey());
            if(replacement==null||replacement.device!=old.getValue().device||replacement.slot!=old.getValue().slot)
                cancel(old.getValue());
        }
        sources.clear();sources.putAll(next);
        boolean changed=!capable.equals(supported)||refresh&&!battery.equals(readings);
        capable=Collections.unmodifiableSet(supported);
        if(refresh){battery=Collections.unmodifiableMap(readings);lastBattery=SystemClock.elapsedRealtime();}
        if(changed)Bridge.MAIN.post(Bridge::changed);
    }
    private String readBattery(Bridge.Pad pad,InputDevice device){
        int percent=-1,status=BatteryManager.BATTERY_STATUS_UNKNOWN;
        if(pad.builtin){
            Intent b=bridge.registerReceiver(null,new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            if(b!=null){
                int level=b.getIntExtra(BatteryManager.EXTRA_LEVEL,-1),scale=b.getIntExtra(BatteryManager.EXTRA_SCALE,-1);
                if(level>=0&&scale>0&&level<=scale)percent=Math.round(level*100f/scale);
                status=b.getIntExtra(BatteryManager.EXTRA_STATUS,BatteryManager.BATTERY_STATUS_UNKNOWN);
            }
        }else if(device!=null&&Build.VERSION.SDK_INT>=31){
            BatteryState b=device.getBatteryState();
            if(b.isPresent()){
                float capacity=b.getCapacity();
                if(!Float.isNaN(capacity)&&capacity>=0&&capacity<=1)percent=Math.round(capacity*100);
                status=b.getStatus();
            }
        }
        return BatteryReading.label(percent,status);
    }
    void play(int[] levels){
        for(Map.Entry<Integer,Source> entry:sources.entrySet()){
            Source source=entry.getValue();
            Long until=testing.get(entry.getKey());
            if(until!=null&&SystemClock.elapsedRealtime()<until)continue;
            int level=levels[source.slot];
            try{
                if(level==0){if(source.lastLevel!=0)source.vibrator.cancel();}
                else source.vibrator.vibrate(VibrationEffect.createOneShot(120,Math.max(1,Math.min(255,(level+128)/257))));
            }catch(RuntimeException ignored){}
            source.lastLevel=level;
        }
    }
    void test(int id,ArrayList<Bridge.Pad> roster){
        for(Bridge.Pad pad:roster)if(pad.id==id){
            InputDevice d=device(pad,roster);if(d==null)return;
            try{Vibrator v=Build.VERSION.SDK_INT>=31?d.getVibratorManager().getDefaultVibrator():d.getVibrator();
                if(v.hasVibrator()){
                    testing.put(id,SystemClock.elapsedRealtime()+250);testVibrators.put(id,v);
                    v.vibrate(VibrationEffect.createOneShot(250,150));
                }
            }catch(RuntimeException ignored){}
        }
    }
    private void cancel(Source source){try{source.vibrator.cancel();}catch(RuntimeException ignored){}}
    void stop(){
        for(Source source:sources.values())cancel(source);
        for(Vibrator vibrator:testVibrators.values())try{vibrator.cancel();}catch(RuntimeException ignored){}
        sources.clear();testing.clear();testVibrators.clear();
    }
}
