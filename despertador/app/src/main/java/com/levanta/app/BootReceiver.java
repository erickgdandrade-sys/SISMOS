package com.levanta.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Depois de reiniciar / mudar a hora: reagenda tudo. Se o alarme estava tocando, volta a tocar. */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context ctx, Intent intent) {
        Store s = new Store(ctx);
        Scheduler.scheduleAll(ctx);
        Scheduler.syncFromSystem(ctx);
        if (s.active() && s.snoozeUntil() == 0) {
            // Reiniciar o celular não desliga o alarme.
            Scheduler.resumeSoon(ctx, 3000);
        }
    }
}
