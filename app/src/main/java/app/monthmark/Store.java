package app.monthmark;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import app.monthmark.core.Countdown;

final class Store {
    private final SharedPreferences prefs;
    Store(Context context) { prefs = context.getSharedPreferences("monthmark", Context.MODE_PRIVATE); }

    List<Countdown> all() {
        List<Countdown> result = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(prefs.getString("events", "[]"));
            for (int i=0; i<array.length(); i++) {
                try {
                    JSONObject o = array.getJSONObject(i);
                    String id = o.getString("id"), title = o.getString("title");
                    int color = o.optInt("color", 0);
                    Countdown event=o.has("date") ? Countdown.fixed(id, title, LocalDate.parse(o.getString("date")), color)
                        : Countdown.monthly(id, title, DayOfWeek.of(o.getInt("weekday")), o.getInt("fromEnd"), color);
                    result.add(event.withAppearance(o.optString("icon","✨"),o.optString("background","")));
                } catch (Exception ignored) { /* A malformed entry must not hide the remaining events. */ }
            }
        } catch (Exception ignored) { }
        return result;
    }

    Countdown get(String id) {
        if (id == null) return null;
        for (Countdown c : all()) if (c.id.equals(id)) return c;
        return null;
    }

    void save(Countdown event) {
        List<Countdown> events = all();
        events.removeIf(c -> c.id.equals(event.id));
        events.add(event);
        write(events);
    }

    void delete(String id) {
        List<Countdown> events = all();
        events.removeIf(c -> c.id.equals(id));
        write(events);
    }

    private void write(List<Countdown> events) {
        JSONArray array = new JSONArray();
        for (Countdown c : events) {
            JSONObject o = new JSONObject();
            try {
                o.put("id",c.id).put("title",c.title).put("color",c.color).put("icon",c.icon).put("background",c.background);
                if (c.isMonthly()) o.put("weekday",c.weekday.getValue()).put("fromEnd",c.fromEnd);
                else o.put("date",c.fixedDate.toString());
                array.put(o);
            } catch (Exception e) { throw new IllegalStateException(e); }
        }
        prefs.edit().putString("events",array.toString()).apply();
    }

    List<String> widgetEvents(int widgetId) {
        List<String> ids = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(prefs.getString("widget_"+widgetId,"[]"));
            for(int i=0;i<array.length();i++) ids.add(array.getString(i));
        } catch(Exception ignored) { }
        return ids;
    }
    boolean isWidgetConfigured(int widgetId) { return prefs.contains("widget_"+widgetId); }
    void bindWidget(int widgetId, List<String> eventIds) {
        prefs.edit().putString("widget_"+widgetId,new JSONArray(eventIds).toString()).apply();
    }
    void removeWidget(int widgetId) { prefs.edit().remove("widget_"+widgetId).apply(); }
}
