package dev.eistcontrols.widget;

import android.app.NotificationManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.SystemClock;
import android.util.Log;

/** Finds Eist's media session and sends it commands. */
final class EistMedia {
    static final String TAG = "EistControls";
    static final String EIST = "io.eist.app";
    static final int[] SKIP_CHOICES = {10, 15, 30};

    private EistMedia() {}

    static ComponentName listener(Context c) {
        return new ComponentName(c, EistListener.class);
    }

    /** True once the user has turned on notification access, which Android requires to see other apps' media. */
    static boolean hasAccess(Context c) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        return nm != null && nm.isNotificationListenerAccessGranted(listener(c));
    }

    static boolean eistInstalled(Context c) {
        return c.getPackageManager().getLaunchIntentForPackage(EIST) != null;
    }

    /** Eist's current media session, or null if Eist isn't running or access is off. */
    static MediaController find(Context c) {
        if (!hasAccess(c)) return null;
        try {
            MediaSessionManager m = c.getSystemService(MediaSessionManager.class);
            for (MediaController mc : m.getActiveSessions(listener(c))) {
                if (EIST.equals(mc.getPackageName())) return mc;
            }
        } catch (SecurityException e) {
            Log.w(TAG, "No access to media sessions", e);
        }
        return null;
    }

    static boolean isPlaying(MediaController mc) {
        PlaybackState s = mc.getPlaybackState();
        if (s == null) return false;
        int st = s.getState();
        return st == PlaybackState.STATE_PLAYING || st == PlaybackState.STATE_BUFFERING
                || st == PlaybackState.STATE_CONNECTING;
    }

    static void playPause(MediaController mc) {
        if (isPlaying(mc)) mc.getTransportControls().pause();
        else mc.getTransportControls().play();
    }

    /** Jumps by the given number of seconds (negative goes back). */
    static void skip(MediaController mc, int seconds) {
        PlaybackState s = mc.getPlaybackState();
        if (s == null) return;
        if ((s.getActions() & PlaybackState.ACTION_SEEK_TO) == 0) {
            // Eist decides the jump size in this case.
            if (seconds < 0) mc.getTransportControls().rewind();
            else mc.getTransportControls().fastForward();
            return;
        }
        long target = Math.max(0, currentPosition(s) + seconds * 1000L);
        MediaMetadata md = mc.getMetadata();
        long duration = md == null ? 0 : md.getLong(MediaMetadata.METADATA_KEY_DURATION);
        if (duration > 0) target = Math.min(target, Math.max(0, duration - 1000));
        mc.getTransportControls().seekTo(target);
    }

    /** The playback position right now, allowing for time passed since Eist last reported it. */
    static long currentPosition(PlaybackState s) {
        long pos = s.getPosition();
        if (s.getState() == PlaybackState.STATE_PLAYING && s.getLastPositionUpdateTime() > 0) {
            long elapsed = SystemClock.elapsedRealtime() - s.getLastPositionUpdateTime();
            pos += (long) (elapsed * s.getPlaybackSpeed());
        }
        return pos;
    }

    static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("settings", Context.MODE_PRIVATE);
    }

    static int skipSeconds(Context c) {
        return prefs(c).getInt("skip_seconds", 10);
    }

    static void setSkipSeconds(Context c, int seconds) {
        prefs(c).edit().putInt("skip_seconds", seconds).apply();
    }
}
