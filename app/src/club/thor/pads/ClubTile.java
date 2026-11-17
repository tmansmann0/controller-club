package club.thor.pads;

import android.app.ActivityOptions;
import android.app.PendingIntent;
import android.content.Intent;
import android.os.Build;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

public final class ClubTile extends TileService {
    @Override public void onStartListening() {
        Tile tile = getQsTile();
        if (tile == null) return;
        tile.setState(Tile.STATE_INACTIVE);
        if (Build.VERSION.SDK_INT >= 29) tile.setSubtitle("Open controller lineup");
        tile.updateTile();
    }

    @Override public void onClick() {
        super.onClick();
        if (isLocked()) unlockAndRun(this::openClub);
        else openClub();
    }

    @SuppressWarnings("deprecation")
    private void openClub() {
        Intent intent = new Intent(this, MainActivity.class)
                .setAction(MainActivity.OPEN_ON_LOWER)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (Build.VERSION.SDK_INT >= 34) {
            ActivityOptions options = ActivityOptions.makeBasic();
            int lower = MainActivity.lowerDisplayId(this);
            if (lower >= 0) options.setLaunchDisplayId(lower);
            startActivityAndCollapse(PendingIntent.getActivity(this, 41, intent,
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT, options.toBundle()));
        } else {
            startActivityAndCollapse(intent);
        }
    }
}
