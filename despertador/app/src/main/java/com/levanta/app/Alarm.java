package com.levanta.app;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Locale;

/** Um despertador criado no app. days = bits dos dias (bit 0 = domingo ... bit 6 = sábado); 0 = uma vez só. */
public class Alarm {
    public static final String[] DAY_NAMES = {"Dom", "Seg", "Ter", "Qua", "Qui", "Sex", "Sáb"};

    public int id;
    public int hour;
    public int minute;
    public int days;
    public boolean enabled = true;

    public JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("id", id);
        o.put("h", hour);
        o.put("m", minute);
        o.put("d", days);
        o.put("on", enabled);
        return o;
    }

    public static Alarm fromJson(JSONObject o) {
        Alarm a = new Alarm();
        a.id = o.optInt("id");
        a.hour = o.optInt("h");
        a.minute = o.optInt("m");
        a.days = o.optInt("d");
        a.enabled = o.optBoolean("on", true);
        return a;
    }

    public String timeText() {
        return String.format(Locale.US, "%02d:%02d", hour, minute);
    }

    public String daysText() {
        if (days == 0) return "Uma vez";
        if (days == 0x7F) return "Todos os dias";
        if (days == 0x3E) return "Dias úteis";
        if (days == 0x41) return "Fim de semana";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 7; i++) {
            if ((days & (1 << i)) != 0) {
                if (sb.length() > 0) sb.append(", ");
                sb.append(DAY_NAMES[i]);
            }
        }
        return sb.toString();
    }
}
