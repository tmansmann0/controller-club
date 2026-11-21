package club.thor.pads;
public final class BatteryReadingTest {
    static void check(String wanted,String actual){if(!wanted.equals(actual))throw new AssertionError(actual);}
    public static void main(String[] args){
        check("Battery unavailable",BatteryReading.label(-1,1));
        check("0%",BatteryReading.label(0,3));
        check("67% · Charging",BatteryReading.label(67,2));
        check("Charging",BatteryReading.label(-1,2));
        check("100% · Full",BatteryReading.label(100,5));
        check("42% · Not charging",BatteryReading.label(42,4));
        check("Battery unavailable",BatteryReading.label(150,1));
        System.out.println("Battery percentage, unknown readings, charging and full state: pass");
    }
}
