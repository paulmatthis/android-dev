package dev.eistcontrols.widget;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;

/** One-time setup: turn on access, pick the skip length, and add the widget. */
public class SetupActivity extends Activity {
    private TextView status;
    private Button access;
    private Button getEist;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (getActionBar() != null) getActionBar().hide();
        int pad = dp(24);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, dp(16), pad, pad);

        TextView title = text("Eist Controls", 26);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(title);
        root.addView(text("A home screen widget for the Eist reader: the book cover, play and pause, "
                + "and skip buttons. Tap the cover to jump to Eist's player.", 15));

        status = text("", 16);
        status.setTypeface(Typeface.DEFAULT_BOLD);
        status.setPadding(0, dp(20), 0, dp(8));
        root.addView(status);

        getEist = button("Get Eist on Google Play", v -> startActivity(new Intent(Intent.ACTION_VIEW,
                Uri.parse("https://play.google.com/store/apps/details?id=" + EistMedia.EIST))));
        root.addView(getEist);

        access = button("Turn on access", v -> openAccessSettings());
        root.addView(access);
        root.addView(text("Android only lets an app see what another app is playing through "
                + "\"notification access\". Eist Controls only looks at Eist's player. It has no "
                + "internet permission, so nothing it sees can leave your phone.\n\n"
                + "If Android says this is a \"restricted setting\", open App info below, tap the "
                + "⋮ menu, choose \"Allow restricted settings\", then try again.", 13));
        root.addView(button("Open App info", v -> startActivity(new Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName())))));

        TextView skipHeader = text("Skip buttons jump", 16);
        skipHeader.setTypeface(Typeface.DEFAULT_BOLD);
        skipHeader.setPadding(0, dp(24), 0, 0);
        root.addView(skipHeader);
        RadioGroup skip = new RadioGroup(this);
        skip.setOrientation(RadioGroup.HORIZONTAL);
        int current = EistMedia.skipSeconds(this);
        for (int s : EistMedia.SKIP_CHOICES) {
            RadioButton r = new RadioButton(this);
            r.setId(View.generateViewId());
            r.setText(s + " seconds");
            r.setChecked(s == current);
            r.setOnCheckedChangeListener((b, checked) -> {
                if (checked) {
                    EistMedia.setSkipSeconds(this, s);
                    EistWidget.updateAll(this);
                }
            });
            skip.addView(r);
        }
        root.addView(skip);

        Button add = button("Add widget to home screen", v -> AppWidgetManager.getInstance(this)
                .requestPinAppWidget(new ComponentName(this, EistWidget.class), null, null));
        ((LinearLayout.LayoutParams) add.getLayoutParams()).topMargin = dp(24);
        root.addView(add);
        root.addView(text("Or long-press your home screen, choose Widgets, and find Eist Controls.", 13));

        ScrollView scroll = new ScrollView(this);
        scroll.addView(root);
        // Keep content clear of the status and navigation bars (Android draws edge to edge).
        scroll.setOnApplyWindowInsetsListener((view, insets) -> {
            view.setPadding(0, insets.getSystemWindowInsetTop(), 0, insets.getSystemWindowInsetBottom());
            return insets;
        });
        setContentView(scroll);
    }

    @Override
    protected void onResume() {
        super.onResume();
        boolean installed = EistMedia.eistInstalled(this);
        boolean granted = EistMedia.hasAccess(this);
        getEist.setVisibility(installed ? View.GONE : View.VISIBLE);
        access.setVisibility(granted ? View.GONE : View.VISIBLE);
        if (!installed) status.setText("Eist isn't installed yet.");
        else if (!granted) status.setText("One step left: turn on notification access.");
        else status.setText("All set. Start a book in Eist and use the widget.");
        EistWidget.updateAll(this);
    }

    private void openAccessSettings() {
        Intent i;
        if (Build.VERSION.SDK_INT >= 30) {
            i = new Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                    .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                            EistMedia.listener(this).flattenToString());
        } else {
            i = new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS);
        }
        try {
            startActivity(i);
        } catch (RuntimeException e) {
            startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
        }
    }

    private TextView text(String s, int sp) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setPadding(0, dp(6), 0, dp(6));
        return t;
    }

    private Button button(String label, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setOnClickListener(l);
        b.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        return b;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
