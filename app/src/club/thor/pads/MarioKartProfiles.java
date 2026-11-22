package club.thor.pads;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Pure profile generation and surgical per-player edits for Mario Kart Wii. */
final class MarioKartProfiles {
    static String profileName(int player) {
        return "Controller-Club-Mario-Kart-P" + player;
    }
    static final Pattern CONTROLS = Pattern.compile("(?m)^\\[Controls][ \\t]*\\r?$");
    static int sectionEnd(String text, int start) {
        Matcher next = Pattern.compile("(?m)^\\[").matcher(text);
        return next.find(start) ? next.start() : text.length();
    }

    static Pattern controlLine(String key) {
        return Pattern.compile("(?m)^" + Pattern.quote(key) + "[ \\t]*=[ \\t]*([^\\r\\n]*)(?:\\r?\\n|$)");
    }

    static String controlValue(String text, String key) {
        Matcher section = CONTROLS.matcher(text);
        if (!section.find()) return null;
        Matcher value = controlLine(key).matcher(text.substring(section.end(), sectionEnd(text, section.end())));
        return value.find() ? value.group(1).trim() : null;
    }

    static String setControls(String text, String key, String value) {
        Matcher section = CONTROLS.matcher(text);
        if (!section.find()) return value == null ? text : text + "\n[Controls]\n" + key + " = " + value + "\n";
        int start = section.end(), end = sectionEnd(text, start);
        String body = text.substring(start, end);
        String replacement = value == null ? "" : key + " = " + value + "\n";
        Matcher line = controlLine(key).matcher(body);
        if (line.find()) body = line.replaceFirst(Matcher.quoteReplacement(replacement));
        else if (value != null) body = body + (body.endsWith("\n") ? "" : "\n") + replacement;
        return text.substring(0, start) + body + text.substring(end);
    }

    static String profileText(int player) {
        StringBuilder text = new StringBuilder("[Profile]\nExtension = Classic\n");
        String[][] map = {
            {"Buttons/A","Button A"},{"Buttons/B","Button B"},{"Buttons/X","Button X"},{"Buttons/Y","Button Y"},
            {"Buttons/+","Start"},{"Buttons/-","Select"},{"Buttons/ZL","Button L2"},{"Buttons/ZR","Button R2"},
            {"Triggers/L","Button L1"},{"Triggers/R","Button R1"},
            {"Triggers/L-Analog","Button L1"},{"Triggers/R-Analog","Button R1"},
            {"Left Stick/Up","Axis 1-"},{"Left Stick/Down","Axis 1+"},{"Left Stick/Left","Axis 0-"},{"Left Stick/Right","Axis 0+"},
            {"Right Stick/Up","Axis 14-"},{"Right Stick/Down","Axis 14+"},{"Right Stick/Left","Axis 11-"},{"Right Stick/Right","Axis 11+"},
            {"D-Pad/Up","Axis 16-"},{"D-Pad/Down","Axis 16+"},{"D-Pad/Left","Axis 15-"},{"D-Pad/Right","Axis 15+"}
        };
        for (String[] binding : map) {
            text.append("Classic/").append(binding[0]).append(" = ");
            // Dolphin's Android device qualifier includes the transient controller number.
            for (int number = 1; number <= 24; number++) {
                if (number > 1) text.append(" | ");
                text.append("`Android/").append(number).append("/Controller Club P").append(player).append(":").append(binding[1]).append("`");
            }
            text.append('\n');
        }
        return text.toString();
    }

    static String selectPlayers(String current,int players){
        if(players<1||players>4)throw new IllegalArgumentException("Choose one to four players.");
        for(int player=1;player<=players;player++){
            current=setControls(current,"WiimoteProfile"+player,profileName(player));
            current=setControls(current,"WiimoteSource"+(player-1),"1");
        }
        return current;
    }
    static String restorePlayer(String current,String original,int player){
        String profile="WiimoteProfile"+player,source="WiimoteSource"+(player-1);
        if(!profileName(player).equals(controlValue(current,profile)))return current;
        String next=setControls(current,profile,controlValue(original,profile));
        if("1".equals(controlValue(current,source)))next=setControls(next,source,controlValue(original,source));
        return next;
    }
}
