package app.monthmark;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.*;
import app.monthmark.core.Countdown;

public class MainActivity extends Activity {
    public static final int[] ACCENTS = Appearance.COLORS;
    private int widgetId = AppWidgetManager.INVALID_APPWIDGET_ID;
    private final LinkedHashSet<String> selected=new LinkedHashSet<>();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable midnight = new Runnable() { public void run() { render(); scheduleRefresh(); } };
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("EEE, d MMM yyyy",Locale.getDefault());

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        widgetId = getIntent().getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,AppWidgetManager.INVALID_APPWIDGET_ID);
        if(state!=null && state.getStringArrayList("selected")!=null) selected.addAll(state.getStringArrayList("selected"));
        else if(configuring()) selected.addAll(new Store(this).widgetEvents(widgetId));
        if (configuring()) setResult(RESULT_CANCELED,new Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,widgetId));
        render();
        if (!configuring() && state == null) {
            Countdown event = new Store(this).get(getIntent().getStringExtra("event_id"));
            if (event != null) details(event);
        }
    }
    private boolean configuring() { return widgetId != AppWidgetManager.INVALID_APPWIDGET_ID; }
    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent); setIntent(intent);
        widgetId=intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,AppWidgetManager.INVALID_APPWIDGET_ID);
        selected.clear();
        if(configuring()) {
            selected.addAll(new Store(this).widgetEvents(widgetId));
            setResult(RESULT_CANCELED,new Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,widgetId));
        }
        render();
        if(!configuring()) { Countdown event=new Store(this).get(intent.getStringExtra("event_id")); if(event!=null) details(event); }
    }
    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state); state.putStringArrayList("selected",new ArrayList<>(selected));
    }
    @Override protected void onResume() { super.onResume(); render(); scheduleRefresh(); CountdownWidget.updateAll(this); }
    @Override protected void onPause() { super.onPause(); handler.removeCallbacks(midnight); }
    private void scheduleRefresh() {
        handler.removeCallbacks(midnight);
        long delay = Duration.between(Instant.now(),LocalDate.now().plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant()).toMillis();
        handler.postDelayed(midnight,Math.max(1000,delay+100));
    }

    private void render() {
        LinearLayout root = Ui.column(this); root.setBackgroundColor(Ui.BG); Ui.insets(root);
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true);
        LinearLayout content = Ui.column(this); content.setPadding(Ui.dp(this,24),Ui.dp(this,24),Ui.dp(this,24),Ui.dp(this,24));
        scroll.addView(content); root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        TextView brand = Ui.text(this,"◷  MONTHMARK",13,Ui.MINT); brand.setLetterSpacing(.14f); Ui.bold(brand); content.addView(brand);
        Ui.gap(content,20);
        TextView heading = Ui.text(this,configuring() ? "Pick your\ncountdown." : "Good things ahead.",28,Ui.WHITE);
        heading.setTypeface(android.graphics.Typeface.create("sans-serif-light",android.graphics.Typeface.NORMAL)); content.addView(heading);
        Ui.gap(content,12);
        TextView sub = Ui.text(this,configuring() ? "Choose one or more. Larger widgets show more countdowns; the nearest comes first." : "Big plans. Little rituals. Every day counts.",15,Ui.MUTED);
        content.addView(sub); Ui.gap(content,24);
        List<Countdown> events = new Store(this).all(); LocalDate today = LocalDate.now();
        // Upcoming first, past fixed dates last; nearest occurrence first within each group.
        events.sort(Comparator.comparing((Countdown c) -> c.days(today)<0).thenComparingLong(c -> Math.abs(c.days(today))).thenComparing(c -> c.title));
        TextView label = Ui.text(this,events.isEmpty() ? "YOUR NEXT MOMENT" : "YOUR COUNTDOWNS  ·  "+events.size(),11,Ui.MUTED);
        label.setLetterSpacing(.12f); Ui.bold(label); content.addView(label); Ui.gap(content,12);
        if (events.isEmpty()) {
            LinearLayout empty = Ui.column(this); empty.setPadding(Ui.dp(this,24),Ui.dp(this,28),Ui.dp(this,24),Ui.dp(this,28));
            empty.setBackground(Ui.shape(Ui.CARD,24,this));
            empty.addView(Ui.text(this,"Something to look\nforward to.",26,Ui.WHITE)); Ui.gap(empty,12);
            empty.addView(Ui.text(this,"Add a trip, a birthday or the last Friday of every month. Give it a name and make it yours.",15,Ui.MUTED));
            content.addView(empty);
        }
        for (int i=0; i<events.size(); i++) {
            Countdown c = events.get(i);
            LinearLayout card = Ui.column(this); int accent=ACCENTS[Math.floorMod(c.color,ACCENTS.length)];
            card.setBackground(Appearance.background(this,c)); card.setPadding(Ui.dp(this,22),Ui.dp(this,20),Ui.dp(this,22),Ui.dp(this,20));
            boolean photo=Appearance.photo(this,c.background);
            int fg=photo ? Ui.WHITE : Ui.BG, muted=photo ? 0xffe5eee8 : 0xff344b40;
            LinearLayout top = Ui.row(this); TextView icon=Ui.text(this,c.icon,28,fg); icon.setContentDescription("Countdown icon");
            LinearLayout.LayoutParams iconParams=new LinearLayout.LayoutParams(Ui.dp(this,44),Ui.dp(this,44)); iconParams.setMarginEnd(Ui.dp(this,12));
            icon.setGravity(Gravity.CENTER); icon.setBackground(Ui.shape(photo ? 0x44101613 : 0x66ffffff,14,this)); top.addView(icon,iconParams);
            TextView name=Ui.text(this,c.title,19,fg); Ui.bold(name);
            top.addView(name,new LinearLayout.LayoutParams(0,-2,1)); TextView arrow=Ui.text(this,configuring() ? selected.contains(c.id) ? "☑" : "☐" : "↗",24,fg); top.addView(arrow); card.addView(top);
            Ui.gap(card,6); card.addView(Ui.text(this,rule(c),12,muted)); Ui.gap(card,16);
            long days=c.days(today); LinearLayout numberRow=Ui.row(this);
            TextView number=Ui.text(this,Long.toString(Math.abs(days)),56,fg);
            number.setTypeface(android.graphics.Typeface.create("sans-serif-light",android.graphics.Typeface.NORMAL)); number.setIncludeFontPadding(false);
            numberRow.addView(number); TextView unit=Ui.text(this,days==0 ? "  today" : days<0 ? "  days ago" : days==1 ? "  day to go" : "  days to go",15,muted);
            numberRow.addView(unit); card.addView(numberRow); Ui.gap(card,12);
            card.addView(Ui.text(this,c.target(today).format(DATE),13,muted));
            card.setOnClickListener(v -> { if (configuring()) { if(!selected.remove(c.id)) selected.add(c.id); render(); } else details(c); });
            card.setFocusable(true); card.setContentDescription(c.title+", "+Math.abs(days)+" days "+(days<0 ? "ago" : "remaining")+". "+rule(c));
            content.addView(card,new LinearLayout.LayoutParams(-1,-2)); Ui.gap(content,12);
        }
        Ui.gap(content,12);
        TextView footer=Ui.text(this,"Made for your home screen.\nAdd a widget to keep a moment in view.",12,Ui.MUTED); footer.setLineSpacing(Ui.dp(this,4),1); content.addView(footer);
        LinearLayout bottom=Ui.column(this); bottom.setPadding(Ui.dp(this,24),Ui.dp(this,12),Ui.dp(this,24),Ui.dp(this,16));
        if(configuring()) {
            selected.removeIf(id -> new Store(this).get(id)==null);
            Button use=Ui.button(this,"Use selected ("+selected.size()+")",true); use.setEnabled(!selected.isEmpty()); use.setAlpha(selected.isEmpty() ? .4f : 1f);
            use.setOnClickListener(v -> selectWidget()); bottom.addView(use,new LinearLayout.LayoutParams(-1,Ui.dp(this,56))); Ui.gap(bottom,8);
        }
        Button add=Ui.button(this,"+  Add countdown",!configuring()); add.setOnClickListener(v -> edit(null)); bottom.addView(add,new LinearLayout.LayoutParams(-1,Ui.dp(this,56)));
        root.addView(bottom); setContentView(root);
    }

    static String rule(Countdown c) {
        if (!c.isMonthly()) return "FIXED DATE";
        String[] ordinal={"Last","Second-last","Third-last","Fourth-last"};
        return ordinal[c.fromEnd-1]+" "+c.weekday.getDisplayName(TextStyle.FULL,Locale.getDefault())+" of every month";
    }

    private void edit(Countdown event) {
        Intent i=new Intent(this,EditorActivity.class);
        if (event!=null) i.putExtra("event_id",event.id);
        startActivity(i);
    }

    private void details(Countdown c) {
        StringBuilder description=new StringBuilder(rule(c));
        if(c.isMonthly()) { description.append("\n\nNext dates"); for(LocalDate date:c.upcoming(LocalDate.now(),3)) description.append("\n").append(date.format(DATE)); }
        else description.append("\n\n").append(c.fixedDate.format(DATE));
        String[] actions={"Edit countdown","Add home-screen widget","Delete countdown"};
        new AlertDialog.Builder(this).setTitle(c.title).setItems(actions,(dialog,which) -> {
            if(which==0) edit(c);
            if(which==1) pin(c);
            if(which==2) new AlertDialog.Builder(this).setTitle("Delete “"+c.title+"”?")
                .setMessage("It will be removed from your widgets. Empty widgets will ask you to choose another countdown.")
                .setNegativeButton("Keep",null).setPositiveButton("Delete",(d,w) -> {new Store(this).delete(c.id); Appearance.deleteIfUnused(this,c.background); CountdownWidget.updateAll(this);render();}).show();
        }).setNeutralButton("View dates",(d,w) -> new AlertDialog.Builder(this).setTitle(c.title).setMessage(description).setPositiveButton("Done",null).show())
          .setNegativeButton("Close",null).show();
    }

    private void selectWidget() {
        new Store(this).bindWidget(widgetId,new ArrayList<>(selected)); CountdownWidget.update(this,AppWidgetManager.getInstance(this),widgetId);
        setResult(RESULT_OK,new Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,widgetId));
        CountdownWidget.updateAll(this); finish();
    }

    private void pin(Countdown c) {
        new AlertDialog.Builder(this).setTitle("Choose a widget size")
            .setItems(new String[]{"Small · 2 × 2 · one countdown","Medium · 4 × 2 · multiple countdowns","Large · 4 × 4 · multiple countdowns"},(d,which) -> pinSize(c,which)).show();
    }

    private void pinSize(Countdown c,int size) {
        if(size==0) { requestPin(size,new ArrayList<>(Collections.singletonList(c.id))); return; }
        List<Countdown> events=new Store(this).all(); String[] names=new String[events.size()]; boolean[] checked=new boolean[events.size()];
        for(int i=0;i<events.size();i++) { names[i]=events.get(i).title; checked[i]=events.get(i).id.equals(c.id); }
        AlertDialog picker=new AlertDialog.Builder(this).setTitle("Choose countdowns")
            .setMultiChoiceItems(names,checked,(dialog,which,value) -> checked[which]=value)
            .setNegativeButton("Cancel",null).setPositiveButton("Add widget",null).create();
        picker.setOnShowListener(dialog -> picker.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            ArrayList<String> ids=new ArrayList<>(); for(int i=0;i<checked.length;i++) if(checked[i]) ids.add(events.get(i).id);
            if(ids.isEmpty()) { Toast.makeText(this,"Choose at least one countdown",Toast.LENGTH_SHORT).show(); return; }
            picker.dismiss(); requestPin(size,ids);
        })); picker.show();
    }

    private void requestPin(int size,ArrayList<String> ids) {
        AppWidgetManager manager=AppWidgetManager.getInstance(this);
        if(manager.isRequestPinAppWidgetSupported()) {
            Intent callback=new Intent(this,CountdownWidget.class).setAction(CountdownWidget.PINNED)
                .setData(android.net.Uri.parse("monthmark://pin/"+UUID.randomUUID())).putStringArrayListExtra("event_ids",ids);
            // The launcher fills in EXTRA_APPWIDGET_ID on success, so this explicit callback is mutable.
            int mutable=android.os.Build.VERSION.SDK_INT>=31 ? PendingIntent.FLAG_MUTABLE : 0;
            PendingIntent success=PendingIntent.getBroadcast(this,0,callback,PendingIntent.FLAG_UPDATE_CURRENT|mutable);
            Class<?> provider=size==0 ? CountdownWidget.class : size==1 ? MediumWidget.class : LargeWidget.class;
            if(!manager.requestPinAppWidget(new ComponentName(this,provider),null,success)) widgetInstructions();
        } else widgetInstructions();
    }

    private void widgetInstructions() {
        new AlertDialog.Builder(this).setTitle("Add your widget")
            .setMessage("Touch and hold an empty space on your home screen. Open Widgets, find Monthmark, then drag a widget onto the screen and choose your countdowns.")
            .setPositiveButton("Got it",null).show();
    }
}
