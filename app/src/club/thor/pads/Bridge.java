package club.thor.pads;

import android.app.*;
import android.content.*;
import android.os.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

public final class Bridge extends Service {
    static final int STEAM_PAD_ID=1000000000;
    static final String CHANNEL="controller_club";
    static final Handler MAIN=new Handler(Looper.getMainLooper());
    static Bridge current;
    static Runnable listener;
    static class Pad {
        final int id, side, vendor, product; final boolean builtin; final String name, identity;
        Pad(int id,int side,boolean builtin,int vendor,int product,String name,String identity) {this.id=id;this.side=side;this.builtin=builtin;this.vendor=vendor;this.product=product;this.name=name;this.identity=identity;}
    }
    final ArrayList<Pad> pads=new ArrayList<>();
    final int[][] seats=new int[4][];
    final Set<Integer> omitted=new HashSet<>();
    boolean online,active,busy;
    boolean routingWanted;
    volatile boolean closed;
    String error="";
    Handler worker;
    HandlerThread thread;
    private SteamController steam;
    private ControllerMotion motion;
    volatile Socket socket;
    volatile java.lang.Process localRouter;
    volatile boolean usingLocal;
    volatile boolean externalOnly;
    private long lastAccessAttempt;
    private static final String ASSIGNMENT="assignment.";
    enum Output {
        XBOX("Controller Club P1–P4"), PROFILES("Club players · Dolphin / Cemu"), EDEN("Club players · Eden + motion");
        final String label;
        Output(String label){this.label=label;}
    }
    Output output=Output.XBOX;
    private String localToken;
    BufferedReader reader;
    BufferedWriter writer;
    static void changed(){if(listener!=null)listener.run();}
    @Override public IBinder onBind(Intent i){return null;}
    @Override public void onCreate(){
        super.onCreate();current=this;
        SharedPreferences settings=getSharedPreferences("routing",MODE_PRIVATE);
        settings.edit().remove("edenLinks").apply();
        // Eden integration is now instructions-only; release its old folder grant.
        for(UriPermission permission:getContentResolver().getPersistedUriPermissions()){
            if(!(EdenGuide.PACKAGE+".user").equals(permission.getUri().getAuthority()))continue;
            int flags=(permission.isReadPermission()?Intent.FLAG_GRANT_READ_URI_PERMISSION:0)
                |(permission.isWritePermission()?Intent.FLAG_GRANT_WRITE_URI_PERMISSION:0);
            try{getContentResolver().releasePersistableUriPermission(permission.getUri(),flags);}
            catch(SecurityException ignored){}
        }
        routingWanted=settings.getBoolean("routingWanted",false);
        String mode=settings.getString("output",settings.getBoolean("xboxOutput",true)?"XBOX":"PROFILES");
        try{output=Output.valueOf(mode);}catch(IllegalArgumentException ignored){}
        NotificationManager nm=getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel(CHANNEL,"Controller routing",NotificationManager.IMPORTANCE_LOW));
        startForeground(1,notification(false));
        thread=new HandlerThread("Controller Club bridge");thread.start();worker=new Handler(thread.getLooper());
        steam=new SteamController(this,worker);
        motion=new ControllerMotion(this,worker);
        worker.post(poll);changed();
    }
    @Override public int onStartCommand(Intent i,int flags,int id){
        if(i!=null && "OFF".equals(i.getAction()))stopRouting();
        return START_STICKY;
    }
    Notification notification(boolean on){
        int flags=PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT;
        PendingIntent open=PendingIntent.getActivity(this,1,new Intent(this,MainActivity.class),flags);
        PendingIntent stop=PendingIntent.getService(this,2,new Intent(this,Bridge.class).setAction("OFF"),flags);
        Notification.Builder b=new Notification.Builder(this,CHANNEL).setSmallIcon(R.drawable.club)
          .setContentTitle("Controller Club").setContentText(on?"Routing player inputs":"Watching for controllers")
          .setContentIntent(open).setOngoing(on).setOnlyAlertOnce(true);
        if(on)b.addAction(new Notification.Action.Builder(null,"Stop routing",stop).build());
        return b.build();
    }
    final Runnable poll=new Runnable(){ public void run(){
        if(closed)return;
        if(socket==null){
            try {
                connectRouter();
            } catch(IOException e){lost();}
        } else request("STATUS",false);
        if(usingLocal&&externalOnly&&SystemClock.elapsedRealtime()-lastAccessAttempt>=5000){
            lastAccessAttempt=SystemClock.elapsedRealtime();
            ThorAccess.restore();
        }
        steam.tick();
        if(!closed)worker.postDelayed(this,1200);
    }};
    private void connectRouter() throws IOException {
        // Use the bundled router: an older USB helper may implement a different
        // controller identity/layout and must not silently become the active backend.
        if(!Build.MODEL.toLowerCase(Locale.ROOT).contains("thor"))throw new IOException("Thor required");
        startLocalRouter();
        if(closed){localRouter.destroy();throw new IOException("Router closed");}
        usingLocal=true;
        connect(37820,localToken);
    }
    private void startLocalRouter() throws IOException {
        ThorAccess.restore();
        if(localRouter!=null&&localRouter.isAlive())return;
        if(localToken==null){
            byte[] random=new byte[32];new java.security.SecureRandom().nextBytes(random);
            StringBuilder token=new StringBuilder(64);
            for(byte b:random)token.append(String.format(Locale.ROOT,"%02x",b&255));
            localToken=token.toString();
        }
        File secret=new File(getFilesDir(),"local-router.token");
        Files.write(secret.toPath(),localToken.getBytes(StandardCharsets.US_ASCII));
        File binary=new File(getApplicationInfo().nativeLibraryDir,"libclub_router.so");
        localRouter=new ProcessBuilder(binary.getAbsolutePath(),secret.getAbsolutePath(),"37820")
                .redirectErrorStream(true).start();
    }
    private void connect(int port,String token) throws IOException {
        Socket s=new Socket();
        socket=s;
        s.connect(new InetSocketAddress("127.0.0.1",port),800);
        s.setSoTimeout(1200);
        reader=new BufferedReader(new InputStreamReader(s.getInputStream(),StandardCharsets.UTF_8));
        writer=new BufferedWriter(new OutputStreamWriter(s.getOutputStream(),StandardCharsets.US_ASCII));
        request("AUTH "+token,false);
        request("PLAYERS",false);
        if(output==Output.EDEN)request("EDENIDLE",false);
        steam.announce();
    }
    private void closeConnection(){
        Socket old=socket;socket=null;
        if(old!=null)try{old.close();}catch(IOException ignored){}
    }
    void watchForSteamPairing(boolean open){
        worker.post(()->steam.setNearby(open));
    }
    void steamInRange(android.bluetooth.BluetoothDevice device){
        worker.post(()->steam.controllerSeen(device));
    }
    boolean pairSteam(android.bluetooth.BluetoothDevice device){
        return worker.post(()->steam.pair(device,true));
    }
    void sendSteam(String command){
        if(socket==null)return;
        try{writer.write(command);writer.newLine();writer.flush();}
        catch(IOException e){lost();}
    }
    void lost(){
        closeConnection();
        if(steam!=null)steam.setRouting(false);
        if(motion!=null)motion.close();
        MAIN.post(()->{if(closed)return; boolean redraw=online||busy; if(online){pads.clear();omitted.clear();Arrays.fill(seats,null);}
            active=false;online=false;busy=false;
            if(redraw){getSystemService(NotificationManager.class).notify(1,notification(false));changed();}
        });
    }
    void request(String command,boolean user){
        try {
            if(socket==null)throw new IOException();
            writer.write(command);writer.newLine();writer.flush();
            String first=reader.readLine();
            if(first==null)throw new EOFException();
            if(first.startsWith("ERR ")){MAIN.post(()->{if(!closed){error=first.substring(4);busy=false;changed();}});return;}
            if(!first.matches("STATE [0-9]+ [01]"))throw new IOException();
            Set<Integer> motionSources=new HashSet<>();
            ArrayList<Pad> found=new ArrayList<>();boolean on=first.endsWith(" 1");
            boolean steamAssigned=false;
            for(int i=0;i<=25;i++){
                String line=reader.readLine();
                if("END".equals(line)){
                    steam.setRouting(on&&steamAssigned);
                    motion.sync(found,motionSources);
                    boolean local=usingLocal&&found.stream().noneMatch(pad->pad.builtin);
                    MAIN.post(()->accept(found,on,user,local));return;
                }
                if(line==null)throw new EOFException();
                boolean hasIdentity=line.startsWith("PAD2 ");
                if(!hasIdentity&&!line.startsWith("PAD "))throw new IOException();
                String[] f=line.split(" ",hasIdentity?10:9);
                if(f.length!=(hasIdentity?10:9))throw new IOException();
                if(!f[4].equals("-1")&&(on||output==Output.EDEN&&f[3].equals("1")))
                    motionSources.add(Integer.parseInt(f[1]));
                if(f[1].equals(Integer.toString(STEAM_PAD_ID))&&!f[4].equals("-1"))steamAssigned=true;
                found.add(new Pad(Integer.parseInt(f[1]),Integer.parseInt(f[2]),f[3].equals("1"),
                    Integer.parseInt(f[6]),Integer.parseInt(f[7]),f[hasIdentity?9:8],
                    hasIdentity&&!f[8].equals("0000000000000000")?f[8]:""));
            }
            throw new IOException();
        }catch(IOException|RuntimeException e){lost();}
    }
    void accept(ArrayList<Pad> found,boolean on,boolean user,boolean local){
        if(closed)return;
        boolean updated=!online||active!=on||externalOnly!=local||user||found.size()!=pads.size();
        if(!updated)for(int i=0;i<found.size();i++)if(found.get(i).id!=pads.get(i).id||found.get(i).product!=pads.get(i).product||!found.get(i).identity.equals(pads.get(i).identity)){updated=true;break;}
        boolean wasActive=active;online=true;active=on;externalOnly=local;
        if(user){busy=false;error="";}
        if(!updated)return;
        pads.clear();pads.addAll(found);
        for(int s=0;s<4;s++){
            int[] a=seats[s];if(a==null)continue;
            int[] live=Arrays.stream(a).filter(id->find(id)!=null).toArray();
            seats[s]=live.length==0?null:live;
        }
        fillSeats();
        if(wasActive!=on)getSystemService(NotificationManager.class).notify(1,notification(on));
        if(!busy&&((on&&!user)||(!on&&routingWanted&&error.isEmpty())))sendRoute();
        changed();
    }
    private void fillSeats(){
        SharedPreferences settings=getSharedPreferences("routing",MODE_PRIVATE);
        // Saved assignments describe Club players, independently of Android controller numbers.
        // Keep disconnected players reserved so reconnect order cannot steal their slots.
        Set<Integer> reserved=new HashSet<>();
        for(Map.Entry<String,?> entry:settings.getAll().entrySet())
            if(entry.getKey().startsWith(ASSIGNMENT)&&entry.getValue() instanceof Integer){
                int slot=(Integer)entry.getValue();
                if(slot>=0&&slot<4)reserved.add(slot);
            }
        Arrays.fill(seats,null);
        omitted.clear();
        for(Pad p:pads){
            int slot=settings.getInt(assignmentKey(p),-2);
            if(slot==-1)omitted.add(p.id);
            else if(slot>=0&&slot<4){
                int[] previous=seats[slot];
                if(previous==null)seats[slot]=new int[]{p.id};
                else if(previous.length==1&&p.side!=0&&find(previous[0]).side==3-p.side)
                    seats[slot]=new int[]{previous[0],p.id};
            }
        }
        int steamSeat=getSharedPreferences("steam",MODE_PRIVATE).getInt("seat",-1);
        Pad steamPad=find(STEAM_PAD_ID);
        if(steamPad!=null&&!settings.contains(assignmentKey(steamPad))&&steamSeat>=0&&steamSeat<4
                &&seats[steamSeat]==null)seats[steamSeat]=new int[]{STEAM_PAD_ID};
        ArrayList<Pad> defaults=new ArrayList<>(pads);
        defaults.sort(Comparator.comparing((Pad p)->!p.builtin).thenComparingInt(p->p.id));
        for(Pad p:defaults)if(!omitted.contains(p.id)&&seat(p.id)<0){
            for(int slot=0;slot<4;slot++)if(seats[slot]==null&&!reserved.contains(slot)){
                seats[slot]=new int[]{p.id};break;
            }
        }
    }
    private String assignmentKey(Pad p){
        if(p.builtin)return ASSIGNMENT+"thor";
        if(p.id==STEAM_PAD_ID)return ASSIGNMENT+"steam";
        return ASSIGNMENT+(p.identity.isEmpty()?p.vendor+":"+p.product+":"+p.name:p.identity);
    }
    Pad find(int id){for(Pad p:pads)if(p.id==id)return p;return null;}
    int seat(int id){for(int s=0;s<4;s++)if(seats[s]!=null)for(int x:seats[s])if(x==id)return s;return -1;}
    int open(){for(int i=0;i<4;i++)if(seats[i]==null)return i;return -1;}
    boolean canRoute(){return online;}
    boolean any(){for(int[] a:seats)if(a!=null)return true;return false;}
    int pairSeat(){for(int i=0;i<4;i++)if(seats[i]!=null&&seats[i].length==2)return i;return -1;}
    void assign(int s,Pad p){
        int prior=seat(p.id);
        if(prior==s && seats[s].length==1)return;
        int[] previous=seats[s];
        if(prior>=0 && prior!=s && seats[prior].length==1){seats[prior]=previous;}
        else {
            if(prior>=0 && prior!=s)seats[prior]=Arrays.stream(seats[prior]).filter(id->id!=p.id).toArray();
            if(previous!=null)for(int id:previous)if(id!=p.id)omitted.add(id);
        }
        omitted.remove(p.id);seats[s]=new int[]{p.id};assignmentChanged();
    }
    void clear(int s){if(seats[s]!=null)for(int id:seats[s])omitted.add(id);seats[s]=null;assignmentChanged();}
    void combine(Pad left,Pad right){
        int s=seat(left.id), other=seat(right.id);
        if(s<0)s=other<0?open():other;
        if(s<0)return;
        for(int i=0;i<4;i++)if(seats[i]!=null){
            int[] filtered=Arrays.stream(seats[i]).filter(id->id!=left.id&&id!=right.id).toArray();
            seats[i]=filtered.length==0?null:filtered;
        }
        omitted.remove(left.id);omitted.remove(right.id);seats[s]=new int[]{left.id,right.id};assignmentChanged();
    }
    void split(int s){
        int next=open();
        if(s<0||next<0||seats[s]==null||seats[s].length!=2)return;
        int[] a=seats[s];seats[s]=new int[]{a[0]};seats[next]=new int[]{a[1]};assignmentChanged();
    }
    void assignmentChanged(){
        SharedPreferences.Editor saved=getSharedPreferences("routing",MODE_PRIVATE).edit();
        // An explicit assignment replaces an older reservation, including an offline pad.
        for(Map.Entry<String,?> entry:getSharedPreferences("routing",MODE_PRIVATE).getAll().entrySet()){
            if(!entry.getKey().startsWith(ASSIGNMENT)||!(entry.getValue() instanceof Integer))continue;
            int slot=(Integer)entry.getValue();
            if(slot<0||slot>=4||seats[slot]==null)continue;
            boolean retained=false;
            for(int id:seats[slot])if(entry.getKey().equals(assignmentKey(find(id))))retained=true;
            if(!retained)saved.putInt(entry.getKey(),-1);
        }
        for(Pad p:pads)saved.putInt(assignmentKey(p),seat(p.id));
        saved.apply();
        int steamSeat=seat(STEAM_PAD_ID);
        if(steamSeat>=0||omitted.contains(STEAM_PAD_ID))
            getSharedPreferences("steam",MODE_PRIVATE).edit().putInt("seat",steamSeat).apply();
        error="";
        if(active){if(canRoute())sendRoute();else stopRouting();}
        changed();
    }
    void selectOutput(Output next){
        if(output==next)return;
        output=next;
        getSharedPreferences("routing",MODE_PRIVATE).edit().putString("output",next.name()).apply();
        if(online)fillSeats();
        if(active){if(canRoute())sendRoute();else stopRouting();}
        else changed();
    }
    void sendRoute(){
        if(!online||!canRoute())return;
        routingWanted=true;
        getSharedPreferences("routing",MODE_PRIVATE).edit().putBoolean("routingWanted",true).apply();
        StringBuilder cmd=new StringBuilder(output==Output.EDEN?"EDEN":output==Output.PROFILES?"ROUTE":"XBOX");
        for(int s=0;s<4;s++)if(seats[s]!=null)for(int id:seats[s])
            cmd.append(' ').append(id).append(':').append(s).append(':').append(seats[s].length==2?1:0);
        busy=true;changed();worker.post(()->request(cmd.toString(),true));
    }
    void stopRouting(){
        routingWanted=false;
        getSharedPreferences("routing",MODE_PRIVATE).edit().putBoolean("routingWanted",false).apply();
        if(!online)return;
        busy=true;changed();worker.post(()->request("OFF",true));
    }
    @Override public void onDestroy(){
        closed=true;
        if(current==this)current=null;
        closeConnection();
        java.lang.Process process=localRouter;if(process!=null)process.destroy();
        worker.removeCallbacksAndMessages(null);worker.post(()->{motion.close();steam.close();});changed();super.onDestroy();
    }
}
