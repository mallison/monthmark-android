package app.monthmark;

import android.app.Activity;
import android.app.DatePickerDialog;
import android.os.Bundle;
import android.view.View;
import android.widget.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.*;
import app.monthmark.core.Countdown;

public final class EditorActivity extends Activity {
    private EditText title;
    private RadioGroup type;
    private Spinner weekday, offset;
    private LinearLayout monthly, fixed, colorRow;
    private TextView preview;
    private Button dateButton;
    private LocalDate date=LocalDate.now().plusDays(1);
    private int color=0;
    private String eventId;
    private static final int FIXED=1001, MONTHLY=1002;
    private static final DateTimeFormatter DATE=DateTimeFormatter.ofPattern("EEE, d MMM yyyy",Locale.getDefault());

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        eventId=getIntent().getStringExtra("event_id"); Countdown original=new Store(this).get(eventId);
        if (eventId!=null && original==null) { finish(); return; }
        if(original!=null) { color=original.color; if(!original.isMonthly()) date=original.fixedDate; }
        if(state!=null) { date=LocalDate.parse(state.getString("date",date.toString())); color=state.getInt("color",color); }
        LinearLayout root=Ui.column(this); root.setBackgroundColor(Ui.BG); Ui.insets(root);
        ScrollView scroll=new ScrollView(this); LinearLayout body=Ui.column(this);
        body.setPadding(Ui.dp(this,24),Ui.dp(this,16),Ui.dp(this,24),Ui.dp(this,32)); scroll.addView(body); root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        Button back=Ui.button(this,"‹  Back",false); back.setOnClickListener(v -> finish()); body.addView(back);
        Ui.gap(body,24); body.addView(Ui.text(this,original==null ? "A moment\nto look forward to." : "Make it yours.",32,Ui.WHITE)); Ui.gap(body,28);
        label(body,"COUNTDOWN NAME"); title=new EditText(this); title.setSingleLine(true); title.setTextSize(20); title.setTextColor(Ui.WHITE); title.setHintTextColor(Ui.MUTED);
        title.setHint("Days to payday, holiday, birthday…"); title.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        title.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(80)});
        title.setText(state!=null ? state.getString("title","") : original==null ? "" : original.title); body.addView(title,new LinearLayout.LayoutParams(-1,Ui.dp(this,64))); Ui.gap(body,24);
        label(body,"DATE TYPE"); type=new RadioGroup(this); type.setOrientation(RadioGroup.VERTICAL);
        RadioButton fixedType=new RadioButton(this); fixedType.setId(FIXED); fixedType.setText("Fixed date"); fixedType.setTextColor(Ui.WHITE); type.addView(fixedType,new RadioGroup.LayoutParams(-1,Ui.dp(this,52)));
        RadioButton monthlyType=new RadioButton(this); monthlyType.setId(MONTHLY); monthlyType.setText("Monthly weekday  ·  e.g. last Friday"); monthlyType.setTextColor(Ui.WHITE); type.addView(monthlyType,new RadioGroup.LayoutParams(-1,Ui.dp(this,52)));
        body.addView(type); Ui.gap(body,20);
        fixed=Ui.column(this); label(fixed,"COUNT DOWN TO"); dateButton=Ui.button(this,date.format(DATE),false); fixed.addView(dateButton,new LinearLayout.LayoutParams(-1,Ui.dp(this,56)));
        dateButton.setOnClickListener(v -> new DatePickerDialog(this,(picker,y,m,d) -> { date=LocalDate.of(y,m+1,d); dateButton.setText(date.format(DATE)); refreshPreview(); },date.getYear(),date.getMonthValue()-1,date.getDayOfMonth()).show()); body.addView(fixed);
        monthly=Ui.column(this); label(monthly,"REPEAT EVERY MONTH ON THE");
        offset=spinner(new String[]{"Last","Second-last","Third-last","Fourth-last"}); monthly.addView(offset,new LinearLayout.LayoutParams(-1,Ui.dp(this,56)));
        Ui.gap(monthly,8); String[] names=new String[7]; for(int i=0;i<7;i++) names[i]=DayOfWeek.of(i+1).getDisplayName(TextStyle.FULL,Locale.getDefault());
        weekday=spinner(names); monthly.addView(weekday,new LinearLayout.LayoutParams(-1,Ui.dp(this,56))); body.addView(monthly);
        int weekSelection=original!=null && original.isMonthly() ? original.weekday.getValue()-1 : 4;
        int offsetSelection=original!=null && original.isMonthly() ? original.fromEnd-1 : 0;
        weekday.setSelection(state!=null ? state.getInt("weekday",weekSelection) : weekSelection);
        offset.setSelection(state!=null ? state.getInt("offset",offsetSelection) : offsetSelection);
        Ui.gap(body,28); label(body,"PICK A COLOUR"); colorRow=Ui.row(this); body.addView(colorRow); paintColors();
        Ui.gap(body,28); LinearLayout previewBox=Ui.column(this); previewBox.setPadding(Ui.dp(this,20),Ui.dp(this,20),Ui.dp(this,20),Ui.dp(this,20)); previewBox.setBackground(Ui.shape(Ui.CARD,20,this));
        TextView previewLabel=Ui.text(this,"A LITTLE LOOK AHEAD",11,Ui.MUTED); previewLabel.setLetterSpacing(.1f); previewBox.addView(previewLabel); Ui.gap(previewBox,12);
        preview=Ui.text(this,"",16,Ui.MINT); preview.setLineSpacing(Ui.dp(this,8),1); previewBox.addView(preview); body.addView(previewBox);
        LinearLayout bottom=Ui.column(this); bottom.setPadding(Ui.dp(this,24),Ui.dp(this,12),Ui.dp(this,24),Ui.dp(this,16));
        Button save=Ui.button(this,original==null ? "Create countdown" : "Save changes",true); save.setOnClickListener(v -> save()); bottom.addView(save,new LinearLayout.LayoutParams(-1,Ui.dp(this,56))); root.addView(bottom);
        setContentView(root);
        type.setOnCheckedChangeListener((group,id) -> refreshPreview());
        AdapterView.OnItemSelectedListener listener=new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> parent,View view,int position,long id) { refreshPreview(); }
            public void onNothingSelected(AdapterView<?> parent) { }
        };
        weekday.setOnItemSelectedListener(listener); offset.setOnItemSelectedListener(listener);
        boolean isMonthly=state!=null ? state.getBoolean("monthly") : original!=null && original.isMonthly();
        type.check(isMonthly ? MONTHLY : FIXED); refreshPreview();
        title.clearFocus(); root.setFocusableInTouchMode(true); root.requestFocus();
    }

    private void label(LinearLayout parent,String value) {
        TextView text=Ui.text(this,value,11,Ui.MUTED); text.setLetterSpacing(.1f); Ui.bold(text); parent.addView(text); Ui.gap(parent,8);
    }

    private Spinner spinner(String[] choices) {
        Spinner s=new Spinner(this); ArrayAdapter<String> adapter=new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,choices);
        s.setAdapter(adapter); s.setBackground(Ui.shape(Ui.CARD,12,this)); s.setPadding(Ui.dp(this,12),0,Ui.dp(this,12),0); return s;
    }

    private Countdown draft(String name) {
        String id=eventId==null ? UUID.randomUUID().toString() : eventId;
        if(type.getCheckedRadioButtonId()==MONTHLY)
            return Countdown.monthly(id,name,DayOfWeek.of(Math.max(0,weekday.getSelectedItemPosition())+1),Math.max(0,offset.getSelectedItemPosition())+1,color);
        return Countdown.fixed(id,name,date,color);
    }

    private void refreshPreview() {
        if(preview==null) return;
        boolean repeats=type.getCheckedRadioButtonId()==MONTHLY; monthly.setVisibility(repeats ? View.VISIBLE : View.GONE); fixed.setVisibility(repeats ? View.GONE : View.VISIBLE);
        Countdown c=draft("Preview"); StringBuilder lines=new StringBuilder();
        for(LocalDate next:c.upcoming(LocalDate.now(),3)) { if(lines.length()>0) lines.append("\n"); lines.append(next.format(DATE)); }
        if(!repeats && date.isBefore(LocalDate.now())) lines.append("\n").append(Math.abs(c.days(LocalDate.now()))).append(" days ago — counts up after the date");
        else if(repeats) lines.append("\nRolls forward after each occurrence.");
        preview.setText(lines); preview.setTextColor(MainActivity.ACCENTS[Math.floorMod(color,MainActivity.ACCENTS.length)]);
    }

    private void paintColors() {
        colorRow.removeAllViews(); String[] names={"Mint","Lavender","Peach","Sky","Rose"};
        for(int i=0;i<MainActivity.ACCENTS.length;i++) {
            final int selected=i; TextView swatch=Ui.text(this,color==i ? "✓" : "",22,Ui.BG); swatch.setGravity(android.view.Gravity.CENTER);
            swatch.setBackground(Ui.shape(MainActivity.ACCENTS[i],24,this)); swatch.setContentDescription(names[i]+(color==i ? ", selected" : "")); swatch.setFocusable(true);
            LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,Ui.dp(this,48),1); p.setMarginEnd(Ui.dp(this,i==MainActivity.ACCENTS.length-1 ? 0 : 8)); colorRow.addView(swatch,p);
            swatch.setOnClickListener(v -> { color=selected; paintColors(); refreshPreview(); });
        }
    }

    private void save() {
        String name=title.getText().toString().trim();
        if(name.isEmpty()) { title.setError("Give your countdown a name"); title.requestFocus(); return; }
        new Store(this).save(draft(name)); CountdownWidget.updateAll(this); finish();
    }

    @Override protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out); out.putString("title",title.getText().toString()); out.putString("date",date.toString());
        out.putBoolean("monthly",type.getCheckedRadioButtonId()==MONTHLY); out.putInt("weekday",weekday.getSelectedItemPosition()); out.putInt("offset",offset.getSelectedItemPosition()); out.putInt("color",color);
    }
}
