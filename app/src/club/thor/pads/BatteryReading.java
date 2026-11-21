package club.thor.pads;

/** Android BatteryManager and BatteryState share these documented status values. */
final class BatteryReading {
    static String label(int percent,int status){
        String level=percent>=0&&percent<=100?percent+"%":"Battery unavailable";
        if(status==2)return (percent>=0&&percent<=100?percent+"% · ":"")+"Charging";
        if(status==5)return (percent>=0&&percent<=100?percent+"% · ":"")+"Full";
        if(status==4)return level+" · Not charging";
        return level;
    }
}
