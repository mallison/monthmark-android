package app.monthmark;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.widget.RemoteViews;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.*;
import app.monthmark.core.Countdown;

public class CountdownWidget extends AppWidgetProvider {
    static final String REFRESH = "app.monthmark.REFRESH";
    static final String PINNED = "app.monthmark.PINNED";

    @Override public void onReceive(Context context, Intent intent) {
        super.onReceive(context,intent);
        String action=intent.getAction();
        if (PINNED.equals(action)) {
            int id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
            ArrayList<String> events = intent.getStringArrayListExtra("event_ids");
            if (id != AppWidgetManager.INVALID_APPWIDGET_ID && events!=null) {
                events.removeIf(eventId -> new Store(context).get(eventId)==null);
                new Store(context).bindWidget(id,events);
            }
            updateAll(context);
        } else if (REFRESH.equals(action) || Intent.ACTION_BOOT_COMPLETED.equals(action)
                || Intent.ACTION_MY_PACKAGE_REPLACED.equals(action) || Intent.ACTION_TIME_CHANGED.equals(action)
                || Intent.ACTION_TIMEZONE_CHANGED.equals(action) || Intent.ACTION_DATE_CHANGED.equals(action)) {
            updateAll(context);
        }
    }

    @Override public void onUpdate(Context c, AppWidgetManager manager, int[] ids) {
        for (int id : ids) { configureDefault(c,manager,id); update(c,manager,id); }
        schedule(c);
    }

    @Override public void onAppWidgetOptionsChanged(Context c, AppWidgetManager manager, int id, Bundle options) {
        configureDefault(c,manager,id);
        update(c,manager,id);
    }

    @Override public void onDeleted(Context c, int[] ids) {
        Store store = new Store(c);
        for (int id : ids) store.removeWidget(id);
        schedule(c);
    }

    @Override public void onRestored(Context c, int[] oldIds, int[] newIds) {
        Store store = new Store(c);
        for (int i=0; i<oldIds.length; i++) {
            List<String> events = store.widgetEvents(oldIds[i]);
            if (store.isWidgetConfigured(oldIds[i])) store.bindWidget(newIds[i],events);
            if(oldIds[i]!=newIds[i]) store.removeWidget(oldIds[i]);
        }
        updateAll(c);
    }

    @Override public void onDisabled(Context c) {
        schedule(c);
    }

    static void updateAll(Context c) {
        AppWidgetManager manager = AppWidgetManager.getInstance(c);
        for (int id : allIds(c)) update(c,manager,id);
        schedule(c);
    }

    static void update(Context c, AppWidgetManager manager, int id) {
        Store store = new Store(c);
        // A host owns placement until its configuration activity returns RESULT_OK.
        // Do not replace its pending view or install a second configure PendingIntent.
        if (!store.isWidgetConfigured(id)) return;
        List<Countdown> events = new ArrayList<>();
        for(String eventId:store.widgetEvents(id)) { Countdown event=store.get(eventId); if(event!=null) events.add(event); }
        LocalDate today=LocalDate.now();
        events.sort(Comparator.comparing((Countdown item) -> item.days(today)<0).thenComparingLong(item -> Math.abs(item.days(today))));
        Countdown event=events.isEmpty() ? null : events.get(0);
        Bundle options=manager.getAppWidgetOptions(id);
        boolean landscape=c.getResources().getConfiguration().orientation==android.content.res.Configuration.ORIENTATION_LANDSCAPE;
        int width=options.getInt(landscape ? AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH : AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH,250);
        int height=options.getInt(landscape ? AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT : AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT,160);
        int capacity=height>=300 ? 4 : height>=230 ? 3 : width>=200 && height>=145 ? 2 : 1;
        boolean list=events.size()>1 && capacity>1;
        RemoteViews views = new RemoteViews(c.getPackageName(), list ? R.layout.countdown_list_widget : R.layout.countdown_widget);
        Intent open = new Intent(c, WidgetConfigurationActivity.class)
            .setAction(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,id);
        if (event == null) {
            open.setClass(c,WidgetConfigurationActivity.class);
            views.setTextViewText(R.id.widget_title,"Choose a countdown");
            views.setTextViewText(R.id.widget_days,"+");
            views.setTextViewText(R.id.widget_unit,"Tap to set up");
            views.setTextViewText(R.id.widget_date,"Monthmark");
            open.setAction(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE);
            open.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,id);
        } else {
            long days = event.days(today);
            if(list) {
                int[] rows={R.id.widget_row_1,R.id.widget_row_2,R.id.widget_row_3,R.id.widget_row_4};
                int[] titles={R.id.title_1,R.id.title_2,R.id.title_3,R.id.title_4};
                int[] numbers={R.id.days_1,R.id.days_2,R.id.days_3,R.id.days_4};
                int[] dates={R.id.date_1,R.id.date_2,R.id.date_3,R.id.date_4};
                int[] icons={R.id.icon_1,R.id.icon_2,R.id.icon_3,R.id.icon_4};
                int[] backgrounds={R.id.background_1,R.id.background_2,R.id.background_3,R.id.background_4};
                int shown=Math.min(capacity,events.size());
                for(int i=0;i<4;i++) {
                    views.setViewVisibility(rows[i],i<shown ? android.view.View.VISIBLE : android.view.View.GONE);
                    if(i>=shown) continue;
                    Countdown item=events.get(i); long remaining=item.days(today);
                    boolean photo=Appearance.photo(c,item.background); int foreground=photo ? Ui.WHITE : Ui.BG;
                    views.setImageViewBitmap(backgrounds[i],Appearance.widgetBackground(c,item,Math.max(100,width-20),Math.max(40,(height-42)/shown)));
                    views.setTextViewText(icons[i],item.icon);
                    views.setTextViewText(titles[i],item.title);
                    views.setTextViewText(numbers[i],remaining==0 ? "Today" : Math.abs(remaining)+(remaining<0 ? "d ago" : "d"));
                    views.setTextViewText(dates[i],item.target(today).format(DateTimeFormatter.ofPattern("EEE, d MMM",Locale.getDefault())));
                    views.setTextColor(numbers[i],foreground); views.setTextColor(titles[i],foreground);
                    views.setTextColor(dates[i],photo ? 0xffe5eee8 : 0xff344b40);
                }
                views.setTextViewText(R.id.list_heading,"MONTHMARK"+(events.size()>shown ? "  ·  +"+(events.size()-shown)+" more" : ""));
            } else {
                boolean photo=Appearance.photo(c,event.background); int foreground=photo ? Ui.WHITE : Ui.BG;
                views.setImageViewBitmap(R.id.widget_background_image,Appearance.widgetBackground(c,event,width,height));
                views.setTextViewText(R.id.widget_icon,event.icon);
                views.setTextViewText(R.id.widget_title,event.title);
                views.setTextViewText(R.id.widget_days,Long.toString(Math.abs(days)));
                views.setTextViewText(R.id.widget_unit, days == 0 ? "today" : days < 0 ? "days ago" : days == 1 ? "day to go" : "days to go");
                String datePattern=events.size()>1 ? "d MMM" : "EEE, d MMM";
                views.setTextViewText(R.id.widget_date,event.target(today).format(DateTimeFormatter.ofPattern(datePattern,Locale.getDefault()))+(events.size()>1 ? "  ·  +"+(events.size()-1)+" more" : ""));
                views.setTextColor(R.id.widget_days,foreground); views.setTextColor(R.id.widget_title,foreground);
                views.setTextColor(R.id.widget_unit,photo ? 0xffe5eee8 : 0xff344b40); views.setTextColor(R.id.widget_date,photo ? 0xffe5eee8 : 0xff344b40);
            }
        }
        open.setData(android.net.Uri.parse("monthmark://widget/"+id));
        PendingIntent tap = PendingIntent.getActivity(c,id,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.widget_root,tap);
        views.setContentDescription(R.id.widget_root,event == null ? "Choose a countdown" : event.title + ", " + Math.abs(event.days(LocalDate.now())) + " days " + (event.days(LocalDate.now()) < 0 ? "ago" : "remaining"));
        manager.updateAppWidget(id,views);
    }

    private static void configureDefault(Context c,AppWidgetManager manager,int id) {
        Store store=new Store(c);
        if(store.isWidgetConfigured(id) || android.os.Build.VERSION.SDK_INT<31) return;
        android.appwidget.AppWidgetProviderInfo info=manager.getAppWidgetInfo(id);
        if(info==null || (info.widgetFeatures & android.appwidget.AppWidgetProviderInfo.WIDGET_FEATURE_CONFIGURATION_OPTIONAL)==0) return;
        LocalDate today=LocalDate.now(); List<Countdown> events=store.all();
        events.sort(Comparator.comparing((Countdown event) -> event.days(today)<0).thenComparingLong(event -> Math.abs(event.days(today))));
        List<String> selected=new ArrayList<>();
        boolean single=info.provider.equals(new ComponentName(c,CountdownWidget.class));
        for(Countdown event:events) { selected.add(event.id); if(single) break; }
        // A useful initial view lets modern launchers finish placement without waiting for an activity result.
        store.bindWidget(id,selected);
    }

    private static PendingIntent refreshIntent(Context c) {
        return PendingIntent.getBroadcast(c,0,new Intent(c,CountdownWidget.class).setAction(REFRESH),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
    }

    private static void schedule(Context c) {
        AlarmManager alarm = (AlarmManager)c.getSystemService(Context.ALARM_SERVICE);
        Store store=new Store(c);
        boolean configured=false;
        for(int id:allIds(c)) if(store.isWidgetConfigured(id)) { configured=true; break; }
        if (!configured) { alarm.cancel(refreshIntent(c)); return; }
        long next = LocalDate.now().plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
        // No exact-alarm permission: Android may defer this during deep sleep.
        alarm.setAndAllowWhileIdle(AlarmManager.RTC,next,refreshIntent(c));
    }

    static int[] allIds(Context c) {
        java.util.stream.IntStream ids=java.util.stream.IntStream.empty();
        for(Class<?> provider:new Class<?>[]{CountdownWidget.class,MediumWidget.class,LargeWidget.class})
            ids=java.util.stream.IntStream.concat(ids,java.util.Arrays.stream(AppWidgetManager.getInstance(c).getAppWidgetIds(new ComponentName(c,provider))));
        return ids.toArray();
    }
}
