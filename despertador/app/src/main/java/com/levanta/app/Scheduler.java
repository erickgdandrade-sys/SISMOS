package com.levanta.app;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import java.util.Calendar;

/** Agenda os alarmes no AlarmManager e espelha os despertadores do app Relógio. */
public final class Scheduler {
    public static final String EXTRA_ID = "id";
    public static final int ID_MIRROR = 100001;
    public static final int ID_SNOOZE = 100002;
    public static final int ID_RESUME = 100003;

    private Scheduler() {}

    /** Próximo disparo (ms) de um alarme a partir de agora. */
    public static long nextTrigger(Alarm a, long now) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(now);
        c.set(Calendar.HOUR_OF_DAY, a.hour);
        c.set(Calendar.MINUTE, a.minute);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        for (int i = 0; i < 8; i++) {
            int dow = c.get(Calendar.DAY_OF_WEEK) - 1; // 0 = domingo
            if (c.getTimeInMillis() > now && (a.days == 0 || (a.days & (1 << dow)) != 0)) {
                return c.getTimeInMillis();
            }
            c.add(Calendar.DAY_OF_MONTH, 1);
        }
        return c.getTimeInMillis();
    }

    /** Reagenda tudo: alarmes do app, soneca pendente e espelho do Relógio. */
    public static void scheduleAll(Context ctx) {
        Store s = new Store(ctx);
        long now = System.currentTimeMillis();
        for (Alarm a : s.alarms()) {
            cancel(ctx, a.id);
            if (a.enabled) setAlarmClock(ctx, a.id, nextTrigger(a, now));
        }
        long snooze = s.snoozeUntil();
        if (s.active() && snooze > 0) {
            setAlarmClock(ctx, ID_SNOOZE, Math.max(snooze, now + 1000));
        } else {
            cancel(ctx, ID_SNOOZE);
        }
        long mirror = s.mirrorTime();
        if (s.syncEnabled() && mirror > now) {
            // Exact (e não setAlarmClock) para não esconder o próximo alarme do Relógio.
            setExact(ctx, ID_MIRROR, mirror);
        } else {
            cancel(ctx, ID_MIRROR);
            if (mirror != 0 && mirror <= now) s.setMirrorTime(0);
        }
    }

    /**
     * Lê o próximo despertador do sistema (o do app Relógio, por exemplo) e cria um
     * "espelho" no mesmo horário. Assim, quando o Relógio tocar, o Levanta! toca junto
     * e só para quando você provar que levantou.
     * @return o horário espelhado, ou 0.
     */
    public static long syncFromSystem(Context ctx) {
        Store s = new Store(ctx);
        if (!s.syncEnabled()) {
            cancel(ctx, ID_MIRROR);
            return 0;
        }
        AlarmManager am = ctx.getSystemService(AlarmManager.class);
        AlarmManager.AlarmClockInfo info = am.getNextAlarmClock();
        long now = System.currentTimeMillis();
        if (info == null) {
            s.setMirrorTime(0);
            cancel(ctx, ID_MIRROR);
            return 0;
        }
        long t = info.getTriggerTime();
        String pkg = null;
        PendingIntent show = info.getShowIntent();
        if (show != null) pkg = show.getCreatorPackage();
        boolean ours = ctx.getPackageName().equals(pkg) || isOurTime(s, t, now);
        if (!ours && t > now) {
            if (s.mirrorTime() != t) {
                s.setMirrorTime(t);
                setExact(ctx, ID_MIRROR, t);
            }
            return t;
        }
        return s.mirrorTime();
    }

    private static boolean isOurTime(Store s, long t, long now) {
        for (Alarm a : s.alarms()) {
            if (a.enabled && Math.abs(nextTrigger(a, now) - t) < 30_000) return true;
        }
        return s.active() && Math.abs(s.snoozeUntil() - t) < 30_000;
    }

    /** Dispara um alarme daqui a alguns segundos (usado para voltar a tocar após reiniciar). */
    public static void resumeSoon(Context ctx, long delayMs) {
        setAlarmClock(ctx, ID_RESUME, System.currentTimeMillis() + delayMs);
    }

    public static boolean canExact(Context ctx) {
        if (Build.VERSION.SDK_INT < 31) return true;
        return ctx.getSystemService(AlarmManager.class).canScheduleExactAlarms();
    }

    private static PendingIntent op(Context ctx, int id) {
        Intent i = new Intent(ctx, AlarmReceiver.class).putExtra(EXTRA_ID, id);
        return PendingIntent.getBroadcast(ctx, id, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static void setAlarmClock(Context ctx, int id, long when) {
        AlarmManager am = ctx.getSystemService(AlarmManager.class);
        PendingIntent show = PendingIntent.getActivity(ctx, 0,
                new Intent(ctx, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
        try {
            am.setAlarmClock(new AlarmManager.AlarmClockInfo(when, show), op(ctx, id));
        } catch (SecurityException e) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when, op(ctx, id));
        }
    }

    private static void setExact(Context ctx, int id, long when) {
        AlarmManager am = ctx.getSystemService(AlarmManager.class);
        try {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when, op(ctx, id));
        } catch (SecurityException e) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when, op(ctx, id));
        }
    }

    public static void cancel(Context ctx, int id) {
        ctx.getSystemService(AlarmManager.class).cancel(op(ctx, id));
    }
}
