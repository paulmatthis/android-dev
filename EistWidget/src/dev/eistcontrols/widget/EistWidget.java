package dev.eistcontrols.widget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.net.Uri;
import android.util.Log;
import android.widget.RemoteViews;

/** The 3x1 home screen widget: cover, skip back, play/pause, skip forward. */
public class EistWidget extends AppWidgetProvider {
    private static final String PLAY_PAUSE = "dev.eistcontrols.widget.PLAY_PAUSE";
    private static final String BACK = "dev.eistcontrols.widget.BACK";
    private static final String FORWARD = "dev.eistcontrols.widget.FORWARD";
    private static final int COVER_MAX_PX = 320;
    private static final int DIMMED = 90; // of 255

    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        updateAll(context);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        String action = intent.getAction();
        if (!PLAY_PAUSE.equals(action) && !BACK.equals(action) && !FORWARD.equals(action)) return;
        MediaController mc = EistMedia.find(context);
        if (mc == null) {
            Log.i(EistMedia.TAG, "Tap ignored: Eist has no active session");
        } else if (PLAY_PAUSE.equals(action)) {
            EistMedia.playPause(mc);
        } else {
            int s = EistMedia.skipSeconds(context);
            EistMedia.skip(mc, BACK.equals(action) ? -s : s);
        }
        updateAll(context);
    }

    static void updateAll(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        int[] ids = manager.getAppWidgetIds(new ComponentName(context, EistWidget.class));
        if (ids.length == 0) return;
        try {
            manager.updateAppWidget(ids, build(context));
        } catch (RuntimeException e) {
            Log.w(EistMedia.TAG, "Widget update failed", e);
        }
    }

    private static RemoteViews build(Context c) {
        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.widget);
        String skip = String.valueOf(EistMedia.skipSeconds(c));
        v.setTextViewText(R.id.back_label, skip);
        v.setTextViewText(R.id.forward_label, skip);
        v.setContentDescription(R.id.back, "Back " + skip + " seconds");
        v.setContentDescription(R.id.forward, "Forward " + skip + " seconds");

        boolean installed = EistMedia.eistInstalled(c);
        boolean access = EistMedia.hasAccess(c);
        MediaController mc = access ? EistMedia.find(c) : null;

        // Cover: the book's art from Eist, else Eist's own icon.
        Bitmap cover = null;
        String title = "Eist";
        String subtitle;
        if (mc != null) {
            MediaMetadata md = mc.getMetadata();
            if (md != null) {
                cover = firstBitmap(md, MediaMetadata.METADATA_KEY_ART,
                        MediaMetadata.METADATA_KEY_ALBUM_ART, MediaMetadata.METADATA_KEY_DISPLAY_ICON);
                String book = md.getString(MediaMetadata.METADATA_KEY_ALBUM);
                String chapter = md.getString(MediaMetadata.METADATA_KEY_TITLE);
                if (book != null && !book.isEmpty()) {
                    title = book;
                    subtitle = chapter != null ? chapter : "";
                } else {
                    title = chapter != null ? chapter : "Eist";
                    String author = md.getString(MediaMetadata.METADATA_KEY_ARTIST);
                    subtitle = author != null ? author : "";
                }
            } else {
                subtitle = "Ready";
            }
        } else if (!installed) {
            subtitle = "Tap to get Eist";
        } else if (!access) {
            subtitle = "Tap to finish setup";
        } else {
            subtitle = "Not playing: tap to open";
        }
        if (cover == null && installed) cover = eistIcon(c);
        if (cover != null) v.setImageViewBitmap(R.id.cover, scale(cover));
        v.setTextViewText(R.id.title, title);
        v.setTextViewText(R.id.subtitle, subtitle);

        boolean playing = mc != null && EistMedia.isPlaying(mc);
        v.setImageViewResource(R.id.play, playing ? R.drawable.ic_pause : R.drawable.ic_play);
        v.setContentDescription(R.id.play, playing ? "Pause" : "Play");

        // What a tap does depends on whether there is something to control.
        PendingIntent open = openIntent(c, mc, installed, access);
        v.setOnClickPendingIntent(R.id.cover, open);
        if (mc != null) {
            v.setOnClickPendingIntent(R.id.play, broadcast(c, PLAY_PAUSE, 1));
            v.setOnClickPendingIntent(R.id.back, broadcast(c, BACK, 2));
            v.setOnClickPendingIntent(R.id.forward, broadcast(c, FORWARD, 3));
        } else {
            v.setOnClickPendingIntent(R.id.play, open);
            v.setOnClickPendingIntent(R.id.back, open);
            v.setOnClickPendingIntent(R.id.forward, open);
        }
        int alpha = mc != null ? 255 : DIMMED;
        int label = (alpha << 24) | 0xE5E7EB;
        v.setInt(R.id.back_icon, "setImageAlpha", alpha);
        v.setInt(R.id.forward_icon, "setImageAlpha", alpha);
        v.setTextColor(R.id.back_label, label);
        v.setTextColor(R.id.forward_label, label);
        return v;
    }

    /** Tapping the cover goes to Eist's player, the same place its notification goes. */
    private static PendingIntent openIntent(Context c, MediaController mc, boolean installed, boolean access) {
        int flags = PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT;
        if (!installed) {
            Intent store = new Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://play.google.com/store/apps/details?id=" + EistMedia.EIST));
            return PendingIntent.getActivity(c, 10, store, flags);
        }
        if (!access) {
            return PendingIntent.getActivity(c, 11, new Intent(c, SetupActivity.class), flags);
        }
        if (mc != null && mc.getSessionActivity() != null) return mc.getSessionActivity();
        Intent launch = c.getPackageManager().getLaunchIntentForPackage(EistMedia.EIST);
        return PendingIntent.getActivity(c, 12, launch, flags);
    }

    private static PendingIntent broadcast(Context c, String action, int code) {
        Intent i = new Intent(c, EistWidget.class).setAction(action);
        return PendingIntent.getBroadcast(c, code, i, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private static Bitmap firstBitmap(MediaMetadata md, String... keys) {
        for (String k : keys) {
            Bitmap b = md.getBitmap(k);
            if (b != null) return b;
        }
        return null;
    }

    private static Bitmap eistIcon(Context c) {
        try {
            Drawable d = c.getPackageManager().getApplicationIcon(EistMedia.EIST);
            Bitmap b = Bitmap.createBitmap(COVER_MAX_PX / 2, COVER_MAX_PX / 2, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(b);
            d.setBounds(0, 0, b.getWidth(), b.getHeight());
            d.draw(canvas);
            return b;
        } catch (Exception e) {
            return null;
        }
    }

    /** Keeps the bitmap small; widget updates travel between processes. */
    private static Bitmap scale(Bitmap b) {
        int w = b.getWidth(), h = b.getHeight();
        int longest = Math.max(w, h);
        if (longest <= COVER_MAX_PX) return b;
        float f = (float) COVER_MAX_PX / longest;
        return Bitmap.createScaledBitmap(b, Math.max(1, Math.round(w * f)), Math.max(1, Math.round(h * f)), true);
    }
}
