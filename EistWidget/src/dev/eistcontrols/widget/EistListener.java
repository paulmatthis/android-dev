package dev.eistcontrols.widget;

import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSession;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.os.Looper;
import android.service.notification.NotificationListenerService;
import android.util.Log;

import java.util.List;

/**
 * Android only shares other apps' media sessions with an enabled notification listener.
 * This service never reads notifications. It watches Eist's session and redraws the widget
 * whenever playback, the chapter, or the cover changes.
 */
public class EistListener extends NotificationListenerService
        implements MediaSessionManager.OnActiveSessionsChangedListener {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable redraw = () -> EistWidget.updateAll(this);
    private MediaSessionManager sessions;
    private MediaController current;

    private final MediaController.Callback callback = new MediaController.Callback() {
        @Override public void onPlaybackStateChanged(PlaybackState state) { scheduleRedraw(); }
        @Override public void onMetadataChanged(MediaMetadata metadata) { scheduleRedraw(); }
        @Override public void onSessionDestroyed() { watch(null); scheduleRedraw(); }
    };

    @Override
    public void onListenerConnected() {
        Log.i(EistMedia.TAG, "Listener connected");
        sessions = getSystemService(MediaSessionManager.class);
        try {
            sessions.addOnActiveSessionsChangedListener(this, EistMedia.listener(this), handler);
            onActiveSessionsChanged(sessions.getActiveSessions(EistMedia.listener(this)));
        } catch (SecurityException e) {
            Log.w(EistMedia.TAG, "No access to media sessions", e);
        }
    }

    @Override
    public void onListenerDisconnected() {
        Log.i(EistMedia.TAG, "Listener disconnected");
        if (sessions != null) sessions.removeOnActiveSessionsChangedListener(this);
        watch(null);
        scheduleRedraw();
    }

    @Override
    public void onActiveSessionsChanged(List<MediaController> controllers) {
        MediaController eist = null;
        if (controllers != null) {
            for (MediaController mc : controllers) {
                if (EistMedia.EIST.equals(mc.getPackageName())) { eist = mc; break; }
            }
        }
        watch(eist);
        scheduleRedraw();
    }

    private void watch(MediaController next) {
        MediaSession.Token oldToken = current == null ? null : current.getSessionToken();
        MediaSession.Token newToken = next == null ? null : next.getSessionToken();
        if (oldToken != null && oldToken.equals(newToken)) return;
        if (current != null) current.unregisterCallback(callback);
        current = next;
        if (current != null) current.registerCallback(callback, handler);
    }

    /** Eist can report several changes at once (chapter, cover, state); draw once they settle. */
    private void scheduleRedraw() {
        handler.removeCallbacks(redraw);
        handler.postDelayed(redraw, 150);
    }
}
