package club.thor.pads;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.provider.DocumentsContract;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Manages the two player mappings for Mario Kart Wii's US release via Dolphin's document provider. */
final class MarioKartSetup {
    static final String AUTHORITY = "org.dolphinemu.dolphinemu.user";
    private static final String GAME = "root/GameSettings/RMCE01.ini";
    private static final String PROFILE_DIR = "root/Config/Profiles/Wiimote";
    private static String profileName(int player) {
        return "Controller-Club-Mario-Kart-P" + player;
    }
    private static final Pattern CONTROLS = Pattern.compile("(?m)^\\[Controls][ \\t]*\\r?$");
    private final Context context;
    private final ContentResolver resolver;
    private final Uri tree;

    MarioKartSetup(Context context, Uri tree) {
        this.context = context;
        this.resolver = context.getContentResolver();
        this.tree = tree;
    }

    static boolean isDolphinRoot(Uri tree) {
        return AUTHORITY.equals(tree.getAuthority()) && DocumentsContract.isTreeUri(tree)
                && DocumentsContract.getTreeDocumentId(tree).matches("root/?");
    }

    private Uri document(String id) {
        return DocumentsContract.buildDocumentUriUsingTree(tree, id);
    }

    private String read(Uri uri) throws IOException {
        try (InputStream input = resolver.openInputStream(uri)) {
            if (input == null) throw new IOException("Dolphin’s settings could not be read.");
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            for (int n; (n = input.read(buffer)) != -1;) {
                if (output.size() + n > 1024 * 1024) throw new IOException("Settings file is too large.");
                output.write(buffer, 0, n);
            }
            return new String(output.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private void write(Uri uri, String text) throws IOException {
        try (OutputStream output = resolver.openOutputStream(uri, "wt")) {
            if (output == null) throw new IOException("Dolphin’s settings could not be saved.");
            output.write(text.getBytes(StandardCharsets.UTF_8));
        }
    }

    synchronized void apply() throws IOException {
        Uri game = document(GAME);
        File backup = new File(context.getFilesDir(), "mario-kart-rmce01.original.ini");
        String current;
        boolean createdGame = false;
        try {
            current = read(game);
        } catch (FileNotFoundException missing) {
            current = backup.exists() ? new String(Files.readAllBytes(backup.toPath()), StandardCharsets.UTF_8) : "";
            game = DocumentsContract.createDocument(resolver, document("root/GameSettings"), "application/octet-stream", "RMCE01.ini");
            if (game == null) throw new IOException("Dolphin could not create the Mario Kart settings.");
            createdGame = true;
        }
        if (!backup.exists()) {
            File temp = new File(context.getFilesDir(), backup.getName() + ".tmp");
            Files.write(temp.toPath(), current.getBytes(StandardCharsets.UTF_8));
            Files.move(temp.toPath(), backup.toPath(), java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        }
        String next = current;
        for (int player = 1; player <= 2; player++) {
            String filename = profileName(player) + ".ini";
            Uri profile = document(PROFILE_DIR + "/" + filename);
            try {
                read(profile);
            } catch (FileNotFoundException missing) {
                profile = DocumentsContract.createDocument(resolver, document(PROFILE_DIR), "application/octet-stream", filename);
                if (profile == null) throw new IOException("Dolphin could not create the Club profile.");
            }
            write(profile, profileText(player));
            next = setControls(next, "WiimoteProfile" + player, profileName(player));
            next = setControls(next, "WiimoteSource" + (player - 1), "1");
        }
        if (createdGame || !next.equals(current)) {
            try {
                write(game, next);
            } catch (IOException failure) {
                try { write(game, current); }
                catch (IOException rollback) { failure.addSuppressed(rollback); }
                throw failure;
            }
        }
    }

    synchronized boolean restore() throws IOException {
        File backup = new File(context.getFilesDir(), "mario-kart-rmce01.original.ini");
        if (!backup.exists()) return false;
        Uri game = document(GAME);
        String original = new String(Files.readAllBytes(backup.toPath()), StandardCharsets.UTF_8);
        String current = read(game);
        String next = current;
        for (int player = 1; player <= 2; player++) {
            String profileKey = "WiimoteProfile" + player;
            String sourceKey = "WiimoteSource" + (player - 1);
            if (profileName(player).equals(controlValue(current, profileKey))) {
                next = setControls(next, profileKey, controlValue(original, profileKey));
                if ("1".equals(controlValue(current, sourceKey))) {
                    next = setControls(next, sourceKey, controlValue(original, sourceKey));
                }
            }
        }
        if (!next.equals(current)) write(game, next);
        Files.delete(backup.toPath());
        return !next.equals(current);
    }

    private static int sectionEnd(String text, int start) {
        Matcher next = Pattern.compile("(?m)^\\[").matcher(text);
        return next.find(start) ? next.start() : text.length();
    }

    private static Pattern controlLine(String key) {
        return Pattern.compile("(?m)^" + Pattern.quote(key) + "[ \\t]*=[ \\t]*([^\\r\\n]*)(?:\\r?\\n|$)");
    }

    private static String controlValue(String text, String key) {
        Matcher section = CONTROLS.matcher(text);
        if (!section.find()) return null;
        Matcher value = controlLine(key).matcher(text.substring(section.end(), sectionEnd(text, section.end())));
        return value.find() ? value.group(1).trim() : null;
    }

    private static String setControls(String text, String key, String value) {
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

    private static String profileText(int player) {
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
}
