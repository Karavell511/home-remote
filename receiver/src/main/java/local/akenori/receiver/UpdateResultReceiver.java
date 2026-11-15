package local.akenori.receiver;

import android.content.*;

public class UpdateResultReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context,Intent intent) { new ReceiverUpdater(context).result(intent); }
}
