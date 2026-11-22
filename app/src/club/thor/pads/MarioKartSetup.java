package club.thor.pads;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.provider.DocumentsContract;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import static club.thor.pads.MarioKartProfiles.*;

/** Manages selected player mappings for Mario Kart Wii's US release via Dolphin's document provider. */
final class MarioKartSetup {
    static final String AUTHORITY = "org.dolphinemu.dolphinemu.user";
    private static final String GAME = "root/GameSettings/RMCE01.ini";
    private static final String PROFILE_DIR = "root/Config/Profiles/Wiimote";
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

    synchronized void apply(int players) throws IOException {
        if(players<1||players>4)throw new IllegalArgumentException("Choose one to four players.");
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
        for (int player = 1; player <= players; player++) {
            File playerBackup=new File(context.getFilesDir(),"mario-kart-player-"+player+".original.ini");
            if(!playerBackup.exists()){
                String before=profileName(player).equals(controlValue(current,"WiimoteProfile"+player))
                    ?new String(Files.readAllBytes(backup.toPath()),StandardCharsets.UTF_8):current;
                Files.write(playerBackup.toPath(),before.getBytes(StandardCharsets.UTF_8));
            }
            String filename = profileName(player) + ".ini";
            Uri profile = document(PROFILE_DIR + "/" + filename);
            try {
                read(profile);
            } catch (FileNotFoundException missing) {
                profile = DocumentsContract.createDocument(resolver, document(PROFILE_DIR), "application/octet-stream", filename);
                if (profile == null) throw new IOException("Dolphin could not create the Club profile.");
            }
            write(profile, profileText(player));

        }
        next=selectPlayers(current,players);
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
        for (int player = 1; player <= 4; player++) {
            File playerBackup=new File(context.getFilesDir(),"mario-kart-player-"+player+".original.ini");
            String before=playerBackup.exists()?new String(Files.readAllBytes(playerBackup.toPath()),StandardCharsets.UTF_8):original;
            next=restorePlayer(next,before,player);
        }
        if (!next.equals(current)) write(game, next);
        Files.delete(backup.toPath());
        for(int player=1;player<=4;player++)Files.deleteIfExists(new File(context.getFilesDir(),"mario-kart-player-"+player+".original.ini").toPath());
        return !next.equals(current);
    }

}
