package dev.dinolite.widget;

import android.Manifest;
import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Asks for Bluetooth access once, and offers a test toggle and an add-widget shortcut. */
public class SetupActivity extends Activity {
    private TextView status;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setPadding(64, 64, 64, 64);

        status = new TextView(this);
        status.setGravity(Gravity.CENTER);
        status.setTextSize(18);
        root.addView(status);

        Button test = new Button(this);
        test.setText("Toggle Dinolite");
        test.setOnClickListener(v -> toggle());
        root.addView(test);

        Button add = new Button(this);
        add.setText("Add widget to home screen");
        add.setOnClickListener(v -> AppWidgetManager.getInstance(this)
                .requestPinAppWidget(new ComponentName(this, DinoliteWidget.class), null, null));
        root.addView(add);

        setContentView(root);
        if (hasPermission()) {
            status.setText("Ready");
        } else {
            status.setText("Bluetooth access is needed to reach the light.");
            requestPermissions(new String[] {Manifest.permission.BLUETOOTH_CONNECT}, 1);
        }
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] permissions, int[] results) {
        status.setText(hasPermission() ? "Ready" : "Bluetooth access was denied.");
    }

    private boolean hasPermission() {
        return checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
    }

    private void toggle() {
        status.setText("Working...");
        LightClient.toggle(this, result -> status.setText("Result: " + result));
    }
}
