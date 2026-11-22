package dev.dinolite.widget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;
import java.util.concurrent.atomic.AtomicBoolean;

public class DinoliteWidget extends AppWidgetProvider {
    private static final String ACTION_TOGGLE = "dev.dinolite.widget.TOGGLE";
    private static final String PREFS = "dinolite";
    private static final String KEY_LAST = "last";
    private static final int COLOR_ON = 0xFFFFC94D;
    private static final int COLOR_OFF = 0xFF8A8D91;
    private static final AtomicBoolean busy = new AtomicBoolean();

    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        String last = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_LAST, null);
        LightClient.Result result = last == null ? null : LightClient.Result.valueOf(last);
        render(context, label(result), result == LightClient.Result.ON ? COLOR_ON : COLOR_OFF);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        if (ACTION_TOGGLE.equals(intent.getAction())) {
            toggle(context, goAsync());
        }
    }

    private void toggle(Context context, PendingResult pending) {
        if (!busy.compareAndSet(false, true)) {
            pending.finish();
            return;
        }
        render(context, "...", COLOR_OFF);
        LightClient.toggle(context, result -> {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_LAST, result.name()).apply();
            render(context, label(result), result == LightClient.Result.ON ? COLOR_ON : COLOR_OFF);
            busy.set(false);
            pending.finish();
        });
    }

    private static String label(LightClient.Result result) {
        if (result == null) return "Dinolite";
        switch (result) {
            case ON: return "On";
            case OFF: return "Off";
            case BLUETOOTH_OFF: return "BT off";
            case NO_PERMISSION: return "Open app";
            default: return "No light";
        }
    }

    private static void render(Context context, String text, int color) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget);
        views.setTextViewText(R.id.label, text);
        views.setInt(R.id.icon, "setColorFilter", color);
        Intent intent = new Intent(context, DinoliteWidget.class).setAction(ACTION_TOGGLE);
        views.setOnClickPendingIntent(R.id.root, PendingIntent.getBroadcast(
                context, 0, intent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT));
        AppWidgetManager.getInstance(context).updateAppWidget(new ComponentName(context, DinoliteWidget.class), views);
    }
}
