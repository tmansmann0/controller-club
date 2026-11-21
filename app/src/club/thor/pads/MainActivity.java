package club.thor.pads;

import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.hardware.display.DisplayManager;
import android.hardware.input.InputManager;
import android.os.*;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import java.util.*;

public final class MainActivity extends Activity implements ClubUi.Actions, InputManager.InputDeviceListener {
    static final String OPEN_ON_LOWER="club.thor.pads.OPEN_ON_LOWER";
    private InputManager input;
    private boolean front;
    private AlertDialog dialog;
    private Nearby nearby;
    private final String[] controllerColorKeys=new String[4], rightColorKeys=new String[4];
    private ClubUi.Card[] lineup;
    private static final String[] COLOR_NAMES={"Red","Orange","Yellow","Lime","Green","Mint","Cyan","Blue","Purple","Pink","Silver","White"};
    private static final int[] COLORS={0xFFFF4757,0xFFFF944D,0xFFFFC53D,0xFFB7DB51,0xFF3DDC84,0xFF70E1BE,
        0xFF00B4E6,0xFF6F9DFF,0xFFAA89FF,0xFFFF86B7,0xFFA9AFBA,0xFFF1F2F4};
    private int leftShoulderPad=-1, rightShoulderPad=-1;
    private static final int PICK_DOLPHIN = 63;
    private static final java.util.concurrent.Executor DOLPHIN_IO =
            java.util.concurrent.Executors.newSingleThreadExecutor();
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        getWindow().setStatusBarColor(ClubUi.BG);
        getWindow().setNavigationBarColor(ClubUi.BG);
        getWindow().getDecorView().setSystemUiVisibility(0);
        input=getSystemService(InputManager.class);
        nearby=new Nearby(this);
        startForegroundService(new Intent(this,Bridge.class));
        moveFromTile(getIntent());
    }
    @Override protected void onNewIntent(Intent intent){
        super.onNewIntent(intent);
        moveFromTile(intent);
    }
    private void moveFromTile(Intent intent){
        if(!OPEN_ON_LOWER.equals(intent.getAction())||lower())return;
        int display=lowerDisplayId(this);
        if(display>=0) getWindow().getDecorView().post(()->moveTo(display));
    }
    private void moveTo(int display){
        Intent intent=new Intent(this,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try{startActivity(intent,ActivityOptions.makeBasic().setLaunchDisplayId(display).toBundle());}
        catch(RuntimeException e){msg("Display move unavailable","Use Thor Control Center to move Controller Club.");}
    }
    @Override public void onResume(){
        super.onResume();front=true;Bridge.listener=this::render;input.registerInputDeviceListener(this,null);render();
    }
    @Override public void onDestroy(){nearby.close();super.onDestroy();}
    @Override public void onPause(){leftShoulderPad=rightShoulderPad=-1;front=false;input.unregisterInputDeviceListener(this);Bridge.listener=null;super.onPause();}
    boolean lower(){return getDisplay()!=null&&getDisplay().getDisplayId()!=Display.DEFAULT_DISPLAY;}
    private List<InputDevice> physical(){
        List<InputDevice> result=new ArrayList<>();
        for(int id:input.getInputDeviceIds()){
            InputDevice d=input.getInputDevice(id);
            if(d!=null&&!d.getName().startsWith("Controller Club P")&&!d.getName().startsWith("Xbox Controller P") && (d.supportsSource(InputDevice.SOURCE_GAMEPAD)||d.supportsSource(InputDevice.SOURCE_JOYSTICK)))result.add(d);
        }
        result.sort(Comparator.comparingInt(InputDevice::getControllerNumber));return result;
    }
    boolean connectedController(String name){
        if(name==null)return false;
        for(InputDevice d:physical())if(name.equalsIgnoreCase(d.getName()))return true;
        return false;
    }
    boolean isConnectedNano(String bluetoothName){
        Bridge bridge=Bridge.current;
        if(bridge==null||bluetoothName==null||!connectedController(bluetoothName))return false;
        return bridge.pads.stream().anyMatch(pad->bluetoothName.equalsIgnoreCase(pad.name)&&pad.vendor==0x045e&&pad.product==0x0b4f);
    }
    private SharedPreferences colors(){return getSharedPreferences("appearance",MODE_PRIVATE);}
    private int savedColor(String key,int fallback){return key==null?fallback:colors().getInt(key,fallback);}
    private String deviceColorKey(InputDevice device){
        if(device.getName().equals("Odin Controller"))return "controller.thor";
        return "controller.android."+device.getDescriptor();
    }
    private String deviceColorKey(Bridge.Pad pad,List<InputDevice> devices){
        if(pad.builtin)return "controller.thor";
        if(!pad.identity.isEmpty())return "controller.hardware."+pad.identity;
        InputDevice match=null;
        for(InputDevice device:devices){
            if(!device.getName().equalsIgnoreCase(pad.name))continue;
            if(match!=null)return "controller.model."+pad.vendor+"."+pad.product+"."+pad.name;
            match=device;
        }
        return match!=null?deviceColorKey(match):"controller.model."+pad.vendor+"."+pad.product+"."+pad.name;
    }
    void render(){
        if(!front)return;
        if(nearby!=null)nearby.render();
        Bridge b=Bridge.current;boolean online=b!=null&&b.online, on=online&&b.active;
        ClubUi.Card[] cards=new ClubUi.Card[4];
        List<InputDevice> androidPads=physical();
        for(int s=0;s<4;s++){
            String title="Open slot",detail="Tap to assign",type="empty",colorKey=null,rightKey=null;
            if(online && b.seats[s]!=null){
                int[] a=b.seats[s];Bridge.Pad p=b.find(a[0]);
                if(p!=null){
                    title=a.length==2?"Joy-Con pair":p.builtin?"Thor controls":p.name;
                    detail=a.length==2?"Left + right":p.side!=0?"Sideways":p.builtin?"Built-in":"Connected";
                    type=a.length==2?"joy_pair":hardware(p.name,p.side,p.vendor,p.product);
                    if("xbox_nano".equals(type))title="Xbox Nano";
                    colorKey=deviceColorKey(p,androidPads);
                    if(a.length==2){
                        Bridge.Pad other=b.find(a[1]);
                        if(other!=null){
                            if(p.side==1)rightKey=deviceColorKey(other,androidPads);
                            else{rightKey=colorKey;colorKey=deviceColorKey(other,androidPads);}
                        }
                    }
                }
            } else if(!online&&s<androidPads.size()){
                InputDevice d=androidPads.get(s);title=d.getName().equals("Odin Controller")?"Thor controls":d.getName();
                detail="Android #"+d.getControllerNumber();
                type=hardware(d.getName(),0,d.getVendorId(),d.getProductId());
                colorKey=deviceColorKey(d);
                if("xbox_nano".equals(type))title="Xbox Nano";
            }
            controllerColorKeys[s]=colorKey;
            rightColorKeys[s]=rightKey;
            cards[s]=new ClubUi.Card(title,detail,type,savedColor("seat."+s,ClubUi.SEAT[s]),savedColor(colorKey,0),savedColor(rightKey,0));
        }
        String status=!online?"Setup needed for routing · connected pads shown":
          b.busy?"Updating player slots…":!b.error.isEmpty()?b.error:on?(b.output.label+" · tap the game screen to play"):
          b.externalOnly?"Choose any player slot · retrying Thor access":b.pads.size()+" connected · tap a player to assign";
        boolean paired=online&&b.pairSeat()>=0;
        boolean canCombine=online&&b.pads.stream().anyMatch(p->p.side==1)
            &&b.pads.stream().anyMatch(p->p.side==2);
        if(canCombine&&!paired&&!on&&!b.busy&&b.error.isEmpty())status="Hold L + R on the two Joy-Cons to combine";
        lineup=cards;
        setContentView(ClubUi.build(this,cards,status,on,paired,paired||canCombine,lower(),this));
    }
    static String hardware(String raw,int side,int vendor,int product){
        String n=raw.toLowerCase(Locale.ROOT);
        if((vendor==0x28de&&product==0x1106)||SteamController.matches(raw))return "steam";
        if(n.equals("odin controller")||n.contains("ayn thor"))return "thor";
        if(side==1||(vendor==0x057e&&product==0x2006)||((n.contains("joy-con")||n.contains("joy con"))&&(n.contains("(l)")||n.contains("left"))))return "joy_left";
        if(side==2||(vendor==0x057e&&product==0x2007)||((n.contains("joy-con")||n.contains("joy con"))&&(n.contains("(r)")||n.contains("right"))))return "joy_right";
        if(n.contains("joy-con")||n.contains("joy con"))return "joy_pair";
        if(n.contains("wiimote")||n.contains("wii remote")||n.contains("rvl-cnt"))return "wii_remote";
        if((vendor==0x045e&&product==0x0b4f)||n.contains("nano")&&(n.contains("xbox")||n.contains("powera")||n.contains("enhanced wired")))return "xbox_nano";
        if(n.contains("xbox")||vendor==0x045e)return "xbox";
        if((vendor==0x057e&&product==0x2009)||n.contains("switch pro")||n.equals("pro controller")||n.contains("nintendo pro"))return "switch_pro";
        if(n.contains("dualshock")||n.contains("dualsense")||n.contains("playstation")||vendor==0x054c)return "playstation";
        return "generic";
    }
    @Override public boolean dispatchKeyEvent(KeyEvent event){
        int key=event.getKeyCode();
        if(key!=KeyEvent.KEYCODE_BUTTON_L1&&key!=KeyEvent.KEYCODE_BUTTON_R1)return super.dispatchKeyEvent(event);
        Bridge bridge=Bridge.current;
        InputDevice device=event.getDevice();
        if(bridge==null||!bridge.online||bridge.active||bridge.busy||device==null||dialog!=null&&dialog.isShowing())
            return super.dispatchKeyEvent(event);
        int side=key==KeyEvent.KEYCODE_BUTTON_L1?1:2;
        String expected=side==1?"joy_left":"joy_right";
        if(!expected.equals(hardware(device.getName(),0,device.getVendorId(),device.getProductId())))
            return super.dispatchKeyEvent(event);
        Bridge.Pad match=null;
        for(Bridge.Pad pad:bridge.pads){
            if(pad.side!=side||!pad.name.equalsIgnoreCase(device.getName()))continue;
            if(match!=null)return super.dispatchKeyEvent(event);
            match=pad;
        }
        if(match==null)return super.dispatchKeyEvent(event);
        if(key==KeyEvent.KEYCODE_BUTTON_L1)leftShoulderPad=event.getAction()==KeyEvent.ACTION_DOWN?match.id:-1;
        else rightShoulderPad=event.getAction()==KeyEvent.ACTION_DOWN?match.id:-1;
        if(leftShoulderPad>=0&&rightShoulderPad>=0){
            Bridge.Pad left=bridge.find(leftShoulderPad), right=bridge.find(rightShoulderPad);
            leftShoulderPad=rightShoulderPad=-1;
            if(left!=null&&right!=null&&(bridge.seat(left.id)<0||bridge.seat(left.id)!=bridge.seat(right.id)))combine(bridge,left,right);
        }
        return true;
    }
    @Override public void onInputDeviceAdded(int id){if(Bridge.current==null||!Bridge.current.online)render();if(nearby!=null)nearby.render();}
    @Override public void onInputDeviceRemoved(int id){onInputDeviceAdded(id);}
    @Override public void onInputDeviceChanged(int id){onInputDeviceAdded(id);}
    private Bridge ready(){
        Bridge b=Bridge.current;
        if(b==null||!b.online){
            show(new AlertDialog.Builder(this,android.R.style.Theme_Material_Dialog_Alert).setTitle("Router is offline").setMessage("Close and reopen Controller Club. For routing the built-in Thor controls, connect USB and run scripts/start.sh from your Mac.").setPositiveButton("OK",null));
            return null;
        }
        return b.busy?null:b;
    }
    private void show(AlertDialog.Builder builder){if(dialog!=null)dialog.dismiss();dialog=builder.show();}
    @Override public void toggle(){Bridge b=ready();if(b==null)return;if(b.active)b.stopRouting();else if(b.canRoute())b.sendRoute();else msg("No players assigned","Connect a controller, then tap a player slot to assign it.");}
    @Override public void choose(int slot){
        Bridge b=Bridge.current;
        boolean canAssign=b!=null&&b.online;
        android.widget.LinearLayout panel=new android.widget.LinearLayout(this);
        panel.setOrientation(android.widget.LinearLayout.VERTICAL);
        panel.setPadding(ClubUi.dp(this,18),ClubUi.dp(this,16),ClubUi.dp(this,18),ClubUi.dp(this,12));
        panel.setBackgroundColor(ClubUi.BG);
        panel.addView(ClubUi.text(this,(canAssign?"Assign player ":"Player ")+(slot+1),22,ClubUi.TEXT,true));
        if(canAssign){
            android.widget.ScrollView scroll=new android.widget.ScrollView(this);
            android.widget.LinearLayout rows=new android.widget.LinearLayout(this);
            rows.setOrientation(android.widget.LinearLayout.VERTICAL);
            scroll.addView(rows);
            panel.addView(scroll,new android.widget.LinearLayout.LayoutParams(-1,ClubUi.dp(this,Math.min(250,Math.max(72,b.pads.size()*64)))));
            for(Bridge.Pad pad:new ArrayList<>(b.pads)){
                String type=hardware(pad.name,pad.side,pad.vendor,pad.product);
                String label=pad.builtin?"Thor controls":"xbox_nano".equals(type)?"Xbox Nano":pad.name;
                int current=b.seat(pad.id);
                android.widget.LinearLayout row=new android.widget.LinearLayout(this);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(ClubUi.dp(this,8),0,ClubUi.dp(this,12),0);
                row.setBackground(ClubUi.states(this,ClubUi.SURF,current==slot?ClubUi.FOCUS:ClubUi.LINE,10,false));
                row.addView(new ClubUi.Art(this,type,lineup[slot].slotColor,savedColor(deviceColorKey(pad,physical()),0),0),new android.widget.LinearLayout.LayoutParams(ClubUi.dp(this,60),ClubUi.dp(this,53)));
                row.addView(ClubUi.text(this,label,15,ClubUi.TEXT,true),new android.widget.LinearLayout.LayoutParams(0,-2,1));
                row.addView(ClubUi.text(this,current<0?"Unassigned":"P"+(current+1),13,ClubUi.SOFT,false));
                ClubUi.tap(row,label+", "+(current<0?"unassigned":"player "+(current+1)),()->{
                    dialog.dismiss();
                    if(!b.busy&&b.find(pad.id)!=null)b.assign(slot,pad);
                });
                android.widget.LinearLayout.LayoutParams item=new android.widget.LinearLayout.LayoutParams(-1,ClubUi.dp(this,58));
                item.topMargin=ClubUi.dp(this,6);
                rows.addView(row,item);
            }
        }
        android.widget.LinearLayout footer=new android.widget.LinearLayout(this);
        footer.setGravity(Gravity.END);
        footer.addView(ClubUi.button(this,"Colors",null,0,ClubUi.TEXT,()->showColors(slot)));
        if(canAssign&&b.seats[slot]!=null)footer.addView(ClubUi.button(this,"Clear slot",null,0,ClubUi.TEXT,()->{dialog.dismiss();if(!b.busy)b.clear(slot);}));
        footer.addView(ClubUi.button(this,"Done",null,0,ClubUi.FOCUS,()->dialog.dismiss()));
        panel.addView(footer);
        show(new AlertDialog.Builder(this).setView(panel));
        styleClubDialog();
    }
    private void showColors(int slot){
        LinearLayout content=new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(ClubUi.dp(this,16),ClubUi.dp(this,14),ClubUi.dp(this,16),ClubUi.dp(this,10));
        content.setBackgroundColor(ClubUi.BG);
        content.addView(ClubUi.text(this,"Player "+(slot+1)+" colors",22,ClubUi.TEXT,true));
        LinearLayout groups=new LinearLayout(this);
        groups.setOrientation(LinearLayout.VERTICAL);
        ScrollView scroll=new ScrollView(this);
        scroll.addView(groups);
        content.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        String controller=controllerColorKeys[slot], right=rightColorKeys[slot];
        if(controller!=null)groups.addView(colorGroup(right==null?lineup[slot].title+" · Controller":"Left Joy-Con",controller));
        if(right!=null)groups.addView(colorGroup("Right Joy-Con",right));
        groups.addView(colorGroup("Player slot","seat."+slot));
        LinearLayout footer=new LinearLayout(this);
        footer.setGravity(Gravity.END);
        footer.addView(ClubUi.button(this,"Done",null,0,ClubUi.FOCUS,()->dialog.dismiss()));
        content.addView(footer);
        show(new AlertDialog.Builder(this).setView(content));
        styleClubDialog();
        if(dialog.getWindow()!=null){
            int available=getResources().getDisplayMetrics().heightPixels-ClubUi.dp(this,48);
            int preferred=ClubUi.dp(this,right!=null?520:controller!=null?390:225);
            dialog.getWindow().setLayout(dialog.getWindow().getAttributes().width,Math.min(available,preferred));
        }
    }
    private View colorGroup(String label,String key){
        LinearLayout group=new LinearLayout(this);
        group.setOrientation(LinearLayout.VERTICAL);
        group.setPadding(0,ClubUi.dp(this,8),0,0);
        group.addView(ClubUi.text(this,label,15,ClubUi.TEXT,true));
        ArrayList<View> swatches=new ArrayList<>();
        for(int index=0;index<COLORS.length;index++){
            if(index%6==0)group.addView(new LinearLayout(this));
            LinearLayout row=(LinearLayout)group.getChildAt(group.getChildCount()-1);
            int color=COLORS[index];
            FrameLayout touch=new FrameLayout(this);
            View swatch=new View(this);
            FrameLayout.LayoutParams dot=new FrameLayout.LayoutParams(ClubUi.dp(this,30),ClubUi.dp(this,30),Gravity.CENTER);
            touch.addView(swatch,dot);
            row.addView(touch,new LinearLayout.LayoutParams(0,ClubUi.dp(this,44),1));
            swatches.add(swatch);
            ClubUi.tap(touch,label+": "+COLOR_NAMES[index],()->{
                colors().edit().putInt(key,color).apply();
                refreshSwatches(swatches,color);
                render();
            });
        }
        group.addView(ClubUi.button(this,"Default",null,0,ClubUi.SOFT,()->{
            colors().edit().remove(key).apply();
            refreshSwatches(swatches,0);
            render();
        }));
        refreshSwatches(swatches,savedColor(key,0));
        return group;
    }
    private void refreshSwatches(List<View> swatches,int selected){
        for(int index=0;index<swatches.size();index++){
            View swatch=swatches.get(index);
            boolean checked=COLORS[index]==selected;
            swatch.setBackground(ClubUi.shape(this,COLORS[index],checked?ClubUi.TEXT:ClubUi.LINE,checked?3:1,20,false));
            ((View)swatch.getParent()).setSelected(checked);
        }
    }
    private void styleClubDialog(){
        if(dialog.getWindow()==null)return;
        dialog.getWindow().setLayout(Math.max(ClubUi.dp(this,250),getResources().getDisplayMetrics().widthPixels-ClubUi.dp(this,36)),-2);
        dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(ClubUi.BG));
    }
    @Override public void pair(){
        Bridge b=ready();if(b==null)return;
        List<Bridge.Pad> left=new ArrayList<>(),right=new ArrayList<>();
        for(Bridge.Pad p:b.pads){if(p.side==1)left.add(p);if(p.side==2)right.add(p);}
        if(left.isEmpty()||right.isEmpty()){
            show(new AlertDialog.Builder(this,android.R.style.Theme_Material_Dialog_Alert).setTitle("Connect both Joy-Cons").setMessage("Pair each Joy-Con in Bluetooth settings. When both appear, you can combine them into one player.").setPositiveButton("Bluetooth",(d,w)->bluetooth()).setNegativeButton("Close",null));return;
        }
        if(left.size()==1&&right.size()==1){combine(b,left.get(0),right.get(0));return;}
        pickHalf(b,left,"Choose left Joy-Con",l->pickHalf(b,right,"Choose right Joy-Con",r->combine(b,l,r)));
    }
    private void combine(Bridge b,Bridge.Pad l,Bridge.Pad r){
        if(b.find(l.id)==null||b.find(r.id)==null)return;
        if(b.open()<0&&b.seat(l.id)<0&&b.seat(r.id)<0){msg("No open slot","Clear a player slot, then combine the Joy-Cons.");return;}
        b.combine(l,r);
    }
    private void pickHalf(Bridge b,List<Bridge.Pad> pads,String title,java.util.function.Consumer<Bridge.Pad> fn){
        String[] names=pads.stream().map(p->p.name+" · "+(b.seat(p.id)<0?"Unassigned":"P"+(b.seat(p.id)+1))).toArray(String[]::new);
        show(new AlertDialog.Builder(this,android.R.style.Theme_Material_Dialog_Alert).setTitle(title).setItems(names,(d,i)->fn.accept(pads.get(i))).setNegativeButton("Cancel",null));
    }
    @Override public void split(){
        Bridge b=ready();if(b==null)return;
        if(b.open()<0){msg("No open slot","Clear a slot for the second Joy-Con, then split the pair.");return;}
        List<Integer> pairs=new ArrayList<>();
        for(int s=0;s<4;s++)if(b.seats[s]!=null&&b.seats[s].length==2)pairs.add(s);
        if(pairs.size()==1)b.split(pairs.get(0));
        else if(pairs.size()>1)show(new AlertDialog.Builder(this,android.R.style.Theme_Material_Dialog_Alert).setTitle("Split which Joy-Con pair?")
            .setItems(pairs.stream().map(s->"Player "+(s+1)).toArray(String[]::new),(d,i)->b.split(pairs.get(i))).setNegativeButton("Cancel",null));
    }
    private void msg(String title,String message){show(new AlertDialog.Builder(this,android.R.style.Theme_Material_Dialog_Alert).setTitle(title).setMessage(message).setPositiveButton("OK",null));}
    @Override public void bluetooth(){nearby.open();}
    void systemBluetooth(){
        try{startActivity(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS));}catch(ActivityNotFoundException e){msg("Bluetooth","Open Bluetooth from Android Settings.");}
    }
    void openDolphin(){launch("org.dolphinemu.dolphinemu");}
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] results){
        super.onRequestPermissionsResult(request,permissions,results);
        if(request==Nearby.PERMISSIONS)nearby.afterPermission();
    }
    static int lowerDisplayId(Context context){
        DisplayManager dm=context.getSystemService(DisplayManager.class);
        for(Display d:dm.getDisplays())if(d.getDisplayId()!=0&&"Screen-2".equalsIgnoreCase(d.getName()))return d.getDisplayId();
        return -1;
    }
    @Override public void move(){
        int id=lower()?0:lowerDisplayId(this);
        if(id<0){msg("Lower screen unavailable","Turn on both displays in Thor Control Center.");return;}
        moveTo(id);
    }
    static final String[][] EMUS={
        {"org.dolphinemu.dolphinemu","Wii / GameCube","Set each port to Emulated Wii Remote or Standard Controller. Load the matching Club Wii or GameCube profile, or select Controller Club P1–P4 in the Device menu. For Wii, use Classic Controller where the game supports it. Use original Wii Remotes directly for pointer, motion, and passthrough."},
        {"info.cemu.cemuplus","Wii U","Open Input settings and load Club-P1 as the Wii U GamePad. Load Club-P2 through Club-P4 as Pro Controllers where the game supports them. If Club profiles are unavailable, select the named pad and run Setup all inputs. Save your original mapping as a named profile first."},
        {"com.flycast.emulator","Dreamcast","Use Xbox output. In Flycast’s Controls settings, match Xbox Controller P1–P4 to ports A–D if the game needs explicit player assignments."},
        {"com.nx.android","Switch native launcher","This Switch port launcher has no verified player assignment adapter. A port may accept Android gamepads, but per-player binding depends on the port."},
        {"org.nx2a.t0100965017338000","Switch native port","This game port has no verified player assignment adapter. The Club pads appear as Android gamepads; the port decides how to use them."},
        {"com.armsx3","PS3","Open the Controller tab and Controller Mapping. Turn off All controllers play as Player 1, then map each available player to the matching Club pad."},
        {"io.github.lime3ds.android","3DS","Select Xbox Controller P1 when mapping controls."},
        {"me.magnum.melonds","DS","Map Xbox Controller P1 as your gamepad."},
        {"org.vita3k.emulator","PS Vita","Select Xbox Controller P1 where the app offers a controller choice."},
        {"com.explusalpha.GbaEmu","GBA","Open Input Device Setup and choose the matching Club pad."},
        {EdenGuide.PACKAGE,"Switch",""},
        {"org.yuzu.yuzu_emu","Switch","Use Pro Controller and select the matching Club pad per player."}
    };
    @Override public void emulators(){
        android.widget.LinearLayout panel=new android.widget.LinearLayout(this);
        panel.setOrientation(android.widget.LinearLayout.VERTICAL);
        panel.setPadding(ClubUi.dp(this,18),ClubUi.dp(this,16),ClubUi.dp(this,18),ClubUi.dp(this,10));
        panel.setBackgroundColor(ClubUi.BG);
        panel.addView(ClubUi.text(this,"Emulators",22,ClubUi.TEXT,true));
        Bridge bridge=Bridge.current;
        if(bridge!=null){
            View output=ClubUi.button(this,"Output: "+bridge.output.label,
                null,0,ClubUi.FOCUS,()->{
                    bridge.selectOutput(Bridge.Output.values()[(bridge.output.ordinal()+1)%Bridge.Output.values().length]);
                    dialog.dismiss();
                    emulators();
                });
            panel.addView(output,new android.widget.LinearLayout.LayoutParams(-1,ClubUi.dp(this,44)));
        }
        android.widget.ScrollView scroll=new android.widget.ScrollView(this);
        android.widget.LinearLayout rows=new android.widget.LinearLayout(this);
        rows.setOrientation(android.widget.LinearLayout.VERTICAL);
        scroll.addView(rows);
        panel.addView(scroll,new android.widget.LinearLayout.LayoutParams(-1,ClubUi.dp(this,300)));
        for(String[] emulator:EMUS){
            try{
                ApplicationInfo app=getPackageManager().getApplicationInfo(emulator[0],0);
                CharSequence label=getPackageManager().getApplicationLabel(app);
                android.widget.LinearLayout row=new android.widget.LinearLayout(this);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(ClubUi.dp(this,10),ClubUi.dp(this,6),ClubUi.dp(this,8),ClubUi.dp(this,6));
                row.setBackground(ClubUi.states(this,ClubUi.SURF,ClubUi.LINE,10,false));
                android.widget.ImageView icon=new android.widget.ImageView(this);
                icon.setImageDrawable(getPackageManager().getApplicationIcon(app));
                int size=ClubUi.dp(this,36);
                row.addView(icon,new android.widget.LinearLayout.LayoutParams(size,size));
                android.widget.LinearLayout copy=new android.widget.LinearLayout(this);
                copy.setOrientation(android.widget.LinearLayout.VERTICAL);
                copy.setPadding(ClubUi.dp(this,10),0,ClubUi.dp(this,6),0);
                copy.addView(ClubUi.text(this,label.toString(),15,ClubUi.TEXT,true));
                copy.addView(ClubUi.text(this,emulator[1],12,ClubUi.SOFT,false));
                row.addView(copy,new android.widget.LinearLayout.LayoutParams(0,-2,1));
                boolean dolphin="org.dolphinemu.dolphinemu".equals(emulator[0]);
                boolean eden=EdenGuide.PACKAGE.equals(emulator[0]);
                View details=ClubUi.button(this,dolphin?"Mario Kart Wii":eden?"Set up controls":"Controls",null,0,ClubUi.TEXT,()->{
                    if(dolphin)marioKartOptions();
                    else if(eden)edenOptions();
                    else show(new AlertDialog.Builder(this,android.R.style.Theme_Material_Dialog_Alert)
                        .setTitle(label).setMessage(emulator[2])
                        .setPositiveButton("Open on top",(d,w)->launch(emulator[0])).setNegativeButton("Close",null));
                });
                row.addView(details,new android.widget.LinearLayout.LayoutParams(-2,ClubUi.dp(this,44)));
                row.setOnClickListener(v->{dialog.dismiss();launch(emulator[0]);});
                android.widget.LinearLayout.LayoutParams item=new android.widget.LinearLayout.LayoutParams(-1,-2);
                item.topMargin=ClubUi.dp(this,7);
                rows.addView(row,item);
            }catch(PackageManager.NameNotFoundException ignored){}
        }
        show(new AlertDialog.Builder(this,android.R.style.Theme_Material_Dialog_Alert)
            .setView(panel).setNegativeButton("Done",null));
        styleClubDialog();
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(ClubUi.FOCUS);
    }
    private void edenOptions(){
        LinearLayout panel=new LinearLayout(this);panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(ClubUi.dp(this,20),ClubUi.dp(this,16),ClubUi.dp(this,20),ClubUi.dp(this,12));panel.setBackgroundColor(ClubUi.BG);
        panel.addView(ClubUi.text(this,"Eden · Controller Club",22,ClubUi.TEXT,true));
        ScrollView scroll=new ScrollView(this);
        TextView guide=ClubUi.text(this,EdenGuide.INSTRUCTIONS,14,ClubUi.SOFT,false);
        guide.setPadding(0,ClubUi.dp(this,10),0,ClubUi.dp(this,10));scroll.addView(guide);
        panel.addView(scroll,new LinearLayout.LayoutParams(-1,ClubUi.dp(this,265)));
        panel.addView(ClubUi.button(this,"Open Eden on top",null,0,ClubUi.FOCUS,()->{dialog.dismiss();launch(EdenGuide.PACKAGE);}));
        panel.addView(ClubUi.button(this,"Done",null,0,ClubUi.TEXT,()->dialog.dismiss()));
        show(new AlertDialog.Builder(this).setView(panel));
    }
    private void marioKartOptions(){
        show(new AlertDialog.Builder(this,android.R.style.Theme_Material_Dialog_Alert)
            .setTitle("Mario Kart Wii · Controller setup")
            .setMessage("Bind both Mario Kart Wii players to the order shown in Controller Club. Start Club routing before launching the game.")
            .setPositiveButton("Use Club seats",(d,w)->editMarioKart(false))
            .setNeutralButton("Restore previous",(d,w)->editMarioKart(true))
            .setNegativeButton("Close",null));
    }
    private void editMarioKart(boolean restore){
        if(!restore&&Bridge.current!=null)Bridge.current.selectOutput(Bridge.Output.PROFILES);
        Uri tree=null;
        for(android.content.UriPermission permission:getContentResolver().getPersistedUriPermissions()){
            if(permission.isWritePermission()&&MarioKartSetup.isDolphinRoot(permission.getUri()))tree=permission.getUri();
        }
        if(tree!=null){saveMarioKart(tree,restore);return;}
        if(restore){msg("No saved setup","Set up the Club seats first.");return;}
        Intent picker=new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            .putExtra(DocumentsContract.EXTRA_INITIAL_URI,DocumentsContract.buildDocumentUri(MarioKartSetup.AUTHORITY,"root"));
        try{startActivityForResult(picker,PICK_DOLPHIN);}
        catch(ActivityNotFoundException e){msg("Dolphin folder unavailable","Android’s file picker could not be opened.");}
    }
    @Override protected void onActivityResult(int code,int result,Intent data){
        super.onActivityResult(code,result,data);
        if(result!=RESULT_OK||data==null||data.getData()==null)return;
        Uri tree=data.getData();
        if(code!=PICK_DOLPHIN)return;
        if(!MarioKartSetup.isDolphinRoot(tree)){android.util.Log.w("ClubSetup","Unexpected folder provider "+tree.getAuthority()+" id "+DocumentsContract.getTreeDocumentId(tree));msg("Choose the Dolphin folder","In the file picker, choose Dolphin Emulator from the sidebar, then tap Use this folder.");return;}
        try{
            getContentResolver().takePersistableUriPermission(tree,Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            saveMarioKart(tree,false);
        }catch(SecurityException e){msg("Dolphin access was not granted","Choose the Dolphin Emulator folder and allow access.");}
    }
    private void saveMarioKart(Uri tree,boolean restore){
        DOLPHIN_IO.execute(()->{
            String title,message;
            try{
                MarioKartSetup setup=new MarioKartSetup(getApplicationContext(),tree);
                if(restore){
                    title=setup.restore()?"Previous settings restored":"No Club settings to restore";
                    message="Changes take effect when you next open Mario Kart Wii.";
                }else{
                    setup.apply();
                    title="Mario Kart Wii follows Club seats";
                    message="Start Club routing, then reopen Mario Kart Wii. Both players follow the order shown in Controller Club.";
                }
            }catch(Exception e){
                title="Could not update Dolphin";
                message=e.getLocalizedMessage();
            }
            final String heading=title, detail=message;
            runOnUiThread(()->{if(!isFinishing()&&!isDestroyed())msg(heading,detail);});
        });
    }
    private void launch(String pkg){
        Intent i=getPackageManager().getLaunchIntentForPackage(pkg);
        if(i==null){msg("Open emulator","Use your launcher to open this app.");return;}
        Bridge bridge=Bridge.current;
        if(bridge!=null)bridge.selectOutput(EdenGuide.PACKAGE.equals(pkg)?Bridge.Output.EDEN:
            pkg.equals("org.dolphinemu.dolphinemu")||pkg.equals("info.cemu.cemuplus")?Bridge.Output.PROFILES:Bridge.Output.XBOX);
        try{startActivity(i,ActivityOptions.makeBasic().setLaunchDisplayId(0).toBundle());}
        catch(RuntimeException e){msg("Open emulator","Use your launcher to open this app.");}
    }
}
