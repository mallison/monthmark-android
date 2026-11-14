package app.monthmark;

import android.app.Activity;
import android.app.Instrumentation;
import android.appwidget.*;
import android.content.*;
import android.graphics.Point;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.*;
import java.time.*;
import java.util.*;
import app.monthmark.core.Countdown;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class LauncherPlacementTest {
    private final Instrumentation instrumentation=InstrumentationRegistry.getInstrumentation();
    private final Context context=instrumentation.getTargetContext();
    private final UiDevice device=UiDevice.getInstance(instrumentation);

    private void seed() {
        Store store=new Store(context);
        for(Countdown c:store.all()) store.delete(c.id);
        store.save(Countdown.fixed("launcher-holiday","Holiday",LocalDate.now().plusDays(10),0));
        store.save(Countdown.monthly("launcher-payday","Payday",DayOfWeek.FRIDAY,1,1));
    }

    @Test public void pendingWidgetKeepsHostPlacementViewUntilConfigured() throws Exception {
        AppWidgetHost[] host=new AppWidgetHost[1]; int[] id=new int[1];
        instrumentation.getUiAutomation().adoptShellPermissionIdentity("android.permission.BIND_APPWIDGET");
        try {
            instrumentation.runOnMainSync(() -> {
                host[0]=new AppWidgetHost(context,5353); id[0]=host[0].allocateAppWidgetId();
                AppWidgetManager manager=AppWidgetManager.getInstance(context);
                assertTrue(manager.bindAppWidgetIdIfAllowed(id[0],new ComponentName(context,CountdownWidget.class)));
                assertFalse(new Store(context).isWidgetConfigured(id[0]));
                new CountdownWidget().onReceive(context,new Intent(AppWidgetManager.ACTION_APPWIDGET_ENABLED));
                new CountdownWidget().onAppWidgetOptionsChanged(context,manager,id[0],new Bundle());
                CountdownWidget.updateAll(context);
                AppWidgetHostView view=host[0].createView(context,id[0],manager.getAppWidgetInfo(id[0]));
                assertEquals("Monthmark",((android.widget.TextView)view.findViewById(R.id.widget_title)).getText().toString());
                assertFalse(new Store(context).isWidgetConfigured(id[0]));
            });
        } finally {
            if(host[0]!=null) instrumentation.runOnMainSync(() -> host[0].deleteHost());
            instrumentation.getUiAutomation().dropShellPermissionIdentity();
        }
    }

    @Test public void configurationReturnsSelectionToExternalHost() throws Exception {
        seed();
        AppWidgetHost[] host=new AppWidgetHost[1]; int[] id=new int[1];
        instrumentation.getUiAutomation().adoptShellPermissionIdentity("android.permission.BIND_APPWIDGET");
        try {
            instrumentation.runOnMainSync(() -> {
                host[0]=new AppWidgetHost(context,5252); id[0]=host[0].allocateAppWidgetId();
                assertTrue(AppWidgetManager.getInstance(context).bindAppWidgetIdIfAllowed(id[0],new ComponentName(context,MediumWidget.class)));
            });
        } finally { instrumentation.getUiAutomation().dropShellPermissionIdentity(); }
        try {
            Intent configure=new Intent(context,MainActivity.class).setAction(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,id[0]);
            try(ActivityScenario<MainActivity> activity=ActivityScenario.launchActivityForResult(configure)) {
                required(By.text("Holiday")).click();
                // Selecting the first card rebuilds the screen; find the second again.
                UiObject2 payday=device.findObject(By.text("Payday"));
                if(payday==null || !payday.isClickable()) device.swipe(device.getDisplayWidth()/2,device.getDisplayHeight()*3/4,device.getDisplayWidth()/2,device.getDisplayHeight()/3,25);
                required(By.text("Payday")).click();
                required(By.text("Use selected (2)")).click();
                assertEquals(Activity.RESULT_OK,activity.getResult().getResultCode());
                assertEquals(id[0],activity.getResult().getResultData().getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,-1));
            }
            assertEquals(new HashSet<>(Arrays.asList("launcher-holiday","launcher-payday")),new HashSet<>(new Store(context).widgetEvents(id[0])));
        } finally {
            capture("configuration");
            if(host[0]!=null) instrumentation.runOnMainSync(() -> host[0].deleteHost());
        }
    }

    @Test public void droppedWidgetSurvivesLauncherConfiguration() throws Exception {
        seed();
        try {
            context.startActivity(new Intent(context,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            required(By.text("Holiday"));
            device.pressHome(); device.waitForIdle();
            String launcher=device.getLauncherPackageName();
            int x=device.getDisplayWidth()/2,y=device.getDisplayHeight()/2;
            device.swipe(x,y,x,y,200);
            required(By.text("Widgets")).click();
            UiObject2 search=device.wait(Until.findObject(By.clazz("android.widget.EditText")),5000);
            if(search!=null) search.setText("Monthmark");
            UiObject2 header=device.wait(Until.findObject(By.res("com.google.android.apps.nexuslauncher","app_title").text("Monthmark")),5000);
            if(header!=null) header.click();
            device.waitForIdle(); capture("picker");
            UiObject2 preview=device.wait(Until.findObject(By.desc("Monthmark · Small")),10000);
            assertNotNull("Monthmark widget preview missing",preview);
            preview.drag(new Point(device.getDisplayWidth()/2,device.getDisplayHeight()/3),500);
            capture("after-drop");
            required(By.text("Holiday")).click();
            required(By.text("Use selected (1)")).click();
            device.pressHome(); device.waitForIdle();
            assertNotNull("Dropped widget must remain on the launcher",device.wait(Until.findObject(By.res("app.monthmark","widget_title").text("Holiday")),10000));
            capture("placed-widget");
        } finally { capture("launcher-final"); }
    }

    private UiObject2 required(BySelector selector) {
        UiObject2 result=device.wait(Until.findObject(selector),10000);
        assertNotNull("Missing launcher/configuration view: "+selector,result); return result;
    }

    private String shell(String command) throws IOException {
        try(InputStream stream=new ParcelFileDescriptor.AutoCloseInputStream(instrumentation.getUiAutomation().executeShellCommand(command))) {
            ByteArrayOutputStream bytes=new ByteArrayOutputStream(); byte[] buffer=new byte[4096]; int count;
            while((count=stream.read(buffer))!=-1) bytes.write(buffer,0,count);
            return bytes.toString("UTF-8");
        }
    }

    private void capture(String name) throws Exception {
        File folder=new File(context.getExternalFilesDir(null),"diagnostics"); folder.mkdirs();
        device.takeScreenshot(new File(folder,name+".png"));
        device.dumpWindowHierarchy(new File(folder,name+".xml"));
        try(FileOutputStream out=new FileOutputStream(new File(folder,name+"-logcat.txt"))) { out.write(shell("logcat -d -v threadtime").getBytes("UTF-8")); }
        shell("mkdir -p /sdcard/Download/monthmark-screenshots");
        for(File file:Objects.requireNonNull(folder.listFiles())) shell("cp "+file.getAbsolutePath()+" /sdcard/Download/monthmark-screenshots/"+file.getName());
    }
}
