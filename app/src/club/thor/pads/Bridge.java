package club.thor.pads;

import android.app.*;
import android.content.*;
import android.os.*;
import android.view.InputDevice;
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
    volatile boolean closed;
    String error="";
    Handler worker;
    HandlerThread thread;
    private SteamController steam;
    volatile Socket socket;
    volatile java.lang.Process localRouter;
    volatile boolean usingLocal;
    boolean externalOnly;
    enum Output {
        XBOX("Xbox controllers"), PROFILES("Dolphin / Cemu profiles"), EDEN("Eden controllers");
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
        String mode=settings.getString("output",settings.getBoolean("xboxOutput",true)?"XBOX":"PROFILES");
        try{output=Output.valueOf(mode);}catch(IllegalArgumentException ignored){}
        NotificationManager nm=getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel(CHANNEL,"Controller routing",NotificationManager.IMPORTANCE_LOW));
        startForeground(1,notification(false));
        thread=new HandlerThread("Controller Club bridge");thread.start();worker=new Handler(thread.getLooper());
        steam=new SteamController(this,worker);
        worker.post(poll);changed();
    }
    @Override public int onStartCommand(Intent i,int flags,int id){
        if(i!=null && "OFF".equals(i.getAction()))stopRouting();
        return START_NOT_STICKY;
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
        steam.tick();
        if(!closed)worker.postDelayed(this,1200);
    }};
    private void connectRouter() throws IOException {
        File shellToken=new File(getFilesDir(),"session.token");
        if(shellToken.isFile()){
            String token=new String(Files.readAllBytes(shellToken.toPath()),StandardCharsets.US_ASCII).trim();
            if(token.matches("[a-f0-9]{64}")){
                try{
                    usingLocal=false;
                    connect(37819,token);
                    return;
                }catch(IOException ignored){closeConnection();}
            }
        }
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
            ArrayList<Pad> found=new ArrayList<>();boolean on=first.endsWith(" 1");
            boolean steamAssigned=false;
            for(int i=0;i<=25;i++){
                String line=reader.readLine();
                if("END".equals(line)){
                    steam.setRouting(on&&steamAssigned);
                    boolean local=usingLocal&&found.stream().noneMatch(pad->pad.builtin);
                    MAIN.post(()->accept(found,on,user,local));return;
                }
                if(line==null)throw new EOFException();
                boolean hasIdentity=line.startsWith("PAD2 ");
                if(!hasIdentity&&!line.startsWith("PAD "))throw new IOException();
                String[] f=line.split(" ",hasIdentity?10:9);
                if(f.length!=(hasIdentity?10:9))throw new IOException();
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
        if(on&&!user){ if(canRoute())sendRoute();else stopRouting(); }
        changed();
    }
    private void fillSeats(){
        if(directP1()){
            if(seats[0]!=null){
                int next=open();
                if(next>=0&&Arrays.stream(seats[0]).noneMatch(id->find(id).builtin))seats[next]=seats[0];
                seats[0]=null;
            }
            for(int s=1;s<4;s++)if(seats[s]!=null&&find(seats[s][0]).builtin)seats[s]=null;
        }
        int steamSeat=getSharedPreferences("steam",MODE_PRIVATE).getInt("seat",-1);
        if(steamSeat>=(directP1()?1:0)&&steamSeat<4&&!omitted.contains(STEAM_PAD_ID)
                &&find(STEAM_PAD_ID)!=null&&seat(STEAM_PAD_ID)<0&&seats[steamSeat]==null)
            seats[steamSeat]=new int[]{STEAM_PAD_ID};
        ArrayList<Pad> defaults=new ArrayList<>(pads);
        defaults.sort(Comparator.comparing((Pad p)->!p.builtin).thenComparingInt(p->p.id));
        for(Pad p:defaults)
            if((!directP1()||!p.builtin)&&!omitted.contains(p.id)&&seat(p.id)<0){
                int s=open();
                if(s>=0)seats[s]=new int[]{p.id};
            }
    }
    Pad find(int id){for(Pad p:pads)if(p.id==id)return p;return null;}
    int seat(int id){for(int s=0;s<4;s++)if(seats[s]!=null)for(int x:seats[s])if(x==id)return s;return -1;}
    boolean directP1(){return externalOnly;}
    private boolean hasThor(){
        for(int id:InputDevice.getDeviceIds()){
            InputDevice device=InputDevice.getDevice(id);
            if(device!=null&&"Odin Controller".equals(device.getName()))return true;
        }
        return false;
    }
    int open(){for(int i=directP1()?1:0;i<4;i++)if(seats[i]==null)return i;return -1;}
    boolean canRoute(){return any()||directP1()&&hasThor();}
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
        StringBuilder cmd=new StringBuilder(output==Output.EDEN?"EDEN":!any()?"DIRECT":output==Output.PROFILES?"ROUTE":"XBOX");
        for(int s=0;s<4;s++)if(seats[s]!=null)for(int id:seats[s])
            cmd.append(' ').append(id).append(':').append(s).append(':').append(seats[s].length==2?1:0);
        busy=true;changed();worker.post(()->request(cmd.toString(),true));
    }
    void stopRouting(){
        if(!online)return;
        busy=true;changed();worker.post(()->request("OFF",true));
    }
    @Override public void onDestroy(){
        closed=true;
        if(current==this)current=null;
        closeConnection();
        java.lang.Process process=localRouter;if(process!=null)process.destroy();
        worker.removeCallbacksAndMessages(null);worker.post(steam::close);changed();super.onDestroy();
    }
}
