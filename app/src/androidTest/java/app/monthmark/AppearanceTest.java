package app.monthmark;

import android.app.Activity;
import android.app.Instrumentation;
import android.appwidget.*;
import android.content.*;
import android.graphics.*;
import android.net.Uri;
import android.os.*;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import app.monthmark.core.Countdown;
import static org.junit.Assert.*;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.matcher.ViewMatchers.*;

@RunWith(AndroidJUnit4.class)
public class AppearanceTest {
    private final Instrumentation instrumentation=InstrumentationRegistry.getInstrumentation();
    private final Context context=instrumentation.getTargetContext();

    @Test public void coloursIconsAndImportedPhotoSurviveEditorAndRenderInWidget() throws Exception {
        Store store=new Store(context); for(Countdown c:store.all()) store.delete(c.id);
        store.save(Countdown.monthly("style-payday","Payday",DayOfWeek.FRIDAY,1,0).withAppearance("💷",""));
        store.save(Countdown.fixed("style-trip","Summer escape",LocalDate.now().plusDays(21),3));
        store.save(Countdown.fixed("style-birthday","Birthday",LocalDate.now().plusDays(40),4).withAppearance("🎂",""));
        Uri source=fixture(); String imported;
        Intent edit=new Intent(context,EditorActivity.class).putExtra("event_id","style-trip");
        try(ActivityScenario<EditorActivity> editor=ActivityScenario.launch(edit)) {
            onView(withContentDescription("Coral")).perform(scrollTo(),click());
            onView(withText("Choose icon  ✨")).perform(scrollTo(),click());
            onView(withText("Travel")).perform(click());
            editor.onActivity(activity -> activity.onActivityResult(EditorActivity.REQUEST_PHOTO,Activity.RESULT_OK,new Intent().setData(source)));
            AtomicBoolean ready=new AtomicBoolean(false);
            long deadline=SystemClock.uptimeMillis()+10000;
            while(!ready.get() && SystemClock.uptimeMillis()<deadline) {
                editor.onActivity(activity -> ready.set(activity.findViewById(R.id.remove_photo).getVisibility()==View.VISIBLE));
                if(!ready.get()) SystemClock.sleep(50);
            }
            assertTrue("Imported photo must become available in the editor",ready.get());
            editor.recreate();
            onView(withText("Save changes")).perform(click());
        }
        Countdown event=new Store(context).get("style-trip");
        assertNotNull(event); assertEquals("✈️",event.icon); assertEquals(7,event.color); assertTrue(event.background.startsWith("photo:"));
        imported=event.background; assertTrue(Appearance.photo(context,imported));
        // Removing the original source must not break the app's imported copy.
        if("content".equals(source.getScheme())) context.getContentResolver().delete(source,null,null);
        else if(source.getPath()!=null) new File(source.getPath()).delete();
        assertTrue(Appearance.photo(context,imported));
        Bitmap compact=Appearance.widgetBackground(context,event,150,140);
        assertTrue(compact.getAllocationByteCount()<=480*480*4); assertEquals(150f/140,compact.getWidth()/(float)compact.getHeight(),.02f); compact.recycle();

        try(ActivityScenario<MainActivity> home=ActivityScenario.launch(MainActivity.class)) {
            screenshot("colourful-app.png");
            AppWidgetHost[] host=new AppWidgetHost[1]; int[] id=new int[1];
            instrumentation.getUiAutomation().adoptShellPermissionIdentity("android.permission.BIND_APPWIDGET");
            try {
                instrumentation.runOnMainSync(() -> {
                    host[0]=new AppWidgetHost(context,6363); host[0].startListening(); id[0]=host[0].allocateAppWidgetId();
                    Bundle options=new Bundle(); options.putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH,300); options.putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH,300);
                    options.putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT,340); options.putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT,340);
                    assertTrue(AppWidgetManager.getInstance(context).bindAppWidgetIdIfAllowed(id[0],new ComponentName(context,LargeWidget.class),options));
                    store.bindWidget(id[0],Arrays.asList("style-payday","style-trip","style-birthday")); CountdownWidget.updateAll(context);
                });
                home.onActivity(activity -> {
                    AppWidgetHostView widget=host[0].createView(activity,id[0],AppWidgetManager.getInstance(context).getAppWidgetInfo(id[0]));
                    FrameLayout frame=new FrameLayout(activity); frame.setBackgroundColor(Ui.BG);
                    frame.addView(widget,new FrameLayout.LayoutParams(Ui.dp(activity,300),Ui.dp(activity,340),Gravity.CENTER)); activity.setContentView(frame);
                    assertEquals("✈️",((TextView)widget.findViewById(R.id.icon_2)).getText().toString());
                    assertNotNull(((ImageView)widget.findViewById(R.id.background_2)).getDrawable());
                });
                screenshot("photo-widget.png");
            } finally {
                if(host[0]!=null) instrumentation.runOnMainSync(() -> { host[0].stopListening(); host[0].deleteHost(); });
                instrumentation.getUiAutomation().dropShellPermissionIdentity();
            }
        }
    }

    @Test public void oldSavedCountdownsKeepWorkingWithoutAppearanceFields() {
        android.content.SharedPreferences prefs=context.getSharedPreferences("monthmark",Context.MODE_PRIVATE);
        String previous=prefs.getString("events","[]");
        try {
            prefs.edit().putString("events","[{\"id\":\"legacy\",\"title\":\"Old payday\",\"color\":2,\"weekday\":5,\"fromEnd\":1}]").commit();
            Countdown event=new Store(context).get("legacy"); assertNotNull(event);
            assertEquals("✨",event.icon); assertEquals("",event.background); assertEquals(DayOfWeek.FRIDAY,event.weekday); assertEquals(1,event.fromEnd);
        } finally { prefs.edit().putString("events",previous).commit(); }
    }

    @Test public void nonImageImportIsRejected() throws Exception {
        File text=new File(context.getCacheDir(),"not-an-image.txt");
        try(FileWriter out=new FileWriter(text)) { out.write("not an image"); }
        try { Appearance.importPhoto(context,Uri.fromFile(text)); fail("A non-image must be rejected"); }
        catch(IOException expected) { assertTrue(expected.getMessage().contains("format")); }
        finally { text.delete(); }
    }

    private Uri fixture() throws Exception {
        Bitmap bitmap=Bitmap.createBitmap(1600,1000,Bitmap.Config.ARGB_8888); Canvas canvas=new Canvas(bitmap); Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setShader(new LinearGradient(0,0,0,1000,new int[]{0xff3976ad,0xfff3bb88},null,Shader.TileMode.CLAMP)); canvas.drawRect(0,0,1600,1000,paint); paint.setShader(null);
        paint.setColor(0xffffdc9c); canvas.drawCircle(1180,260,135,paint);
        Path mountain=new Path(); mountain.moveTo(0,1000); mountain.lineTo(0,780); mountain.lineTo(480,280); mountain.lineTo(930,900); mountain.lineTo(1250,560); mountain.lineTo(1600,890); mountain.lineTo(1600,1000); mountain.close();
        paint.setColor(0xff28515f); canvas.drawPath(mountain,paint);
        Uri uri;
        if(Build.VERSION.SDK_INT>=29) {
            ContentValues values=new ContentValues(); values.put(MediaStore.Images.Media.DISPLAY_NAME,"Monthmark test landscape.jpg"); values.put(MediaStore.Images.Media.MIME_TYPE,"image/jpeg");
            values.put(MediaStore.Images.Media.RELATIVE_PATH,"Pictures/MonthmarkTests");
            uri=context.getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values); assertNotNull(uri);
            try(OutputStream out=context.getContentResolver().openOutputStream(uri)) { assertNotNull(out); bitmap.compress(Bitmap.CompressFormat.JPEG,90,out); }
        } else {
            File image=new File(context.getCacheDir(),"test-landscape.jpg"); try(FileOutputStream out=new FileOutputStream(image)) { bitmap.compress(Bitmap.CompressFormat.JPEG,90,out); } uri=Uri.fromFile(image);
        }
        bitmap.recycle(); return uri;
    }

    private void screenshot(String name) throws Exception {
        instrumentation.waitForIdleSync(); SystemClock.sleep(350);
        File folder=new File(context.getExternalFilesDir(null),"screenshots"); folder.mkdirs();
        Bitmap image=instrumentation.getUiAutomation().takeScreenshot(); assertNotNull(image);
        File file=new File(folder,name); try(FileOutputStream out=new FileOutputStream(file)) { image.compress(Bitmap.CompressFormat.PNG,100,out); } image.recycle();
        shell("mkdir -p /sdcard/Download/monthmark-screenshots"); shell("cp "+file.getAbsolutePath()+" /sdcard/Download/monthmark-screenshots/"+name);
    }
    private void shell(String command) throws IOException {
        try(InputStream stream=new ParcelFileDescriptor.AutoCloseInputStream(instrumentation.getUiAutomation().executeShellCommand(command))) {
            byte[] buffer=new byte[4096]; while(stream.read(buffer)!=-1) { }
        }
    }
}
