package app.monthmark;

import android.app.Activity;
import android.app.DatePickerDialog;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.*;
import java.util.concurrent.*;
import app.monthmark.core.Countdown;

public final class EditorActivity extends Activity {
    private EditText title;
    private RadioGroup type;
    private Spinner weekday, offset;
    private LinearLayout monthly, fixed, colorRow, previewBox;
    private TextView preview, previewTitle, previewDays;
    private Button dateButton, iconButton, photoButton, removePhoto, saveButton;
    private LocalDate date=LocalDate.now().plusDays(1);
    private int color=0;
    private String eventId;
    private String icon="✨", background="", originalBackground="", pendingImport;
    private boolean saved=false, photoLoading=false;
    private final ExecutorService importer=Executors.newSingleThreadExecutor();
    static final int REQUEST_PHOTO=42;
    private static final int FIXED=1001, MONTHLY=1002;
    private static final DateTimeFormatter DATE=DateTimeFormatter.ofPattern("EEE, d MMM yyyy",Locale.getDefault());

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        eventId=getIntent().getStringExtra("event_id"); Countdown original=new Store(this).get(eventId);
        if (eventId!=null && original==null) { finish(); return; }
        if(original!=null) { color=original.color; icon=original.icon; background=original.background; originalBackground=background; if(!original.isMonthly()) date=original.fixedDate; }
        if(state!=null) {
            date=LocalDate.parse(state.getString("date",date.toString())); color=state.getInt("color",color);
            icon=state.getString("icon",icon); background=state.getString("background",background); pendingImport=state.getString("pending_import");
            eventId=state.getString("event_id",eventId);
        }
        if(eventId==null) eventId=UUID.randomUUID().toString();
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
        Ui.gap(body,28); label(body,"PICK A COLOUR"); colorRow=Ui.column(this); body.addView(colorRow); paintColors();
        Ui.gap(body,24); label(body,"MAKE IT YOURS");
        iconButton=Ui.button(this,"Choose icon  "+icon,false); iconButton.setOnClickListener(v -> chooseIcon()); body.addView(iconButton,new LinearLayout.LayoutParams(-1,Ui.dp(this,56)));
        Ui.gap(body,10); photoButton=Ui.button(this,"Choose background photo",false); photoButton.setOnClickListener(v -> choosePhoto()); body.addView(photoButton,new LinearLayout.LayoutParams(-1,Ui.dp(this,56)));
        Ui.gap(body,8); removePhoto=Ui.button(this,"Remove photo",false); removePhoto.setId(R.id.remove_photo); removePhoto.setOnClickListener(v -> {
            String previous=background; background=""; Appearance.deleteIfUnused(this,previous); refreshPreview();
        }); body.addView(removePhoto,new LinearLayout.LayoutParams(-1,Ui.dp(this,48)));
        Ui.gap(body,24); label(body,"LIVE PREVIEW"); previewBox=Ui.column(this); previewBox.setPadding(Ui.dp(this,22),Ui.dp(this,22),Ui.dp(this,22),Ui.dp(this,22));
        previewTitle=Ui.text(this,"",18,Ui.BG); Ui.bold(previewTitle); previewBox.addView(previewTitle); Ui.gap(previewBox,12);
        previewDays=Ui.text(this,"",32,Ui.BG); previewBox.addView(previewDays); Ui.gap(previewBox,12);
        preview=Ui.text(this,"",14,Ui.BG); preview.setLineSpacing(Ui.dp(this,7),1); previewBox.addView(preview); body.addView(previewBox);
        LinearLayout bottom=Ui.column(this); bottom.setPadding(Ui.dp(this,24),Ui.dp(this,12),Ui.dp(this,24),Ui.dp(this,16));
        saveButton=Ui.button(this,original==null ? "Create countdown" : "Save changes",true); saveButton.setOnClickListener(v -> save()); bottom.addView(saveButton,new LinearLayout.LayoutParams(-1,Ui.dp(this,56))); root.addView(bottom);
        setContentView(root);
        type.setOnCheckedChangeListener((group,id) -> refreshPreview());
        AdapterView.OnItemSelectedListener listener=new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> parent,View view,int position,long id) { refreshPreview(); }
            public void onNothingSelected(AdapterView<?> parent) { }
        };
        weekday.setOnItemSelectedListener(listener); offset.setOnItemSelectedListener(listener);
        boolean isMonthly=state!=null ? state.getBoolean("monthly") : original!=null && original.isMonthly();
        type.check(isMonthly ? MONTHLY : FIXED); refreshPreview();
        title.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence s,int start,int count,int after) { }
            public void onTextChanged(CharSequence s,int start,int before,int count) { refreshPreview(); }
            public void afterTextChanged(android.text.Editable text) { }
        });
        title.clearFocus(); root.setFocusableInTouchMode(true); root.requestFocus();
        if(pendingImport!=null) importPhoto(Uri.parse(pendingImport));
    }

    private void label(LinearLayout parent,String value) {
        TextView text=Ui.text(this,value,11,Ui.MUTED); text.setLetterSpacing(.1f); Ui.bold(text); parent.addView(text); Ui.gap(parent,8);
    }

    private Spinner spinner(String[] choices) {
        Spinner s=new Spinner(this); ArrayAdapter<String> adapter=new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,choices);
        s.setAdapter(adapter); s.setBackground(Ui.shape(Ui.CARD,12,this)); s.setPadding(Ui.dp(this,12),0,Ui.dp(this,12),0); return s;
    }

    private Countdown draft(String name) {
        String id=eventId;
        if(type.getCheckedRadioButtonId()==MONTHLY)
            return Countdown.monthly(id,name,DayOfWeek.of(Math.max(0,weekday.getSelectedItemPosition())+1),Math.max(0,offset.getSelectedItemPosition())+1,color).withAppearance(icon,background);
        return Countdown.fixed(id,name,date,color).withAppearance(icon,background);
    }

    private void refreshPreview() {
        if(preview==null) return;
        boolean repeats=type.getCheckedRadioButtonId()==MONTHLY; monthly.setVisibility(repeats ? View.VISIBLE : View.GONE); fixed.setVisibility(repeats ? View.GONE : View.VISIBLE);
        String name=title.getText().toString().trim();
        Countdown c=draft(name.isEmpty() ? "Your next good thing" : name); StringBuilder lines=new StringBuilder();
        for(LocalDate next:c.upcoming(LocalDate.now(),3)) { if(lines.length()>0) lines.append("\n"); lines.append(next.format(DATE)); }
        if(!repeats && date.isBefore(LocalDate.now())) lines.append("\n").append(Math.abs(c.days(LocalDate.now()))).append(" days ago — counts up after the date");
        else if(repeats) lines.append("\nRolls forward after each occurrence.");
        boolean photo=Appearance.photo(this,background); int text=photo ? Ui.WHITE : Ui.BG;
        previewBox.setBackground(Appearance.background(this,c));
        previewTitle.setText(icon+"  "+c.title); previewTitle.setTextColor(text);
        long days=c.days(LocalDate.now()); previewDays.setText(days==0 ? "Today" : Math.abs(days)+(days<0 ? " days ago" : days==1 ? " day to go" : " days to go")); previewDays.setTextColor(text);
        preview.setText(lines); preview.setTextColor(photo ? 0xffe5eee8 : 0xff344b40);
        iconButton.setText("Choose icon  "+icon); photoButton.setText(photoLoading ? "Loading photo…" : photo ? "Change background photo" : "Choose background photo");
        photoButton.setEnabled(!photoLoading); removePhoto.setVisibility(photo ? View.VISIBLE : View.GONE); saveButton.setEnabled(!photoLoading);
    }

    private void paintColors() {
        colorRow.removeAllViews(); LinearLayout row=null;
        for(int i=0;i<MainActivity.ACCENTS.length;i++) {
            if(i%5==0) { if(row!=null) Ui.gap(colorRow,10); row=Ui.row(this); colorRow.addView(row); }
            final int selected=i; TextView swatch=Ui.text(this,color==i ? "✓" : "",22,Ui.BG); swatch.setGravity(android.view.Gravity.CENTER);
            swatch.setBackground(Ui.shape(MainActivity.ACCENTS[i],24,this)); swatch.setContentDescription(Appearance.COLOR_NAMES[i]+(color==i ? ", selected" : "")); swatch.setFocusable(true);
            LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,Ui.dp(this,48),1); p.setMarginEnd(Ui.dp(this,i%5==4 ? 0 : 8)); row.addView(swatch,p);
            swatch.setOnClickListener(v -> { color=selected; paintColors(); refreshPreview(); });
        }
    }

    private void save() {
        if(photoLoading) return;
        String name=title.getText().toString().trim();
        if(name.isEmpty()) { title.setError("Give your countdown a name"); title.requestFocus(); return; }
        new Store(this).save(draft(name)); saved=true;
        if(!originalBackground.equals(background)) Appearance.deleteIfUnused(this,originalBackground);
        CountdownWidget.updateAll(this); finish();
    }

    private void chooseIcon() {
        ScrollView scroll=new ScrollView(this); LinearLayout grid=Ui.column(this); grid.setPadding(Ui.dp(this,12),Ui.dp(this,8),Ui.dp(this,12),Ui.dp(this,8)); scroll.addView(grid);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Choose an icon").setView(scroll).setNegativeButton("Cancel",null).create();
        LinearLayout row=null;
        for(int i=0;i<Appearance.ICONS.length;i++) {
            if(i%4==0) { row=Ui.row(this); grid.addView(row); }
            final int chosen=i; LinearLayout cell=Ui.column(this); cell.setGravity(android.view.Gravity.CENTER); cell.setContentDescription(Appearance.ICON_NAMES[i]);
            TextView symbol=Ui.text(this,Appearance.ICONS[i],30,Ui.WHITE); symbol.setGravity(android.view.Gravity.CENTER); cell.addView(symbol);
            TextView label=Ui.text(this,Appearance.ICON_NAMES[i],11,Ui.MUTED); label.setGravity(android.view.Gravity.CENTER); cell.addView(label);
            LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,Ui.dp(this,76),1); row.addView(cell,p); cell.setFocusable(true);
            cell.setOnClickListener(v -> { icon=Appearance.ICONS[chosen]; refreshPreview(); dialog.dismiss(); });
        }
        dialog.show();
    }

    private void choosePhoto() {
        Intent pick=new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("image/*").addCategory(Intent.CATEGORY_OPENABLE).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try { startActivityForResult(pick,REQUEST_PHOTO); }
        catch(android.content.ActivityNotFoundException error) { Toast.makeText(this,"No photo picker is available",Toast.LENGTH_LONG).show(); }
    }

    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data) {
        super.onActivityResult(requestCode,resultCode,data);
        if(requestCode==REQUEST_PHOTO && resultCode==RESULT_OK && data!=null && data.getData()!=null) importPhoto(data.getData());
    }

    private void importPhoto(Uri uri) {
        pendingImport=uri.toString(); photoLoading=true; refreshPreview();
        importer.execute(() -> {
            String imported=null; Exception failure=null;
            try { imported=Appearance.importPhoto(getApplicationContext(),uri); }
            catch(Exception error) { failure=error; }
            catch(OutOfMemoryError error) { failure=new java.io.IOException("This photo is too large",error); }
            final String result=imported;
            runOnUiThread(() -> {
                if(isFinishing() || isDestroyed()) { if(result!=null) Appearance.deleteIfUnused(getApplicationContext(),result); return; }
                photoLoading=false; pendingImport=null;
                if(result!=null) { String previous=background; background=result; Appearance.deleteIfUnused(this,previous); }
                else Toast.makeText(this,"Could not open this photo. Try a JPEG or PNG image.",Toast.LENGTH_LONG).show();
                refreshPreview();
            });
        });
    }

    @Override protected void onDestroy() {
        if(!saved && isFinishing()) Appearance.deleteIfUnused(getApplicationContext(),background);
        importer.shutdown(); super.onDestroy();
    }

    @Override protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out); out.putString("title",title.getText().toString()); out.putString("date",date.toString());
        out.putBoolean("monthly",type.getCheckedRadioButtonId()==MONTHLY); out.putInt("weekday",weekday.getSelectedItemPosition()); out.putInt("offset",offset.getSelectedItemPosition()); out.putInt("color",color);
        out.putString("icon",icon); out.putString("background",background); out.putString("event_id",eventId); out.putString("pending_import",pendingImport);
    }
}
