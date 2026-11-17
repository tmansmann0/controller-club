package club.thor.pads;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.bluetooth.*;
import android.bluetooth.le.*;
import android.content.*;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.content.pm.PackageManager;
import android.os.*;
import android.text.TextUtils;
import android.view.*;
import android.widget.*;
import java.util.*;

@SuppressLint("MissingPermission")
final class Nearby {
    static final int PERMISSIONS=61;
    static final int BG=0xff1a1e27, FG=0xfff5f7fa, SUB=0xffbbc5d3, ACCENT=0xff69caff;
    final MainActivity a;
    final BluetoothManager manager;
    final BluetoothAdapter bt;
    final Handler main=new Handler(Looper.getMainLooper());
    final LinkedHashMap<String, Found> devices=new LinkedHashMap<>();
    AlertDialog dialog, help;
    LinearLayout rows;
    TextView status;
    BluetoothLeScanner le;
    boolean receiving, scanning, classicScan;
    String note="";
    String steamInRange, steamPairing;
    final Runnable expire=this::stop;
    final Runnable resolveNames=new Runnable(){@Override public void run(){
        if(dialog==null||!permitted())return;
        for(Found x:new ArrayList<>(devices.values())){
            String name=x.d.getName();
            if(!TextUtils.isEmpty(name)&&x.name.equals("Identifying controller…"))found(x.d,name,null);
        }
        if(scanning)main.postDelayed(this,800);
    }};
    static class Found {
        final BluetoothDevice d;
        final String name, model;
        Found(BluetoothDevice d,String name,String model){this.d=d;this.name=name;this.model=model;}
    }
    Nearby(MainActivity a){
        this.a=a;
        manager=a.getSystemService(BluetoothManager.class);
        bt=manager==null?null:manager.getAdapter();
    }
    boolean permitted(){
        return a.checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN)==PackageManager.PERMISSION_GRANTED
            &&a.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)==PackageManager.PERMISSION_GRANTED;
    }
    void open(){
        if(Build.VERSION.SDK_INT<31){a.systemBluetooth();return;}
        if(!permitted()){
            a.requestPermissions(new String[]{Manifest.permission.BLUETOOTH_SCAN,Manifest.permission.BLUETOOTH_CONNECT},PERMISSIONS);
            return;
        }
        if(bt==null){new AlertDialog.Builder(a).setTitle("Bluetooth unavailable").setPositiveButton("OK",null).show();return;}
        if(dialog!=null)return;
        devices.clear();note="";steamInRange=steamPairing=null;
        LinearLayout body=column();body.setPadding(dp(20),dp(16),dp(20),dp(10));
        TextView title=label("Nearby controllers",21,FG);
        title.setTypeface(null,android.graphics.Typeface.BOLD);body.addView(title);
        status=label("",14,ACCENT);status.setPadding(0,dp(5),0,0);body.addView(status);
        TextView intro=label("Put a controller in pairing mode to see it here.",14,FG);
        LinearLayout.LayoutParams ip=new LinearLayout.LayoutParams(-1,-2);ip.topMargin=dp(6);ip.bottomMargin=dp(8);
        body.addView(intro,ip);
        rows=column();
        ScrollView scroll=new ScrollView(a);scroll.addView(rows);body.addView(scroll,new LinearLayout.LayoutParams(-1,dp(212)));
        body.setBackgroundColor(BG);
        dialog=new AlertDialog.Builder(a,android.R.style.Theme_Material_Dialog_Alert)
          .setView(body)
          .setNeutralButton("Pairing help",(d,w)->{}).setNegativeButton("Done",null)
          .setPositiveButton("Scan again",(d,w)->{}).create();
        dialog.setOnDismissListener(d->close());
        dialog.show();
        if(dialog.getWindow()!=null){
            dialog.getWindow().setLayout(a.getResources().getDisplayMetrics().widthPixels-dp(42),-2);
            dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(BG));
        }
        for(int which:new int[]{AlertDialog.BUTTON_POSITIVE,AlertDialog.BUTTON_NEUTRAL,AlertDialog.BUTTON_NEGATIVE})dialog.getButton(which).setTextColor(ACCENT);
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->start());
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v->picker());
        IntentFilter f=new IntentFilter();
        f.addAction(BluetoothDevice.ACTION_FOUND);f.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
        f.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        f.addAction(BluetoothDevice.ACTION_NAME_CHANGED);
        if(Build.VERSION.SDK_INT>=33)a.registerReceiver(receiver,f,Context.RECEIVER_EXPORTED);
        else a.registerReceiver(receiver,f);
        receiving=true;start();
    }
    void afterPermission(){
        if(permitted())open();
        else new AlertDialog.Builder(a).setTitle("Nearby devices")
            .setMessage("Allow Nearby devices to scan inside Controller Club, or use Android Bluetooth settings.")
            .setPositiveButton("Android Bluetooth",(d,w)->a.systemBluetooth()).setNegativeButton("Close",null).show();
    }
    void start(){
        if(dialog==null)return;
        stop();
        note="";steamInRange=null;
        if(Bridge.current!=null)Bridge.current.watchForSteamPairing(true);
        if(!bt.isEnabled()){note="Bluetooth is off. Turn it on in Android settings.";render();return;}
        devices.values().removeIf(x->x.d.getBondState()==BluetoothDevice.BOND_NONE);
        for(BluetoothDevice d:bt.getBondedDevices())found(d,null,null);
        classicScan=!bt.isDiscovering()&&bt.startDiscovery();
        boolean classic=classicScan||bt.isDiscovering(), ble=false;
        try {
            le=bt.getBluetoothLeScanner();
            if(le!=null){
                le.startScan(Collections.emptyList(),new ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(),scanCallback);
                ble=true;
            }
        }catch(RuntimeException ignored){le=null;}
        scanning=classic||ble;
        if(scanning){main.postDelayed(expire,24000);main.postDelayed(resolveNames,800);}
        else note="Scan unavailable. Try Android Bluetooth settings.";
        render();
    }
    void stop(){
        main.removeCallbacks(expire);
        main.removeCallbacks(resolveNames);
        if(scanning&&bt!=null&&permitted()){
            if(classicScan&&bt.isDiscovering())bt.cancelDiscovery();
            if(le!=null)try{le.stopScan(scanCallback);}catch(RuntimeException ignored){}
        }
        le=null;scanning=false;classicScan=false;render();
    }
    void close(){
        AlertDialog old=dialog;dialog=null;
        if(help!=null){help.dismiss();help=null;}
        stop();
        if(receiving){a.unregisterReceiver(receiver);receiving=false;}
        if(Bridge.current!=null)Bridge.current.watchForSteamPairing(false);
        if(old!=null&&old.isShowing())old.dismiss();
    }
    final BroadcastReceiver receiver=new BroadcastReceiver(){
        @Override public void onReceive(Context c,Intent i){
            if(dialog==null||!permitted())return;
            String action=i.getAction();
            if(BluetoothAdapter.ACTION_STATE_CHANGED.equals(action)){if(bt.isEnabled())start();else stop();return;}
            BluetoothDevice d=i.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
            if(d==null)return;
            if(BluetoothDevice.ACTION_FOUND.equals(action)&&scanning)
                found(d,i.getStringExtra(BluetoothDevice.EXTRA_NAME),i.getParcelableExtra(BluetoothDevice.EXTRA_CLASS));
            else if(BluetoothDevice.ACTION_NAME_CHANGED.equals(action)&&(scanning||devices.containsKey(d.getAddress())))
                found(d,i.getStringExtra(BluetoothDevice.EXTRA_NAME),null);
            else if(BluetoothDevice.ACTION_BOND_STATE_CHANGED.equals(action)
                    &&(devices.containsKey(d.getAddress())||SteamController.matches(d.getName()))){
                if(!devices.containsKey(d.getAddress()))found(d,null,null);
                int state=i.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE,BluetoothDevice.ERROR);
                if(state!=BluetoothDevice.BOND_BONDING&&d.getAddress().equals(steamPairing))steamPairing=null;
                note=state==BluetoothDevice.BOND_BONDED?"Paired. It will appear in a player slot once connected.":
                    state==BluetoothDevice.BOND_BONDING?"Pairing… follow any Android prompt.":"Pairing did not finish. Try again or open pairing help.";
                render();
            }
        }
    };
    final ScanCallback scanCallback=new ScanCallback(){
        @Override public void onScanResult(int type,ScanResult result){
            ScanRecord record=result.getScanRecord();
            String name=record==null?null:record.getDeviceName();
            boolean hid=record!=null&&record.getServiceUuids()!=null&&record.getServiceUuids().contains(ParcelUuid.fromString("00001812-0000-1000-8000-00805f9b34fb"));
            main.post(()->{
                if(!scanning||dialog==null)return;
                found(result.getDevice(),name,null,hid);
                Bridge bridge=Bridge.current;
                if(bridge!=null&&result.isConnectable()&&SteamController.matches(name))
                    bridge.steamInRange(result.getDevice());
            });
        }
    };
    void found(BluetoothDevice d,String name,BluetoothClass cls){found(d,name,cls,false);}
    void found(BluetoothDevice d,String name,BluetoothClass cls,boolean bleHid){
        if(TextUtils.isEmpty(name))name=d.getName();
        if(TextUtils.isEmpty(name)&&devices.containsKey(d.getAddress()))return;
        if(cls==null)cls=d.getBluetoothClass();
        int devClass=cls==null?0:cls.getDeviceClass();
        String model=MainActivity.hardware(name==null?"":name,0,0,0);
        String lower=name==null?"":name.toLowerCase(Locale.ROOT);
        boolean gamepad=devClass==0x0504||devClass==0x0508||
            lower.contains("gamepad")||lower.contains("game controller")||lower.contains("8bitdo")||
            lower.contains("gamesir")||lower.contains("steelseries")||lower.contains("wireless controller");
        if(!gamepad&&"generic".equals(model)&&!bleHid)return;
        if(bleHid&&"generic".equals(model)&&(lower.contains("keyboard")||lower.contains("mouse")))return;
        if(TextUtils.isEmpty(name))name="Identifying controller…";
        Found old=devices.get(d.getAddress());
        if("wii_remote".equals(model)&&!name.startsWith("Wii Remote")){
            name=name.toUpperCase(Locale.ROOT).contains("01-TR")?"Wii Remote Plus":"Wii Remote";
        }
        if(scanning&&"steam".equals(model)&&d.getBondState()==BluetoothDevice.BOND_NONE
                &&!d.getAddress().equals(steamInRange)){
            steamInRange=d.getAddress();
        }else if(old!=null&&old.name.equals(name)&&old.model.equals(model))return;
        devices.put(d.getAddress(),new Found(d,name,model));render();
    }
    void render(){
        if(dialog==null||rows==null)return;
        status.setText(!note.isEmpty()?note:scanning?"Looking for controllers…":devices.isEmpty()?"No controllers found":"Select a controller to pair");
        rows.removeAllViews();
        if(!bt.isEnabled()){
            add("Open Android Bluetooth settings",a::systemBluetooth);return;
        }
        ArrayList<Found> sorted=new ArrayList<>();
        Found steam=null;
        int steamRank=-1;
        Bridge bridge=Bridge.current;
        Bridge.Pad steamPad=bridge==null?null:bridge.find(Bridge.STEAM_PAD_ID);
        Set<String> connectedSteam=new HashSet<>();
        if(devices.values().stream().anyMatch(x->"steam".equals(x.model))){
            for(BluetoothDevice device:manager.getConnectedDevices(BluetoothProfile.GATT))
                connectedSteam.add(device.getAddress());
        }
        String lastSteam=a.getSharedPreferences("steam",0).getString("lastConnectedAddress","");
        for(Found x:devices.values()){
            if(!"steam".equals(x.model)){sorted.add(x);continue;}
            // The Valve bridge exposes one original Steam pad. Choose its live address first.
            String address=x.d.getAddress();
            int rank=isLiveSteam(x,steamPad)?5:connectedSteam.contains(address)?4:
                address.equals(steamPairing)?3:address.equals(steamInRange)?2:address.equals(lastSteam)?1:0;
            if(rank>steamRank){steam=x;steamRank=rank;}
        }
        if(steam!=null)sorted.add(steam);
        sorted.sort(Comparator.comparingInt(x->x.d.getBondState()==BluetoothDevice.BOND_BONDED?1:0));
        for(Found x:sorted){
            int bond=x.d.getBondState();
            String bluetoothName=x.d.getName();
            boolean connected=a.connectedController(bluetoothName);
            boolean originalSteam="steam".equals(x.model);
            if(originalSteam)connected=isLiveSteam(x,steamPad);
            boolean nano="xbox_nano".equals(x.model)||a.isConnectedNano(bluetoothName);
            Found display=nano?new Found(x.d,"Xbox Nano","xbox_nano"):
                originalSteam?new Found(x.d,"Steam Controller (2015)","steam"):x;
            String state="wii_remote".equals(x.model)?"Use a USB DolphinBar":bond==BluetoothDevice.BOND_BONDED?(connected?"Connected":"Paired"):bond==BluetoothDevice.BOND_BONDING?"Pairing…":x.name.equals("Identifying controller…")?"Waiting for Bluetooth name":"Ready to pair";
            addDevice(display,state);
        }
        if(sorted.isEmpty()){
            TextView empty=label("Try the controller’s Sync or Pair button. USB-only controllers appear on the main screen when plugged in.",14,SUB);
            rows.addView(empty);
        }
    }
    private boolean isLiveSteam(Found found,Bridge.Pad pad){
        return pad!=null&&pad.identity.equals(SteamController.hashAddress(found.d.getAddress()));
    }
    void addDevice(Found x,String state){
        LinearLayout row=new LinearLayout(a);row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(10),dp(4),dp(12),dp(4));row.setMinimumHeight(dp(62));
        GradientDrawable bg=new GradientDrawable();bg.setColor(ClubUi.SURF);
        bg.setStroke(dp(1),ClubUi.LINE);bg.setCornerRadius(dp(10));
        row.setBackground(new RippleDrawable(ColorStateList.valueOf(0x3369caff),bg,null));
        ClubUi.Art art=new ClubUi.Art(a,x.model,ACCENT);
        row.addView(art,new LinearLayout.LayoutParams(dp(47),dp(55)));
        LinearLayout copy=column();copy.setPadding(dp(8),0,0,0);
        TextView name=label(x.name,14,FG);name.setMaxLines(2);name.setEllipsize(TextUtils.TruncateAt.END);
        copy.addView(name);copy.addView(label(state,12,SUB));
        row.addView(copy,new LinearLayout.LayoutParams(0,-2,1));
        boolean wiimote="wii_remote".equals(x.model);
        boolean canPair=!wiimote&&!x.name.equals("Identifying controller…")&&x.d.getBondState()==BluetoothDevice.BOND_NONE;
        View action=ClubUi.button(a,wiimote?"Set up":canPair?"Pair":"Help",null,canPair?ACCENT:0,canPair?ClubUi.BG:FG,
            ()->{if(canPair)bond(x);else details(x.model,x);});
        row.addView(action,new LinearLayout.LayoutParams(dp(68),dp(42)));
        row.setFocusable(true);row.setContentDescription(x.name+", "+state);row.setOnClickListener(v->details(x.model,x));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=dp(7);
        rows.addView(row,lp);
    }
    void add(String title,Runnable action){
        Button b=new Button(a);b.setText(title);b.setTextColor(ClubUi.BG);b.setAllCaps(false);b.setGravity(Gravity.CENTER_VERTICAL);
        b.setMinHeight(dp(48));b.setOnClickListener(v->action.run());rows.addView(b,new LinearLayout.LayoutParams(-1,-2));
    }
    void picker(){
        String[] names={"Joy-Con (left or right)","Switch Pro Controller","Wii Remote","Xbox wireless","Xbox Nano","PlayStation","Steam Controller (2015)","Other controller","Android Bluetooth settings"};
        String[] type={"joy_left","switch_pro","wii_remote","xbox","xbox_nano","playstation","steam","generic"};
        help=new AlertDialog.Builder(a,android.R.style.Theme_Material_Dialog_Alert).setTitle("Put a controller in pairing mode")
            .setItems(names,(d,w)->{if(w==type.length){stop();a.systemBluetooth();}else details(type[w],null);})
            .setNegativeButton("Back",null).show();
    }
    void details(String model,Found x){
        String title=x!=null?x.name:"Pairing instructions";
        String instructions;
        switch(model){
            case "joy_left":case "joy_right":case "joy_pair":
                instructions="Hold the tiny Sync button on the rail until the LEDs chase. Pair each half separately. Back in Controller Club, combine them or leave them in separate slots.";break;
            case "switch_pro":
                instructions="Hold the Sync button next to USB-C until the player LEDs chase. Leave the controller awake while Android pairs.";break;
            case "wii_remote":
                instructions="Press the red SYNC button under the battery cover (on some models it is exposed on the back). Some remotes also advertise while 1 + 2 is held.\n\nThe Thor can discover a real Wii Remote, but Dolphin on Android cannot connect to it over the Thor’s built-in Bluetooth. For motion and pointer, connect a Mayflash DolphinBar over USB, choose Mode 4, sync the remote to the bar, then select Real Wii Remote in Dolphin.";break;
            case "steam":
                instructions="With the controller off, press Steam + B to reconnect to its last Bluetooth host. If the Thor is the other saved host, press Steam + Start. To pair again, hold Steam + Y while powering on. Controller Club starts pairing when it sees the controller. Follow any Android prompt.";break;
            case "xbox":
                instructions="Turn the controller on. Hold the small Pair button near USB until the Xbox light flashes quickly. Older Xbox One controllers without Bluetooth need another connection method.";break;
            case "xbox_nano":
                instructions="For Microsoft’s Xbox Nano, turn it on and hold the Pair button until the Xbox light flashes quickly. For a PowerA wired Nano, connect it with a data-capable USB-C adapter or hub.";break;
            case "playstation":
                instructions="With the pad off, hold PS + Create on DualSense or PS + Share on DualShock 4 until the light flashes.";break;
            default:
                instructions="Use the controller’s Android or Bluetooth gamepad mode, then hold its Pair button until the LEDs flash. If it is USB-only, plug it in with a data-capable adapter.";
        }
        AlertDialog.Builder b=new AlertDialog.Builder(a,android.R.style.Theme_Material_Dialog_Alert)
          .setTitle(title).setMessage(instructions).setNegativeButton("Back",null);
        if(x!=null&&!x.name.equals("Identifying controller…")&&x.d.getBondState()==BluetoothDevice.BOND_NONE&&!"wii_remote".equals(model))b.setPositiveButton("Pair with Android",(d,w)->bond(x));
        if("wii_remote".equals(model))b.setPositiveButton("Open Dolphin",(d,w)->{close();a.openDolphin();});
        else if(x!=null&&x.d.getBondState()==BluetoothDevice.BOND_BONDED)b.setPositiveButton("Bluetooth settings",(d,w)->{stop();a.systemBluetooth();});
        help=b.show();
    }
    void bond(Found x){
        stop();
        try{
            Bridge bridge=Bridge.current;
            boolean started="steam".equals(x.model)&&bridge!=null?bridge.pairSteam(x.d):x.d.createBond();
            if(started&&"steam".equals(x.model))steamPairing=x.d.getAddress();
            note=started?"Pairing… follow any Android prompt.":"Could not start pairing. Put the pad back into pairing mode.";
        }
        catch(RuntimeException e){note="Android could not pair this device. Try Bluetooth settings.";}
        render();
    }
    int dp(int x){return ClubUi.dp(a,x);}
    LinearLayout column(){LinearLayout l=new LinearLayout(a);l.setOrientation(LinearLayout.VERTICAL);return l;}
    TextView label(String t,int size,int c){TextView v=new TextView(a);v.setText(t);v.setTextSize(size);v.setTextColor(c);return v;}
}
