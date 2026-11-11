package com.levanta.app;

import android.Manifest;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.NotificationManager;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Tela inicial: alarmes, sincronização com o Relógio, configurações e permissões. */
public class MainActivity extends Activity {
    private static final int BG = Color.rgb(17, 17, 27);
    private static final int CARD = Color.rgb(32, 32, 48);
    private static final int ACCENT = Color.rgb(255, 196, 0);
    private static final int GREEN = Color.rgb(46, 160, 67);

    private Store store;
    private LinearLayout content;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        store = new Store(this);
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(BG);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(16), dp(32), dp(16), dp(32));
        scroll.addView(content);
        setContentView(scroll);

        List<String> ask = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= 33 && !granted(Manifest.permission.POST_NOTIFICATIONS)) {
            ask.add(Manifest.permission.POST_NOTIFICATIONS);
        }
        if (Build.VERSION.SDK_INT >= 29 && !granted(Manifest.permission.ACTIVITY_RECOGNITION)) {
            ask.add(Manifest.permission.ACTIVITY_RECOGNITION);
        }
        if (!ask.isEmpty()) requestPermissions(ask.toArray(new String[0]), 1);
    }

    @Override
    protected void onResume() {
        super.onResume();
        Scheduler.scheduleAll(this);
        Scheduler.syncFromSystem(this);
        render();
        // Se um alarme está tocando, volta para a tela dele.
        if (store.active() && store.snoozeUntil() == 0) {
            startActivity(new Intent(this, RingActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        render();
    }

    // ---------- tela ----------

    private void render() {
        content.removeAllViews();

        TextView title = label("Levanta! ⏰", 34, ACCENT, true);
        content.addView(title);
        content.addView(label("O despertador que só para quando você levantar. "
                + Store.MAX_RINGS + "º alarme = celular travado e vibrando até você provar.", 15, Color.LTGRAY, false));

        renderPermissions();
        renderAlarms();
        renderSync();
        renderSettings();

        Button test = button("▶  Testar agora", Color.rgb(70, 70, 95));
        test.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle("Testar o alarme?")
                .setMessage("Ele vai tocar de verdade. Para desligar você vai ter que andar "
                        + store.stepsRequired() + " passos" + (store.mathEnabled() ? " e resolver a conta." : "."))
                .setPositiveButton("Testar", (d, w) -> startForegroundService(new Intent(this, AlarmService.class)
                        .setAction(AlarmService.ACTION_RING).putExtra(AlarmService.EXTRA_FRESH, true)))
                .setNegativeButton("Cancelar", null)
                .show());
        content.addView(test);
    }

    private void renderPermissions() {
        LinearLayout card = card("Permissões necessárias");
        boolean allOk = true;

        boolean notif = Build.VERSION.SDK_INT < 33 || granted(Manifest.permission.POST_NOTIFICATIONS);
        allOk &= permRow(card, "Notificações", notif, () -> openAppSettings());

        boolean exact = Scheduler.canExact(this);
        allOk &= permRow(card, "Alarmes no horário exato", exact, () -> {
            if (Build.VERSION.SDK_INT >= 31) {
                tryStart(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, pkgUri()));
            }
        });

        boolean full = true;
        if (Build.VERSION.SDK_INT >= 34) full = getSystemService(NotificationManager.class).canUseFullScreenIntent();
        allOk &= permRow(card, "Abrir em tela cheia no bloqueio", full, () -> {
            if (Build.VERSION.SDK_INT >= 34) {
                tryStart(new Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, pkgUri()));
            }
        });

        boolean overlay = Settings.canDrawOverlays(this);
        allOk &= permRow(card, "Sobrepor outros apps (para TRAVAR o celular)", overlay,
                () -> tryStart(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, pkgUri())));

        boolean steps = Build.VERSION.SDK_INT < 29 || granted(Manifest.permission.ACTIVITY_RECOGNITION);
        allOk &= permRow(card, "Contar passos (atividade física)", steps, () -> {
            if (Build.VERSION.SDK_INT >= 29) requestPermissions(new String[]{Manifest.permission.ACTIVITY_RECOGNITION}, 2);
        });

        boolean battery = getSystemService(PowerManager.class).isIgnoringBatteryOptimizations(getPackageName());
        allOk &= permRow(card, "Não ser fechado pela economia de bateria", battery, () ->
                tryStart(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, pkgUri())));

        if (allOk) {
            card.removeAllViews();
            card.addView(label("✅ Tudo pronto. Todas as permissões estão ativas.", 15, Color.rgb(120, 220, 140), false));
        }
        content.addView(card);
    }

    private boolean permRow(LinearLayout card, String name, boolean ok, Runnable fix) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(6), 0, dp(6));
        TextView t = label((ok ? "✅ " : "❌ ") + name, 15, Color.WHITE, false);
        t.setGravity(Gravity.START);
        row.addView(t, new LinearLayout.LayoutParams(0, -2, 1f));
        if (!ok) {
            Button b = smallButton("Ativar", GREEN);
            b.setOnClickListener(v -> fix.run());
            row.addView(b);
        }
        card.addView(row);
        return ok;
    }

    private void renderAlarms() {
        LinearLayout card = card("Meus alarmes");
        List<Alarm> alarms = store.alarms();
        Collections.sort(alarms, (a, b) -> (a.hour * 60 + a.minute) - (b.hour * 60 + b.minute));
        if (alarms.isEmpty()) {
            card.addView(label("Nenhum alarme ainda. Crie um abaixo ou use a sincronização com o Relógio.",
                    14, Color.LTGRAY, false));
        }
        for (Alarm a : alarms) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, dp(8), 0, dp(8));

            LinearLayout info = new LinearLayout(this);
            info.setOrientation(LinearLayout.VERTICAL);
            TextView time = label(a.timeText(), 32, a.enabled ? Color.WHITE : Color.GRAY, true);
            time.setGravity(Gravity.START);
            info.addView(time);
            TextView days = label(a.daysText(), 13, Color.LTGRAY, false);
            days.setGravity(Gravity.START);
            info.addView(days);
            info.setOnClickListener(v -> editAlarm(a));
            row.addView(info, new LinearLayout.LayoutParams(0, -2, 1f));

            Switch on = new Switch(this);
            on.setChecked(a.enabled);
            on.setOnCheckedChangeListener((btn, checked) -> {
                a.enabled = checked;
                store.put(a);
                Scheduler.scheduleAll(this);
                render();
            });
            row.addView(on);

            Button del = smallButton("🗑", Color.rgb(90, 40, 40));
            del.setOnClickListener(v -> {
                Scheduler.cancel(this, a.id);
                store.remove(a.id);
                Scheduler.scheduleAll(this);
                render();
            });
            row.addView(del);
            card.addView(row);
        }
        Button add = button("+  Novo alarme", GREEN);
        add.setOnClickListener(v -> {
            Alarm a = new Alarm();
            a.id = store.newId();
            a.hour = 7;
            a.minute = 0;
            editAlarm(a);
        });
        card.addView(add);
        content.addView(card);
    }

    private void editAlarm(Alarm a) {
        new TimePickerDialog(this, (picker, h, m) -> {
            a.hour = h;
            a.minute = m;
            boolean[] checked = new boolean[7];
            for (int i = 0; i < 7; i++) checked[i] = (a.days & (1 << i)) != 0;
            new AlertDialog.Builder(this)
                    .setTitle("Repetir em quais dias? (nenhum = uma vez)")
                    .setMultiChoiceItems(new String[]{"Domingo", "Segunda", "Terça", "Quarta", "Quinta", "Sexta", "Sábado"},
                            checked, (d, which, isChecked) -> checked[which] = isChecked)
                    .setPositiveButton("Salvar", (d, w) -> {
                        int days = 0;
                        for (int i = 0; i < 7; i++) if (checked[i]) days |= 1 << i;
                        a.days = days;
                        a.enabled = true;
                        store.put(a);
                        Scheduler.scheduleAll(this);
                        long next = Scheduler.nextTrigger(a, System.currentTimeMillis());
                        Toast.makeText(this, "Vai tocar " + fmt(next), Toast.LENGTH_LONG).show();
                        render();
                    })
                    .setNegativeButton("Cancelar", null)
                    .show();
        }, a.hour, a.minute, true).show();
    }

    private void renderSync() {
        LinearLayout card = card("Sincronizar com o Relógio do celular");
        Switch sync = new Switch(this);
        sync.setText("Tocar junto com os despertadores do app Relógio");
        sync.setTextColor(Color.WHITE);
        sync.setChecked(store.syncEnabled());
        sync.setOnCheckedChangeListener((btn, checked) -> {
            store.setSyncEnabled(checked);
            Scheduler.syncFromSystem(this);
            Scheduler.scheduleAll(this);
            render();
        });
        card.addView(sync);

        String status;
        long mirror = store.mirrorTime();
        if (!store.syncEnabled()) {
            status = "Desligado.";
        } else if (mirror > System.currentTimeMillis()) {
            status = "🔗 Próximo despertador do Relógio detectado: " + fmt(mirror)
                    + ". O Levanta! vai tocar junto e só para quando você levantar.";
        } else {
            AlarmManager.AlarmClockInfo next = getSystemService(AlarmManager.class).getNextAlarmClock();
            status = next == null
                    ? "Nenhum despertador ativo no Relógio agora."
                    : "Próximo alarme do sistema: " + fmt(next.getTriggerTime()) + " (é um alarme do Levanta!).";
        }
        card.addView(label(status, 14, Color.LTGRAY, false));
        card.addView(label("Dica: o Android só deixa outros apps verem o PRÓXIMO despertador do Relógio. "
                + "O Levanta! acompanha um por um, automaticamente. Quando os dois tocarem, desligue o do "
                + "Relógio normalmente: o do Levanta! continua até você provar que levantou.",
                12, Color.GRAY, false));
        content.addView(card);
    }

    private void renderSettings() {
        LinearLayout card = card("Regras para acordar");
        card.addView(stepper("Soneca", store.snoozeMinutes() + " min", () -> {
            store.setSnoozeMinutes(Math.max(1, store.snoozeMinutes() - 1));
            render();
        }, () -> {
            store.setSnoozeMinutes(Math.min(30, store.snoozeMinutes() + 1));
            render();
        }));
        card.addView(stepper("Passos para provar", String.valueOf(store.stepsRequired()), () -> {
            store.setStepsRequired(Math.max(10, store.stepsRequired() - 10));
            render();
        }, () -> {
            store.setStepsRequired(Math.min(500, store.stepsRequired() + 10));
            render();
        }));
        Switch math = new Switch(this);
        math.setText("Depois dos passos, resolver uma conta de matemática");
        math.setTextColor(Color.WHITE);
        math.setChecked(store.mathEnabled());
        math.setOnCheckedChangeListener((btn, checked) -> store.setMathEnabled(checked));
        card.addView(math);
        card.addView(label("1º e 2º alarme: pode usar a soneca. 3º alarme: sem soneca, celular travado "
                + "na tela do alarme e vibrando sem parar (a música pode ser desligada).", 12, Color.GRAY, false));
        content.addView(card);
    }

    private View stepper(String name, String value, Runnable minus, Runnable plus) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(4), 0, dp(4));
        TextView t = label(name, 15, Color.WHITE, false);
        t.setGravity(Gravity.START);
        row.addView(t, new LinearLayout.LayoutParams(0, -2, 1f));
        Button m = smallButton("−", Color.rgb(70, 70, 95));
        m.setOnClickListener(v -> minus.run());
        row.addView(m);
        TextView val = label(value, 17, ACCENT, true);
        val.setMinWidth(dp(70));
        row.addView(val);
        Button p = smallButton("+", Color.rgb(70, 70, 95));
        p.setOnClickListener(v -> plus.run());
        row.addView(p);
        return row;
    }

    // ---------- utilidades ----------

    private String fmt(long t) {
        return new SimpleDateFormat("EEE, dd/MM 'às' HH:mm", new Locale("pt", "BR")).format(new Date(t));
    }

    private boolean granted(String perm) {
        return checkSelfPermission(perm) == PackageManager.PERMISSION_GRANTED;
    }

    private Uri pkgUri() {
        return Uri.parse("package:" + getPackageName());
    }

    private void openAppSettings() {
        tryStart(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkgUri()));
    }

    private void tryStart(Intent i) {
        try {
            startActivity(i);
        } catch (Exception e) {
            openAppSettingsFallback();
        }
    }

    private void openAppSettingsFallback() {
        try {
            startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkgUri()));
        } catch (Exception ignored) {
        }
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private TextView label(String s, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setPadding(0, dp(4), 0, dp(4));
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private LinearLayout card(String title) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(16), dp(12), dp(16), dp(16));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(CARD);
        bg.setCornerRadius(dp(16));
        c.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(16);
        c.setLayoutParams(lp);
        c.addView(label(title, 18, ACCENT, true));
        return c;
    }

    private Button button(String text, int color) {
        Button b = smallButton(text, color);
        b.setTextSize(17);
        b.setPadding(dp(16), dp(14), dp(16), dp(14));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(12);
        b.setLayoutParams(lp);
        return b;
    }

    private Button smallButton(String text, int color) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextColor(Color.WHITE);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(color);
        bg.setCornerRadius(dp(10));
        b.setBackground(bg);
        b.setMinWidth(0);
        b.setMinimumWidth(dp(48));
        b.setPadding(dp(12), dp(6), dp(12), dp(6));
        return b;
    }
}
