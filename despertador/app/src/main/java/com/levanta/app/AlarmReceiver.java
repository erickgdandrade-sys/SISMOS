package com.levanta.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Recebe o disparo do AlarmManager e liga o serviço que toca/vibra. */
public class AlarmReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context ctx, Intent intent) {
        int id = intent.getIntExtra(Scheduler.EXTRA_ID, 0);
        Store s = new Store(ctx);
        boolean fresh;
        if (id == Scheduler.ID_SNOOZE) {
            s.setSnoozeUntil(0);
            fresh = false;
        } else if (id == Scheduler.ID_RESUME) {
            if (!s.active()) return;
            fresh = false;
        } else if (id == Scheduler.ID_MIRROR) {
            s.setMirrorTime(0);
            fresh = true;
        } else {
            Alarm a = s.find(id);
            if (a == null || !a.enabled) return;
            if (a.days == 0) {
                a.enabled = false;
                s.put(a);
            }
            fresh = true;
        }
        Scheduler.scheduleAll(ctx);
        Intent svc = new Intent(ctx, AlarmService.class)
                .setAction(AlarmService.ACTION_RING)
                .putExtra(AlarmService.EXTRA_FRESH, fresh);
        try {
            ctx.startForegroundService(svc);
        } catch (Exception e) {
            // Se o sistema bloquear agora, tenta de novo em alguns segundos.
            if (s.active() || fresh) {
                if (!s.active()) s.startSession();
                Scheduler.resumeSoon(ctx, 5000);
            }
        }
    }
}
