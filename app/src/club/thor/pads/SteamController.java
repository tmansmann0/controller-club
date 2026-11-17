package club.thor.pads;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.*;
import android.bluetooth.le.*;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.SystemClock;
import java.util.Collections;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Valve's original Steam Controller BLE service. All state is owned by the bridge worker. */
@SuppressLint("MissingPermission")
final class SteamController {
    private static final UUID SERVICE = uuid("32"), INPUT = uuid("33"), REPORT = uuid("34");
    private static final UUID CCCD = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
    private static final byte[] ENTER_VALVE_MODE = {(byte)0xc0,(byte)0x87,3,8,7,0};
    private static final byte[] DISABLE_DIGITAL = command(0x81, 0);
    private static final byte[] DISABLE_TRACKPADS = command(0x87, 9, 49, 2, 0, 7, 7, 0, 8, 7, 0);
    private static final byte[] ENABLE_MOTION = command(0x87,3,48,24,0);
    private static final byte[] DISABLE_MOTION = command(0x87,3,48,0,0);
    private static final byte[] RESTORE_DIGITAL = command(0x85, 0);
    private static final byte[] RESTORE_TRACKPADS = command(0x8e, 0);
    private final Bridge bridge;
    private final Handler worker;
    private final BluetoothManager manager;
    private final BluetoothAdapter adapter;
    private BluetoothLeScanner scanner;
    private BluetoothDevice bonding;
    private boolean nearby, watching, registered;
    private long scanRetryAt, bondRetryAt;
    private String lastBondAttempt;
    private final ArrayDeque<byte[]> commands = new ArrayDeque<>();
    private BluetoothGatt gatt;
    private BluetoothGattCharacteristic report;
    private String identity, previousInput;
    private SteamReport decoder = new SteamReport();
    private boolean subscribed, wanted, mode, writing, closing, motionReceived;
    private long retryAt;
    private String lastAttempt;
    private String preferredAddress;
    private final Runnable timeout = () -> reset(true);

    SteamController(Bridge bridge, Handler worker) {
        this.bridge=bridge;
        this.worker=worker;
        manager=bridge.getSystemService(BluetoothManager.class);
        adapter=manager==null?null:manager.getAdapter();
        preferredAddress=bridge.getSharedPreferences("steam",0).getString("lastConnectedAddress","");
    }

    static boolean matches(String name) {
        if (name == null) return false;
        String normalized = name.toLowerCase(Locale.ROOT).replace(" ", "");
        return normalized.equals("steamcontroller");
    }

    void setNearby(boolean open) {
        nearby=open;
        if(open){
            lastBondAttempt=null;
            bondRetryAt=0;
        }
        tick();
    }

    void tick() {
        if (closing || adapter==null || !permitted(Manifest.permission.BLUETOOTH_CONNECT)) return;
        if(!registered){
            IntentFilter events=new IntentFilter(BluetoothDevice.ACTION_ACL_CONNECTED);
            events.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
            if(Build.VERSION.SDK_INT>=33)bridge.registerReceiver(bluetoothEvents,events,null,worker,Context.RECEIVER_EXPORTED);
            else bridge.registerReceiver(bluetoothEvents,events,null,worker);
            registered=true;
        }
        if(!adapter.isEnabled()){
            stopScan();
            bonding=null;
            return;
        }
        if(subscribed)return;
        if(bonding!=null&&bonding.getBondState()!=BluetoothDevice.BOND_BONDING
                &&SystemClock.elapsedRealtime()>=bondRetryAt)bonding=null;

        List<BluetoothDevice> candidates=new ArrayList<>();
        for(BluetoothDevice device:adapter.getBondedDevices())
            if(matches(device.getName()))candidates.add(device);
        if(nearby||!preferredAddress.isEmpty()||!candidates.isEmpty())startScan();
        else if(watching)stopScan();
        if(gatt!=null||bonding!=null||SystemClock.elapsedRealtime()<retryAt)return;

        List<BluetoothDevice> connected=manager.getConnectedDevices(BluetoothProfile.GATT);
        candidates.sort(Comparator.comparing((BluetoothDevice device)->!connected.contains(device))
            .thenComparing(device->!device.getAddress().equals(preferredAddress))
            .thenComparing(BluetoothDevice::getAddress));
        if(candidates.isEmpty())return;
        int index=0;
        for(int i=0;i<candidates.size();i++)
            if(candidates.get(i).getAddress().equals(lastAttempt)){index=(i+1)%candidates.size();break;}
        connect(candidates.get(index));
    }

    private boolean permitted(String permission){
        return Build.VERSION.SDK_INT<31||bridge.checkSelfPermission(permission)==PackageManager.PERMISSION_GRANTED;
    }

    private void connect(BluetoothDevice device){
        if(closing||subscribed||device.getBondState()!=BluetoothDevice.BOND_BONDED)return;
        if(gatt!=null){
            if(gatt.getDevice().equals(device))return;
            reset(false);
        }
        lastAttempt=device.getAddress();
        identity=hashAddress(lastAttempt);
        retryAt=SystemClock.elapsedRealtime()+5000;
        gatt=device.connectGatt(bridge,false,callback,BluetoothDevice.TRANSPORT_LE);
        if(gatt!=null)worker.postDelayed(timeout,7000);
    }

    private void startScan(){
        if(watching||!permitted(Manifest.permission.BLUETOOTH_SCAN)
                ||SystemClock.elapsedRealtime()<scanRetryAt)return;
        scanner=adapter.getBluetoothLeScanner();
        if(scanner==null)return;
        ScanFilter name=new ScanFilter.Builder().setDeviceName("SteamController").build();
        ScanSettings settings=new ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_POWER).build();
        try{
            scanner.startScan(Collections.singletonList(name),settings,advertisements);
            watching=true;
        }catch(RuntimeException e){
            scanner=null;
            scanRetryAt=SystemClock.elapsedRealtime()+30000;
        }
    }

    private void stopScan(){
        if(scanner!=null)try{scanner.stopScan(advertisements);}catch(RuntimeException ignored){}
        scanner=null;
        watching=false;
        scanRetryAt=SystemClock.elapsedRealtime()+2000;
    }

    private final ScanCallback advertisements=new ScanCallback(){
        @Override public void onScanResult(int type,ScanResult result){
            ScanRecord record=result.getScanRecord();
            if(record==null||!matches(record.getDeviceName())||!result.isConnectable())return;
            worker.post(()->{
                if(!watching||closing||subscribed||!permitted(Manifest.permission.BLUETOOTH_CONNECT))return;
                controllerSeen(result.getDevice());
            });
        }

        @Override public void onScanFailed(int errorCode){
            worker.post(()->{
                if(!watching)return;
                watching=false;
                scanner=null;
                scanRetryAt=SystemClock.elapsedRealtime()+30000;
            });
        }
    };

    void controllerSeen(BluetoothDevice device){
        if(closing||subscribed||!permitted(Manifest.permission.BLUETOOTH_CONNECT))return;
        int state=device.getBondState();
        if(state==BluetoothDevice.BOND_BONDED)connect(device);
        else if(state==BluetoothDevice.BOND_NONE&&(nearby||!preferredAddress.isEmpty()))pair(device,false);
    }

    void pair(BluetoothDevice device,boolean requested){
        if(closing||subscribed||bonding!=null||!permitted(Manifest.permission.BLUETOOTH_CONNECT)
                ||device.getBondState()!=BluetoothDevice.BOND_NONE)return;
        long now=SystemClock.elapsedRealtime();
        if(!requested&&(now<bondRetryAt||device.getAddress().equals(lastBondAttempt)))return;
        if(gatt!=null)reset(false);
        lastBondAttempt=device.getAddress();
        bondRetryAt=now+30000;
        if(device.createBond())bonding=device;
    }

    private final BroadcastReceiver bluetoothEvents=new BroadcastReceiver(){
        @Override public void onReceive(Context context,Intent intent){
            if(closing||!permitted(Manifest.permission.BLUETOOTH_CONNECT))return;
            BluetoothDevice device=intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
            if(device==null||(!device.equals(bonding)&&!matches(device.getName())))return;
            if(BluetoothDevice.ACTION_BOND_STATE_CHANGED.equals(intent.getAction())){
                int state=intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE,BluetoothDevice.ERROR);
                if(state==BluetoothDevice.BOND_BONDING){bonding=device;return;}
                if(device.equals(bonding))bonding=null;
                if(state==BluetoothDevice.BOND_BONDED)connect(device);
            }else if(device.getBondState()==BluetoothDevice.BOND_BONDED){
                connect(device);
            }
        }
    };

    void announce() { if (subscribed) bridge.sendSteam("STEAM ADD " + identity); }

    void setRouting(boolean on) {
        wanted = on;
        configure();
    }

    private void configure() {
        if (!subscribed || writing || !commands.isEmpty() || mode == wanted) return;
        mode = wanted;
        previousInput = null;
        motionReceived=false;
        decoder = new SteamReport();
        if(mode)commands.add(ENTER_VALVE_MODE);
        commands.add(mode ? DISABLE_DIGITAL : RESTORE_DIGITAL);
        commands.add(mode ? DISABLE_TRACKPADS : RESTORE_TRACKPADS);
        commands.add(mode ? ENABLE_MOTION : DISABLE_MOTION);
        writeNext();
    }

    private void writeNext() {
        if (gatt == null) return;
        byte[] next = commands.poll();
        if (next == null) {
            android.util.Log.i("ClubSteam",mode?"Gamepad mode configured":"Desktop mode restored");
            if (closing && !mode) reset(false);
            else configure();
            return;
        }
        report.setValue(next);
        writing = true;
        if (!gatt.writeCharacteristic(report)) { reset(true); return; }
        worker.removeCallbacks(timeout);
        worker.postDelayed(timeout, 2500);
    }

    void close() {
        closing = true;
        stopScan();
        if(registered)bridge.unregisterReceiver(bluetoothEvents);
        if (!subscribed || (!mode && !writing)) reset(false);
        else setRouting(false);
    }

    private void reset(boolean retry) {
        worker.removeCallbacks(timeout);
        if (subscribed) bridge.sendSteam("STEAM REMOVE");
        if (gatt != null) {
            gatt.disconnect();
            gatt.close();
        }
        gatt=null;
        report=null;
        subscribed=writing=mode=false;
        commands.clear();
        previousInput=null;
        if (retry) retryAt=android.os.SystemClock.elapsedRealtime()+3500;
        if (closing) worker.getLooper().quitSafely();
    }

    private final BluetoothGattCallback callback = new BluetoothGattCallback() {
        @Override public void onConnectionStateChange(BluetoothGatt connection,int status,int state) {
            worker.post(() -> {
                if (connection != gatt) return;
                if (status == BluetoothGatt.GATT_SUCCESS && state == BluetoothProfile.STATE_CONNECTED && connection.discoverServices()) return;
                reset(true);
            });
        }
        @Override public void onServicesDiscovered(BluetoothGatt connection,int status) {
            worker.post(() -> {
                if (connection != gatt) return;
                BluetoothGattService service = connection.getService(SERVICE);
                BluetoothGattCharacteristic input = service == null ? null : service.getCharacteristic(INPUT);
                report = service == null ? null : service.getCharacteristic(REPORT);
                BluetoothGattDescriptor desc = input == null ? null : input.getDescriptor(CCCD);
                if (status != BluetoothGatt.GATT_SUCCESS || report == null || desc == null
                        || !connection.setCharacteristicNotification(input,true)) { reset(true); return; }
                desc.setValue((input.getProperties() & BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0
                        ? BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE : BluetoothGattDescriptor.ENABLE_INDICATION_VALUE);
                if (!connection.writeDescriptor(desc)) reset(true);
            });
        }
        @Override public void onDescriptorWrite(BluetoothGatt connection,BluetoothGattDescriptor descriptor,int status) {
            worker.post(() -> {
                if (connection != gatt || !descriptor.getCharacteristic().getUuid().equals(INPUT)) return;
                worker.removeCallbacks(timeout);
                if (status != BluetoothGatt.GATT_SUCCESS) { reset(true); return; }
                subscribed = true;
                lastAttempt=null;
                lastBondAttempt=null;
                stopScan();
                preferredAddress=connection.getDevice().getAddress();
                bridge.getSharedPreferences("steam",0).edit().putString("lastConnectedAddress",preferredAddress).apply();
                android.util.Log.i("ClubSteam","Original Steam Controller service subscribed");
                announce();
                configure();
            });
        }
        @Override public void onCharacteristicWrite(BluetoothGatt connection,BluetoothGattCharacteristic characteristic,int status) {
            worker.post(() -> {
                if (connection != gatt || !writing || !REPORT.equals(characteristic.getUuid())) return;
                worker.removeCallbacks(timeout);
                writing=false;
                if (status != BluetoothGatt.GATT_SUCCESS) { reset(true); return; }
                writeNext();
            });
        }
        @Override public void onCharacteristicChanged(BluetoothGatt connection,BluetoothGattCharacteristic characteristic) {
            byte[] value=characteristic.getValue();
            if (value != null) input(connection,characteristic,value.clone());
        }
        @Override public void onCharacteristicChanged(BluetoothGatt connection,BluetoothGattCharacteristic characteristic,byte[] value) {
            input(connection,characteristic,value.clone());
        }
    };

    private void input(BluetoothGatt connection,BluetoothGattCharacteristic characteristic,byte[] value) {
        if (!INPUT.equals(characteristic.getUuid())) return;
        worker.post(() -> {
            if (connection != gatt || !mode || writing || !wanted) return;
            String next = decoder.accept(value);
            if (next != null && !next.equals(previousInput)) {
                if(previousInput==null)android.util.Log.i("ClubSteam","Valve gamepad reports received");
                previousInput=next;
                bridge.sendSteam(next);
            }
            String motion=decoder.motion(SystemClock.elapsedRealtimeNanos()/1000);
            if(motion!=null){
                if(!motionReceived)android.util.Log.i("ClubMotion","Steam Controller gyro and accelerometer reports received");
                motionReceived=true;
                bridge.sendSteam(motion);
            }
        });
    }

    private static UUID uuid(String part) {
        return UUID.fromString("100f6c"+part+"-1735-4313-b402-38567131e5f3");
    }

    private static byte[] command(int... values) {
        byte[] packet=new byte[19];
        packet[0]=(byte)0xc0;
        for(int i=0;i<values.length;i++) packet[i+1]=(byte)values[i];
        return packet;
    }

    static String hashAddress(String address) {
        long hash=0xcbf29ce484222325L;
        for (char c:address.toLowerCase(Locale.ROOT).toCharArray()) hash=(hash^c)*0x100000001b3L;
        return String.format(Locale.ROOT,"%016x",hash);
    }
}
