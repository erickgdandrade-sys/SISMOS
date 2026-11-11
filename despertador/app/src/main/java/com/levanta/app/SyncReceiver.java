package com.levanta.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** O próximo despertador do celular mudou (ex.: você criou/alterou um alarme no app Relógio). */
public class SyncReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context ctx, Intent intent) {
        Scheduler.syncFromSystem(ctx);
    }
}
