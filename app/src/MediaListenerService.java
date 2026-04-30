package com.vrchat.osctracker;

import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.service.notification.NotificationListenerService;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

import java.util.List;

/**
 * NotificationListenerService subclass that reads active MediaSessions.
 *
 * Because MediaSessionManager.getActiveSessions() requires
 * NOTIFICATION_LISTENER permission, we piggyback on this service
 * which the user grants via Settings > Notifications > Notification access.
 *
 * Usage: call MediaListenerService.getNowPlaying(context) from anywhere
 * once the service is bound and the user has granted the permission.
 */
public class MediaListenerService extends NotificationListenerService {

    private static final String TAG = "MediaListenerService";

    // Singleton reference so TrackerService can query us
    private static MediaListenerService instance;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        Log.d(TAG, "MediaListenerService started");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        instance = null;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return super.onBind(intent);
    }

    /**
     * Returns a formatted "Artist - Title" string for the first active
     * media session that has metadata, or null if nothing is playing.
     */
    public static String getNowPlaying(Context context) {
        if (instance == null) {
            return null;
        }
        try {
            MediaSessionManager msm =
                (MediaSessionManager) context.getSystemService(Context.MEDIA_SESSION_SERVICE);
            if (msm == null) return null;

            List<MediaController> controllers =
                msm.getActiveSessions(
                    new android.content.ComponentName(context, MediaListenerService.class));

            for (MediaController controller : controllers) {
                MediaMetadata meta = controller.getMetadata();
                if (meta == null) continue;

                String title  = meta.getString(MediaMetadata.METADATA_KEY_TITLE);
                String artist = meta.getString(MediaMetadata.METADATA_KEY_ARTIST);
                if (artist == null || artist.isEmpty()) {
                    artist = meta.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST);
                }

                if (title != null && !title.isEmpty()) {
                    if (artist != null && !artist.isEmpty()) {
                        return artist + " - " + title;
                    } else {
                        return title;
                    }
                }
            }
        } catch (SecurityException e) {
            Log.e(TAG, "No notification listener permission: " + e.getMessage());
        } catch (Exception e) {
            Log.e(TAG, "Error reading media session: " + e.getMessage());
        }
        return null;
    }

    /** True if the service is alive (user has granted notification access). */
    public static boolean isAvailable() {
        return instance != null;
    }
}
