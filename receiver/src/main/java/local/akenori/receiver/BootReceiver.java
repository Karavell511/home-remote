package local.akenori.receiver;

import android.content.*;

/** Never opens a screen or resets the saved pairing on boot/update. */
public class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context,Intent intent) {
        if(Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction()) || Intent.ACTION_MY_PACKAGE_REPLACED.equals(intent.getAction())) ReceiverService.start(context);
    }
}
