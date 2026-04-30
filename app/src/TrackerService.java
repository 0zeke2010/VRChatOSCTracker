package com.vrchat.osctracker;

import android.app.AppOpsManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.preference.PreferenceManager;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * Persistent foreground service that:
 *   1. Reads the foreground app via UsageStatsManager.
 *   2. Optionally reads now-playing media via MediaListenerService.
 *   3. Formats a message and sends it over OSC UDP to VRChat.
 *
 * Polling interval and target IP/port are read from SharedPreferences
 * so the user can change them in MainActivity without restarting.
 */
public class TrackerService extends Service {

    public static final String ACTION_START = "com.vrchat.osctracker.START";
    public static final String ACTION_STOP  = "com.vrchat.osctracker.STOP";

    private static final String TAG          = "TrackerService";
    private static final String CHANNEL_ID   = "osc_tracker_channel";
    private static final int    NOTIF_ID     = 1;

    private Handler  handler;
    private Runnable pollRunnable;
    private boolean  running = false;

    // Exposed so MainActivity can check
    public static boolean isRunning = false;

    @Override
    public void onCreate() {
        super.onCreate();
        handler = new Handler(Looper.getMainLooper());
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) return START_NOT_STICKY;

        if (ACTION_STOP.equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }

        if (!running) {
            startForeground(NOTIF_ID, buildNotification("Starting…"));
            running    = true;
            isRunning  = true;
            startPolling();
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        running   = false;
        isRunning = false;
        if (handler != null && pollRunnable != null) {
            handler.removeCallbacks(pollRunnable);
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }

    // -------------------------------------------------------------------------
    // Polling logic
    // -------------------------------------------------------------------------

    private void startPolling() {
        pollRunnable = new Runnable() {
            @Override
            public void run() {
                if (!running) return;

                SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(TrackerService.this);
                int    intervalSec = Integer.parseInt(prefs.getString("interval", "5"));
                String host        = prefs.getString("osc_host", "192.168.5.220");
                int    port        = Integer.parseInt(prefs.getString("osc_port", "9000"));
                boolean showTime   = prefs.getBoolean("show_time", true);
                boolean showMedia  = prefs.getBoolean("show_media", true);

                // Build message on a background thread to avoid StrictMode violations
                new Thread(() -> {
                    String message = buildMessage(showTime, showMedia);
                    if (message != null) {
                        OSCSender sender = new OSCSender(host, port);
                        boolean ok = sender.sendChatbox(message, true);
                        Log.d(TAG, "Sent: \"" + message + "\" -> " + (ok ? "OK" : "FAIL"));
                        updateNotification(message);
                    }
                }).start();

                handler.postDelayed(this, intervalSec * 1000L);
            }
        };
        handler.post(pollRunnable);
    }

    private String buildMessage(boolean showTime, boolean showMedia) {
        StringBuilder sb = new StringBuilder();

        // --- Foreground app name ---
        String appName = getForegroundAppName();
        if (appName != null) {
            sb.append("\uD83D\uDCF1 ").append(appName);  // 📱
        }

        // --- Now playing media ---
        if (showMedia) {
            String media = MediaListenerService.getNowPlaying(this);
            if (media != null && !media.isEmpty()) {
                if (sb.length() > 0) sb.append("  |  ");
                sb.append("\uD83C\uDFB5 ").append(media);  // 🎵
            }
        }

        // --- Timestamp ---
        if (showTime) {
            String time = new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date());
            if (sb.length() > 0) sb.append("  ");
            sb.append("[").append(time).append("]");
        }

        String result = sb.toString().trim();
        return result.isEmpty() ? null : result;
    }

    // -------------------------------------------------------------------------
    // UsageStats – get current foreground app
    // -------------------------------------------------------------------------

    private String getForegroundAppName() {
        if (!hasUsageStatsPermission()) {
            return null;
        }
        try {
            UsageStatsManager usm =
                (UsageStatsManager) getSystemService(Context.USAGE_STATS_SERVICE);
            if (usm == null) return null;

            long now   = System.currentTimeMillis();
            long begin = now - 5000; // look back 5 s

            List<UsageStats> stats =
                usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, begin, now);

            if (stats == null || stats.isEmpty()) return null;

            SortedMap<Long, UsageStats> sortedMap = new TreeMap<>();
            for (UsageStats s : stats) {
                sortedMap.put(s.getLastTimeUsed(), s);
            }

            if (sortedMap.isEmpty()) return null;
            UsageStats topStats = sortedMap.get(sortedMap.lastKey());
            if (topStats == null) return null;

            String pkg = topStats.getPackageName();

            // Skip system UI and ourselves
            if (pkg.equals(getPackageName()))                return null;
            if (pkg.equals("com.android.systemui"))          return null;
            if (pkg.equals("com.android.launcher3"))         return null;

            // Resolve to a human-readable label
            try {
                android.content.pm.PackageManager pm = getPackageManager();
                android.content.pm.ApplicationInfo ai =
                    pm.getApplicationInfo(pkg, 0);
                return (String) pm.getApplicationLabel(ai);
            } catch (android.content.pm.PackageManager.NameNotFoundException e) {
                return pkg; // Fall back to package name
            }

        } catch (Exception e) {
            Log.e(TAG, "UsageStats error: " + e.getMessage());
            return null;
        }
    }

    private boolean hasUsageStatsPermission() {
        AppOpsManager aom = (AppOpsManager) getSystemService(Context.APP_OPS_SERVICE);
        if (aom == null) return false;
        int mode = aom.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            getPackageName());
        return mode == AppOpsManager.MODE_ALLOWED;
    }

    // -------------------------------------------------------------------------
    // Notification helpers
    // -------------------------------------------------------------------------

    private void createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(
            CHANNEL_ID,
            "OSC Tracker",
            NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("Shows currently tracked app/media info");
        NotificationManager nm =
            (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.createNotificationChannel(channel);
    }

    private Notification buildNotification(String content) {
        Intent stopIntent = new Intent(this, TrackerService.class);
        stopIntent.setAction(ACTION_STOP);
        PendingIntent stopPi = PendingIntent.getService(
            this, 0, stopIntent, PendingIntent.FLAG_IMMUTABLE);

        Intent openIntent = new Intent(this, MainActivity.class);
        PendingIntent openPi = PendingIntent.getActivity(
            this, 0, openIntent, PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("VRChat OSC Tracker")
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentIntent(openPi)
            .addAction(android.R.drawable.ic_media_pause, "Stop", stopPi)
            .setOngoing(true)
            .build();
    }

    private void updateNotification(String content) {
        NotificationManager nm =
            (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) {
            nm.notify(NOTIF_ID, buildNotification(content));
        }
    }
}
