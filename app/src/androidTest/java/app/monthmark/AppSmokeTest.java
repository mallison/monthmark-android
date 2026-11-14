package app.monthmark;

import android.app.Instrumentation;
import android.appwidget.*;
import android.content.*;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.*;
import java.time.*;
import java.util.*;
import app.monthmark.core.Countdown;
import static androidx.test.espresso.Espresso.*;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.assertion.ViewAssertions.*;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class AppSmokeTest {
    private final Instrumentation instrumentation=InstrumentationRegistry.getInstrumentation();
    private final Context context=instrumentation.getTargetContext();

    @Test public void countdownCreationPersistenceAndResponsiveWidgets() throws Exception {
        context.getSharedPreferences("monthmark",Context.MODE_PRIVATE).edit().clear().commit();
        try(ActivityScenario<MainActivity> home=ActivityScenario.launch(MainActivity.class)) {
            onView(withText("+  Add countdown")).perform(click());
            onView(isAssignableFrom(EditText.class)).perform(replaceText("Days to holiday"),closeSoftKeyboard());
            onView(withText("Create countdown")).perform(click());
            onView(withText("Days to holiday")).check(matches(isDisplayed()));
            onView(withText("+  Add countdown")).perform(click());
            onView(isAssignableFrom(EditText.class)).perform(replaceText("Days to payday"),closeSoftKeyboard());
            onView(withText("Monthly weekday  ·  e.g. last Friday")).perform(scrollTo(),click());
            onView(withText("Create countdown")).perform(click());
            home.recreate();
            Store store=new Store(context); List<Countdown> created=store.all(); assertEquals(2,created.size());
            Countdown fixed=created.stream().filter(c -> !c.isMonthly()).findFirst().get();
            Countdown payday=created.stream().filter(Countdown::isMonthly).findFirst().get();
            assertEquals(DayOfWeek.FRIDAY,payday.weekday); assertEquals(1,payday.fromEnd);
            assertEquals(LocalDate.now().plusDays(1),fixed.fixedDate);
            onView(withText("Days to payday")).check(matches(isDisplayed()));
            screenshot("app.png");

            // Editing preserves the id, so existing widgets keep following the same event.
            onView(withText("Days to holiday")).perform(click());
            onView(withText("Edit countdown")).perform(click());
            onView(isAssignableFrom(EditText.class)).perform(replaceText("Days to my holiday"),closeSoftKeyboard());
            onView(withText("Save changes")).perform(click());
            assertEquals("Days to my holiday",new Store(context).get(fixed.id).title);
            assertEquals(2,new Store(context).all().size());

            store.save(Countdown.fixed("birthday","Days to birthday",LocalDate.now().plusDays(60),1));
            store.save(Countdown.fixed("adventure","Next adventure",LocalDate.now().plusDays(90),2));
            List<String> allIds=Arrays.asList(fixed.id,payday.id,"birthday","adventure");
            instrumentation.getUiAutomation().adoptShellPermissionIdentity("android.permission.BIND_APPWIDGET");
            AppWidgetHost[] host=new AppWidgetHost[1]; int[] ids=new int[3];
            try {
                instrumentation.runOnMainSync(() -> {
                    host[0]=new AppWidgetHost(context,4242); host[0].startListening();
                    Class<?>[] providers={CountdownWidget.class,MediumWidget.class,LargeWidget.class};
                    int[] widths={150,300,300}, heights={140,180,340};
                    AppWidgetManager manager=AppWidgetManager.getInstance(context);
                    for(int i=0;i<3;i++) {
                        ids[i]=host[0].allocateAppWidgetId(); Bundle options=options(widths[i],heights[i]);
                        assertTrue("Widget binding must succeed",manager.bindAppWidgetIdIfAllowed(ids[i],new ComponentName(context,providers[i]),options));
                        store.bindWidget(ids[i],allIds); CountdownWidget.update(context,manager,ids[i]);
                    }
                });
                final AppWidgetHostView[] view=new AppWidgetHostView[1];
                home.onActivity(activity -> {
                    view[0]=showWidget(activity,host[0],ids[0],150,140);
                    assertNotNull(view[0].findViewById(R.id.widget_title));
                    assertTrue(((TextView)view[0].findViewById(R.id.widget_date)).getText().toString().contains("+3 more"));
                }); screenshot("widget-small.png");
                home.onActivity(activity -> {
                    view[0]=showWidget(activity,host[0],ids[1],300,180);
                    assertNotNull(view[0].findViewById(R.id.title_1));
                    assertEquals(View.VISIBLE,view[0].findViewById(R.id.widget_row_2).getVisibility());
                    assertEquals(View.GONE,view[0].findViewById(R.id.widget_row_3).getVisibility());
                }); screenshot("widget-medium.png");
                home.onActivity(activity -> {
                    view[0]=showWidget(activity,host[0],ids[2],300,340);
                    assertEquals(View.VISIBLE,view[0].findViewById(R.id.widget_row_4).getVisibility());
                    Set<String> titles=new HashSet<>();
                    for(int id:new int[]{R.id.title_1,R.id.title_2,R.id.title_3,R.id.title_4}) titles.add(((TextView)view[0].findViewById(id)).getText().toString());
                    assertTrue(titles.contains("Days to payday")); assertTrue(titles.contains("Days to my holiday")); assertTrue(titles.contains("Next adventure"));
                }); screenshot("widget-large.png");
                // Shrinking changes layout, never the selected events; widget choices remain independent.
                instrumentation.runOnMainSync(() -> {
                    AppWidgetManager manager=AppWidgetManager.getInstance(context);
                    manager.updateAppWidgetOptions(ids[2],options(150,140));
                    CountdownWidget.update(context,manager,ids[2]);
                    store.bindWidget(ids[0],Collections.singletonList("birthday"));
                    CountdownWidget.update(context,manager,ids[0]);
                });
                assertEquals(allIds,new Store(context).widgetEvents(ids[2]));
                assertEquals(Collections.singletonList("birthday"),new Store(context).widgetEvents(ids[0]));
                home.onActivity(activity -> {
                    AppWidgetHostView shrunk=showWidget(activity,host[0],ids[2],150,140);
                    assertNotNull(shrunk.findViewById(R.id.widget_title));
                });
                store.delete("birthday");
                instrumentation.runOnMainSync(() -> CountdownWidget.updateAll(context));
                home.onActivity(activity -> {
                    AppWidgetHostView empty=showWidget(activity,host[0],ids[0],150,140);
                    assertEquals("Choose a countdown",((TextView)empty.findViewById(R.id.widget_title)).getText().toString());
                });
            } finally {
                if(host[0]!=null) instrumentation.runOnMainSync(() -> {host[0].stopListening();host[0].deleteHost();});
                instrumentation.getUiAutomation().dropShellPermissionIdentity();
            }
        }
    }

    private static Bundle options(int width,int height) {
        Bundle b=new Bundle(); b.putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH,width); b.putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH,width);
        b.putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT,height); b.putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT,height); return b;
    }

    private AppWidgetHostView showWidget(MainActivity activity,AppWidgetHost host,int id,int width,int height) {
        AppWidgetHostView widget=host.createView(activity,id,AppWidgetManager.getInstance(context).getAppWidgetInfo(id));
        FrameLayout background=new FrameLayout(activity); background.setBackgroundColor(Ui.BG);
        FrameLayout.LayoutParams params=new FrameLayout.LayoutParams(Ui.dp(activity,width),Ui.dp(activity,height),Gravity.CENTER);
        background.addView(widget,params); activity.setContentView(background); return widget;
    }

    private void screenshot(String name) throws Exception {
        instrumentation.waitForIdleSync(); android.os.SystemClock.sleep(350);
        File folder=new File(context.getExternalFilesDir(null),"screenshots"); if(!folder.exists() && !folder.mkdirs()) throw new IOException("Cannot create screenshot directory");
        Bitmap image=instrumentation.getUiAutomation().takeScreenshot(); assertNotNull(image);
        try(FileOutputStream out=new FileOutputStream(new File(folder,name))) { image.compress(Bitmap.CompressFormat.PNG,100,out); }
        image.recycle();
    }
}
