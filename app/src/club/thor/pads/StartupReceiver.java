package club.thor.pads;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Restores the input bridge after Android unlocks the user's app storage. */
public final class StartupReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        String action=intent.getAction();
        if(Intent.ACTION_BOOT_COMPLETED.equals(action)||Intent.ACTION_MY_PACKAGE_REPLACED.equals(action))
            context.startForegroundService(new Intent(context,Bridge.class));
    }
}
