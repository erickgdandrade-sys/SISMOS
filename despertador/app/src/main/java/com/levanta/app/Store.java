package com.levanta.app;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.List;

/** Guarda alarmes, configurações e o estado do alarme que está tocando. */
public class Store {
    /** No 3º toque não existe mais soneca: o celular trava até você provar que levantou. */
    public static final int MAX_RINGS = 3;

    private final SharedPreferences sp;

    public Store(Context c) {
        sp = c.getApplicationContext().getSharedPreferences("levanta", Context.MODE_PRIVATE);
    }

    // ---------- alarmes ----------

    public List<Alarm> alarms() {
        List<Alarm> list = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(sp.getString("alarms", "[]"));
            for (int i = 0; i < arr.length(); i++) list.add(Alarm.fromJson(arr.getJSONObject(i)));
        } catch (JSONException ignored) {
        }
        return list;
    }

    public void saveAlarms(List<Alarm> list) {
        JSONArray arr = new JSONArray();
        try {
            for (Alarm a : list) arr.put(a.toJson());
        } catch (JSONException ignored) {
        }
        sp.edit().putString("alarms", arr.toString()).commit();
    }

    public Alarm find(int id) {
        for (Alarm a : alarms()) if (a.id == id) return a;
        return null;
    }

    public void put(Alarm alarm) {
        List<Alarm> list = alarms();
        boolean replaced = false;
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).id == alarm.id) {
                list.set(i, alarm);
                replaced = true;
            }
        }
        if (!replaced) list.add(alarm);
        saveAlarms(list);
    }

    public void remove(int id) {
        List<Alarm> list = alarms();
        for (int i = list.size() - 1; i >= 0; i--) if (list.get(i).id == id) list.remove(i);
        saveAlarms(list);
    }

    public int newId() {
        int id = sp.getInt("nextId", 1);
        sp.edit().putInt("nextId", id + 1).commit();
        return id;
    }

    // ---------- configurações ----------

    public int snoozeMinutes() { return sp.getInt("snoozeMin", 5); }
    public void setSnoozeMinutes(int v) { sp.edit().putInt("snoozeMin", v).commit(); }

    public int stepsRequired() { return sp.getInt("steps", 40); }
    public void setStepsRequired(int v) { sp.edit().putInt("steps", v).commit(); }

    public boolean mathEnabled() { return sp.getBoolean("math", true); }
    public void setMathEnabled(boolean v) { sp.edit().putBoolean("math", v).commit(); }

    public boolean syncEnabled() { return sp.getBoolean("sync", true); }
    public void setSyncEnabled(boolean v) { sp.edit().putBoolean("sync", v).commit(); }

    /** Horário (ms) do despertador do app Relógio que estamos espelhando; 0 = nenhum. */
    public long mirrorTime() { return sp.getLong("mirror", 0); }
    public void setMirrorTime(long t) { sp.edit().putLong("mirror", t).commit(); }

    // ---------- sessão (alarme tocando / em soneca) ----------

    public boolean active() { return sp.getBoolean("active", false); }

    /** Toque atual: 1, 2 ou 3. */
    public int ring() { return sp.getInt("ring", 1); }
    public void setRing(int r) { sp.edit().putInt("ring", r).commit(); }

    public boolean muted() { return sp.getBoolean("muted", false); }
    public void setMuted(boolean m) { sp.edit().putBoolean("muted", m).commit(); }

    public long snoozeUntil() { return sp.getLong("snoozeUntil", 0); }
    public void setSnoozeUntil(long t) { sp.edit().putLong("snoozeUntil", t).commit(); }

    public boolean locked() { return active() && ring() >= MAX_RINGS; }

    public void startSession() {
        sp.edit().putBoolean("active", true).putInt("ring", 1).putBoolean("muted", false)
                .putLong("snoozeUntil", 0).commit();
    }

    public void endSession() {
        sp.edit().putBoolean("active", false).putInt("ring", 1).putBoolean("muted", false)
                .putLong("snoozeUntil", 0).commit();
    }
}
