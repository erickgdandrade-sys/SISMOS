package com.levanta.app;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Random;

/** Tela do alarme: soneca (até o 2º toque), prova de que levantou (passos + conta) e modo travado. */
public class RingActivity extends Activity implements SensorEventListener {
    /** Lida pelo serviço: se a tela sumir no modo travado, ela é reaberta. */
    public static volatile boolean visible;

    private static final int STAGE_MAIN = 0, STAGE_STEPS = 1, STAGE_MATH = 2;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Random random = new Random();
    private Store store;

    private TextView clock, ringInfo, message, stepsText;
    private Button snoozeBtn, proveBtn, muteBtn, emergencyBtn;
    private LinearLayout mainBox, stepsBox, mathBox;
    private ProgressBar stepsBar;
    private TextView mathQuestion;
    private EditText mathInput;

    private int stage = STAGE_MAIN;
    private int steps, stepsTarget, mathAnswer;

    private SensorManager sensors;
    private boolean listening;
    // detector de passos pelo acelerômetro (quando não há sensor de passos)
    private float smooth = SensorManager.GRAVITY_EARTH;
    private boolean high;
    private long lastStepAt;

    private final Runnable refresher = new Runnable() {
        @Override
        public void run() {
            refresh();
            handler.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        store = new Store(this);
        sensors = getSystemService(SensorManager.class);
        showOverLockScreen();
        setContentView(buildUi());
    }

    private void showOverLockScreen() {
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        } else {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                    | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_FULLSCREEN);
    }

    // ---------- interface ----------

    private View buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setBackgroundColor(Color.rgb(17, 17, 27));
        int pad = dp(24);
        root.setPadding(pad, dp(56), pad, pad);

        clock = text(64, Color.WHITE, true);
        root.addView(clock);
        ringInfo = text(20, Color.rgb(255, 196, 0), true);
        root.addView(ringInfo);
        message = text(17, Color.rgb(220, 220, 230), false);
        message.setPadding(0, dp(12), 0, dp(24));
        root.addView(message);

        // principal
        mainBox = column();
        proveBtn = button("🚶  PROVAR QUE LEVANTEI", Color.rgb(46, 160, 67));
        proveBtn.setOnClickListener(v -> startSteps());
        mainBox.addView(proveBtn);
        snoozeBtn = button("", Color.rgb(70, 70, 95));
        snoozeBtn.setOnClickListener(v -> {
            send(AlarmService.ACTION_SNOOZE);
            finish();
        });
        mainBox.addView(snoozeBtn);
        muteBtn = button("🔇  Desligar a música (a vibração continua)", Color.rgb(120, 60, 60));
        muteBtn.setOnClickListener(v -> send(AlarmService.ACTION_MUTE));
        mainBox.addView(muteBtn);
        root.addView(mainBox);

        // passos
        stepsBox = column();
        TextView stepsTitle = text(22, Color.WHITE, true);
        stepsTitle.setText("Sai da cama e anda!");
        stepsBox.addView(stepsTitle);
        TextView stepsHint = text(15, Color.rgb(200, 200, 210), false);
        stepsHint.setText("Segure o celular na mão e caminhe pela casa. Os passos são contados pelo sensor.");
        stepsBox.addView(stepsHint);
        stepsText = text(48, Color.rgb(46, 200, 90), true);
        stepsText.setPadding(0, dp(24), 0, dp(8));
        stepsBox.addView(stepsText);
        stepsBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        stepsBox.addView(stepsBar, new LinearLayout.LayoutParams(-1, dp(18)));
        root.addView(stepsBox);

        // conta
        mathBox = column();
        TextView mathTitle = text(20, Color.WHITE, true);
        mathTitle.setText("Agora prova que acordou mesmo:");
        mathBox.addView(mathTitle);
        mathQuestion = text(40, Color.rgb(255, 196, 0), true);
        mathQuestion.setPadding(0, dp(16), 0, dp(16));
        mathBox.addView(mathQuestion);
        mathInput = new EditText(this);
        mathInput.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED);
        mathInput.setTextColor(Color.WHITE);
        mathInput.setTextSize(28);
        mathInput.setGravity(Gravity.CENTER);
        mathInput.setHint("resposta");
        mathInput.setHintTextColor(Color.GRAY);
        mathBox.addView(mathInput, new LinearLayout.LayoutParams(-1, -2));
        Button ok = button("Confirmar", Color.rgb(46, 160, 67));
        ok.setOnClickListener(v -> checkMath());
        mathBox.addView(ok);
        root.addView(mathBox);

        View spacer = new View(this);
        root.addView(spacer, new LinearLayout.LayoutParams(1, 0, 1f));

        emergencyBtn = button("🆘  Emergência (libera o telefone por 3 min)", Color.rgb(60, 30, 30));
        emergencyBtn.setTextSize(13);
        emergencyBtn.setOnClickListener(v -> emergency());
        root.addView(emergencyBtn);

        showStage(STAGE_MAIN);
        return root;
    }

    private void refresh() {
        if (!store.active()) {
            finish();
            return;
        }
        clock.setText(new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date()));
        int ring = store.ring();
        boolean locked = store.locked();
        ringInfo.setText(locked ? "🔒 3º ALARME — CELULAR TRAVADO" : "Alarme " + ring + " de " + Store.MAX_RINGS);
        if (stage == STAGE_MAIN) {
            message.setText(locked
                    ? "Acabou a soneca. Ele vai vibrar sem parar até você provar que levantou."
                    : "Só desliga depois que você levantar e provar. Restam "
                    + (Store.MAX_RINGS - ring) + " soneca(s).");
        }
        snoozeBtn.setText("😴  Soneca de " + store.snoozeMinutes() + " min (" + (Store.MAX_RINGS - ring) + " restante(s))");
        snoozeBtn.setVisibility(!locked && stage == STAGE_MAIN ? View.VISIBLE : View.GONE);
        muteBtn.setVisibility(locked && !store.muted() && stage == STAGE_MAIN ? View.VISIBLE : View.GONE);
        emergencyBtn.setVisibility(locked ? View.VISIBLE : View.GONE);
    }

    private void showStage(int s) {
        stage = s;
        mainBox.setVisibility(s == STAGE_MAIN ? View.VISIBLE : View.GONE);
        stepsBox.setVisibility(s == STAGE_STEPS ? View.VISIBLE : View.GONE);
        mathBox.setVisibility(s == STAGE_MATH ? View.VISIBLE : View.GONE);
        if (s != STAGE_MAIN) message.setText("");
        if (clock != null) refresh();
    }

    // ---------- prova 1: passos ----------

    private void startSteps() {
        steps = 0;
        stepsTarget = Math.max(1, store.stepsRequired());
        stepsBar.setMax(stepsTarget);
        updateSteps();
        showStage(STAGE_STEPS);
        startListening();
    }

    private void startListening() {
        if (listening) return;
        Sensor detector = sensors.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR);
        boolean allowed = Build.VERSION.SDK_INT < 29
                || checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED;
        if (detector != null && allowed) {
            listening = sensors.registerListener(this, detector, SensorManager.SENSOR_DELAY_FASTEST);
        }
        if (!listening) {
            Sensor accel = sensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            if (accel != null) listening = sensors.registerListener(this, accel, SensorManager.SENSOR_DELAY_GAME);
        }
        if (!listening) {
            // aparelho sem sensores: pula direto para a conta
            Toast.makeText(this, "Sem sensor de movimento. Resolva a conta.", Toast.LENGTH_LONG).show();
            stepsDone();
        }
    }

    private void stopListening() {
        if (listening) sensors.unregisterListener(this);
        listening = false;
    }

    @Override
    public void onSensorChanged(SensorEvent e) {
        if (stage != STAGE_STEPS) return;
        if (e.sensor.getType() == Sensor.TYPE_STEP_DETECTOR) {
            countStep();
            return;
        }
        float x = e.values[0], y = e.values[1], z = e.values[2];
        float mag = (float) Math.sqrt(x * x + y * y + z * z);
        smooth = smooth * 0.75f + mag * 0.25f;
        if (!high && smooth > 11.2f) {
            high = true;
        } else if (high && smooth < 9.0f) {
            high = false;
            long now = System.currentTimeMillis();
            if (now - lastStepAt > 300) { // ninguém dá mais de ~3 passos por segundo
                lastStepAt = now;
                countStep();
            }
        }
    }

    private void countStep() {
        steps++;
        updateSteps();
        if (steps >= stepsTarget) stepsDone();
    }

    private void updateSteps() {
        stepsText.setText(steps + " / " + stepsTarget);
        stepsBar.setProgress(steps);
    }

    private void stepsDone() {
        stopListening();
        if (store.mathEnabled()) {
            newMath();
            showStage(STAGE_MATH);
        } else {
            finishProof();
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    // ---------- prova 2: conta ----------

    private void newMath() {
        int a = 12 + random.nextInt(38);
        int b = 3 + random.nextInt(7);
        int c = 10 + random.nextInt(90);
        mathAnswer = a * b + c;
        mathQuestion.setText(a + " × " + b + " + " + c);
        mathInput.setText("");
    }

    private void checkMath() {
        String t = mathInput.getText().toString().trim();
        int v;
        try {
            v = Integer.parseInt(t);
        } catch (NumberFormatException e) {
            return;
        }
        if (v == mathAnswer) {
            finishProof();
        } else {
            Toast.makeText(this, "Errado! Outra conta.", Toast.LENGTH_SHORT).show();
            newMath();
        }
    }

    private void finishProof() {
        send(AlarmService.ACTION_DISMISS);
        Toast.makeText(this, "Bom dia! ☀️ Você levantou.", Toast.LENGTH_LONG).show();
        finish();
    }

    // ---------- emergência ----------

    private void emergency() {
        send(AlarmService.ACTION_EMERGENCY);
        try {
            startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:")));
        } catch (Exception ignored) {
        }
    }

    // ---------- bloqueios ----------

    @Override
    public void onBackPressed() {
        // voltar não desliga o alarme
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_UP
                || keyCode == KeyEvent.KEYCODE_VOLUME_MUTE || keyCode == KeyEvent.KEYCODE_BACK) {
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onResume() {
        super.onResume();
        visible = true;
        hideSystemBars();
        handler.post(refresher);
        if (stage == STAGE_STEPS) startListening();
    }

    @Override
    protected void onPause() {
        super.onPause();
        visible = false;
        handler.removeCallbacks(refresher);
    }

    @Override
    protected void onDestroy() {
        stopListening();
        visible = false;
        super.onDestroy();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) hideSystemBars();
    }

    @SuppressWarnings("deprecation")
    private void hideSystemBars() {
        if (Build.VERSION.SDK_INT >= 30) {
            WindowInsetsController c = getWindow().getInsetsController();
            if (c != null) {
                c.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                c.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        }
    }

    // ---------- utilidades ----------

    private void send(String action) {
        try {
            startService(new Intent(this, AlarmService.class).setAction(action));
        } catch (Exception ignored) {
        }
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private TextView text(int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setGravity(Gravity.CENTER);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private LinearLayout column() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setGravity(Gravity.CENTER_HORIZONTAL);
        l.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));
        return l;
    }

    private Button button(String label, int color) {
        Button btn = new Button(this);
        btn.setText(label);
        btn.setAllCaps(false);
        btn.setTextColor(Color.WHITE);
        btn.setTextSize(17);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(color);
        bg.setCornerRadius(dp(14));
        btn.setBackground(bg);
        btn.setPadding(dp(16), dp(18), dp(16), dp(18));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(12);
        btn.setLayoutParams(lp);
        return btn;
    }
}
