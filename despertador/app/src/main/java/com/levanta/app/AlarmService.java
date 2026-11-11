package com.levanta.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.media.ToneGenerator;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.VibrationAttributes;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;

/**
 * Serviço em primeiro plano que toca a música, vibra e, no 3º toque, mantém a tela
 * do alarme na frente de tudo até você provar que levantou.
 */
public class AlarmService extends Service {
    public static final String ACTION_RING = "com.levanta.app.RING";
    public static final String ACTION_SNOOZE = "com.levanta.app.SNOOZE";
    public static final String ACTION_MUTE = "com.levanta.app.MUTE";
    public static final String ACTION_DISMISS = "com.levanta.app.DISMISS";
    public static final String ACTION_EMERGENCY = "com.levanta.app.EMERGENCY";
    public static final String EXTRA_FRESH = "fresh";

    private static final String CHANNEL = "alarme_tocando";
    private static final int NOTIF_ID = 42;
    private static final long EMERGENCY_PAUSE_MS = 3 * 60_000;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private MediaPlayer player;
    private ToneGenerator tone;
    private Vibrator vibrator;
    private PowerManager.WakeLock wakeLock;
    private boolean ringing;
    private long lastVibration;
    private long emergencyUntil;
    private Store store;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (!ringing) return;
            long now = System.currentTimeMillis();
            boolean locked = store.locked();
            // Vibração infinita: reforça periodicamente (alguns aparelhos param sozinhos).
            if (now - lastVibration > 15_000) startVibration();
            if (!store.muted()) {
                if (player == null && tone != null) tone.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 900);
                if (locked) forceVolume(1f); // não adianta abaixar o volume no 3º toque
            }
            // Modo travado: se você sair da tela do alarme, ela volta na hora.
            if (locked && !RingActivity.visible && now > emergencyUntil) openRingScreen();
            handler.postDelayed(this, 1000);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        store = new Store(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent != null && intent.getAction() != null ? intent.getAction() : ACTION_RING;
        switch (action) {
            case ACTION_SNOOZE:
                snooze();
                break;
            case ACTION_MUTE:
                if (store.locked()) {
                    store.setMuted(true);
                    stopSound();
                    updateNotification();
                }
                break;
            case ACTION_DISMISS:
                dismiss();
                break;
            case ACTION_EMERGENCY:
                emergencyUntil = System.currentTimeMillis() + EMERGENCY_PAUSE_MS;
                break;
            case ACTION_RING:
            default:
                if (intent == null && !store.active()) { // reinício automático sem alarme ativo
                    stopSelf();
                    return START_NOT_STICKY;
                }
                if (!store.active()) store.startSession();
                store.setSnoozeUntil(0);
                Scheduler.cancel(this, Scheduler.ID_SNOOZE);
                startRinging();
                break;
        }
        return START_STICKY;
    }

    private void startRinging() {
        try {
            Notification n = buildNotification();
            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
            } else {
                startForeground(NOTIF_ID, n);
            }
        } catch (Exception e) {
            Scheduler.resumeSoon(this, 5000);
            stopSelf();
            return;
        }
        ringing = true;
        if (wakeLock == null) {
            PowerManager pm = getSystemService(PowerManager.class);
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "levanta:alarme");
            wakeLock.setReferenceCounted(false);
        }
        wakeLock.acquire(6 * 60 * 60_000L);

        int ring = store.ring();
        forceVolume(ring >= Store.MAX_RINGS ? 1f : (ring == 2 ? 0.85f : 0.6f));
        if (!store.muted()) startSound();
        startVibration();
        openRingScreen();
        handler.removeCallbacks(tick);
        handler.postDelayed(tick, 1000);
    }

    private void snooze() {
        if (!ringing || store.ring() >= Store.MAX_RINGS) return; // no 3º toque não tem soneca
        store.setRing(store.ring() + 1);
        store.setSnoozeUntil(System.currentTimeMillis() + store.snoozeMinutes() * 60_000L);
        Scheduler.scheduleAll(this);
        stopEverything();
    }

    private void dismiss() {
        store.endSession();
        Scheduler.scheduleAll(this);
        stopEverything();
    }

    private void stopEverything() {
        ringing = false;
        handler.removeCallbacks(tick);
        stopSound();
        stopVibration();
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    // ---------- tela ----------

    private void openRingScreen() {
        try {
            Intent i = new Intent(this, RingActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                            | Intent.FLAG_ACTIVITY_SINGLE_TOP
                            | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            startActivity(i);
        } catch (Exception ignored) {
            // Sem permissão de sobreposição a notificação em tela cheia faz esse papel.
        }
    }

    // ---------- som ----------

    private void startSound() {
        if (player != null || tone != null) return;
        AudioAttributes attrs = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();
        Uri uri = RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_ALARM);
        if (uri == null) uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
        if (uri == null) uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
        try {
            player = new MediaPlayer();
            player.setAudioAttributes(attrs);
            player.setDataSource(this, uri);
            player.setLooping(true);
            player.prepare();
            player.start();
        } catch (Exception e) {
            if (player != null) player.release();
            player = null;
            try {
                tone = new ToneGenerator(AudioManager.STREAM_ALARM, 100);
            } catch (Exception ignored) {
                tone = null;
            }
        }
    }

    private void stopSound() {
        if (player != null) {
            try {
                player.stop();
            } catch (Exception ignored) {
            }
            player.release();
            player = null;
        }
        if (tone != null) {
            tone.release();
            tone = null;
        }
    }

    private void forceVolume(float fraction) {
        try {
            AudioManager am = getSystemService(AudioManager.class);
            int max = am.getStreamMaxVolume(AudioManager.STREAM_ALARM);
            int target = Math.max(1, Math.round(max * fraction));
            if (am.getStreamVolume(AudioManager.STREAM_ALARM) < target) {
                am.setStreamVolume(AudioManager.STREAM_ALARM, target, 0);
            }
        } catch (Exception ignored) {
        }
    }

    // ---------- vibração ----------

    private void startVibration() {
        if (vibrator == null) {
            if (Build.VERSION.SDK_INT >= 31) {
                vibrator = getSystemService(VibratorManager.class).getDefaultVibrator();
            } else {
                vibrator = getSystemService(Vibrator.class);
            }
        }
        if (vibrator == null) return;
        VibrationEffect effect = VibrationEffect.createWaveform(new long[]{0, 1000, 350}, 0);
        if (Build.VERSION.SDK_INT >= 33) {
            vibrator.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM));
        } else {
            vibrator.vibrate(effect, new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM).build());
        }
        lastVibration = System.currentTimeMillis();
    }

    private void stopVibration() {
        if (vibrator != null) vibrator.cancel();
    }

    // ---------- notificação ----------

    private Notification buildNotification() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        NotificationChannel ch = new NotificationChannel(CHANNEL, "Alarme tocando",
                NotificationManager.IMPORTANCE_HIGH);
        ch.setSound(null, null);
        ch.enableVibration(false);
        ch.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        ch.setBypassDnd(true);
        nm.createNotificationChannel(ch);

        Intent open = new Intent(this, RingActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent pi = PendingIntent.getActivity(this, 1, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        int ring = store.ring();
        String text = ring >= Store.MAX_RINGS
                ? "Celular travado. Prove que levantou para desligar."
                : "Toque " + ring + " de " + Store.MAX_RINGS + ". Levanta!";
        return new Notification.Builder(this, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle("⏰ Hora de levantar!")
                .setContentText(text)
                .setCategory(Notification.CATEGORY_ALARM)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setOngoing(true)
                .setContentIntent(pi)
                .setFullScreenIntent(pi, true)
                .build();
    }

    private void updateNotification() {
        if (ringing) getSystemService(NotificationManager.class).notify(NOTIF_ID, buildNotification());
    }

    @Override
    public void onDestroy() {
        boolean wasRinging = ringing;
        ringing = false;
        handler.removeCallbacks(tick);
        stopSound();
        stopVibration();
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        // Se o sistema matar o serviço no meio do alarme, ele volta.
        if (wasRinging && store.active() && store.snoozeUntil() == 0) Scheduler.resumeSoon(this, 3000);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
