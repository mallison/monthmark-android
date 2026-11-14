package app.monthmark;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

final class Ui {
    static final int BG = 0xff101613, CARD = 0xff1c2720, WHITE = 0xffeff5ef, MUTED = 0xffa3b4a7, MINT = 0xffbbf5cc;
    static int dp(Context c, float value) { return Math.round(c.getResources().getDisplayMetrics().density*value); }
    static TextView text(Context c, String value, float size, int color) {
        TextView t = new TextView(c); t.setText(value); t.setTextSize(size); t.setTextColor(color);
        t.setFontFeatureSettings("tnum"); return t;
    }
    static void bold(TextView t) { t.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL)); }
    static GradientDrawable shape(int color, int radius, Context c) {
        GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(c,radius)); return d;
    }
    static LinearLayout column(Context c) {
        LinearLayout v = new LinearLayout(c); v.setOrientation(LinearLayout.VERTICAL); return v;
    }
    static LinearLayout row(Context c) {
        LinearLayout v = new LinearLayout(c); v.setOrientation(LinearLayout.HORIZONTAL); v.setGravity(android.view.Gravity.CENTER_VERTICAL); return v;
    }
    static void gap(LinearLayout layout, int height) {
        View v = new View(layout.getContext()); layout.addView(v,new LinearLayout.LayoutParams(1,dp(layout.getContext(),height)));
    }
    static Button button(Context c, String label, boolean primary) {
        Button b = new Button(c); b.setText(label); b.setAllCaps(false); b.setTextSize(15); bold(b);
        b.setTextColor(primary ? BG : WHITE); b.setBackground(shape(primary ? MINT : CARD,16,c));
        b.setPadding(dp(c,16),dp(c,10),dp(c,16),dp(c,10)); b.setMinimumHeight(dp(c,52));
        return b;
    }
    static void insets(View root) {
        root.setOnApplyWindowInsetsListener((v,insets) -> {
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout() | WindowInsets.Type.ime());
                v.setPadding(bars.left,bars.top,bars.right,bars.bottom);
            } else v.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());
            return insets;
        });
        root.requestApplyInsets();
    }
}
