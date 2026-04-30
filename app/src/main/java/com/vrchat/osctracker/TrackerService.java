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

public class TrackerService extends Service {

    public static final String ACTION_START = "com.vrchat.osctracker.START";
    public static final String ACTION_STOP  = "com.vrchat.osctracker.STOP";

    private static final String TAG        = "TrackerService";
    private static final String CHANNEL_ID = "osc_tracker_channel";
    private static final int    NOTIF_ID   = 1;

    private Handler  handler;
    private Runnable pollRunnable;
    private boolean  running = false;

    public static boolean isRunning = false;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        handler = new Handler(Looper.getMainLooper());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) {
            stopSelf();
            return START_NOT_STICKY;
        }

        if (ACTION_STOP.equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }

        try {
            startForeground(NOTIF_ID, buildNotification("Tracker running..."));
        } catch (Exception e) {
            Log.e(TAG, "startForeground failed: " + e.getMessage());
            stopSelf();
            return START_NOT_STICKY;
        }

        if (!running) {
            running   = true;
            isRunning = true;
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

    private void startPolling() {
        pollRunnable = new Runnable() {
            @Override
            public void run() {
                if (!running) return;

                SharedPreferences prefs =
                    PreferenceManager.getDefaultSharedPreferences(TrackerService.this);

                int    intervalSec = 5;
                try {
                    intervalSec = Integer.parseInt(prefs.getString("interval", "5"));
                } catch (Exception ignored) {}

                String  host       = prefs.getString("osc_host", "192.168.5.220");
                int     port       = 9000;
                try {
                    port = Integer.parseInt(prefs.getString("osc_port", "9000"));
                } catch (Exception ignored) {}

                boolean showTime  = prefs.getBoolean("show_time", true);
                boolean showMedia = prefs.getBoolean("show_media", true);

                final int finalPort       = port;
                final int finalIntervalSec = intervalSec;

                new Thread(() -> {
                    try {
                        String message = buildMessage(showTime, showMedia);
                        if (message != null && !message.isEmpty()) {
                            OSCSender sender = new OSCSender(host, finalPort);
                            sender.sendChatbox(message, true);
                            updateNotification(message);
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Poll error: " + e.getMessage());
                    }
                    handler.postDelayed(pollRunnable, finalIntervalSec * 1000L);
                }).start();
            }
        };
        handler.post(pollRunnable);
    }

    private String buildMessage(boolean showTime, boolean showMedia) {
        StringBuilder sb = new StringBuilder();

        try {
            String appName = getForegroundAppName();
            if (appName != null && !appName.isEmpty()) {
                sb.append("\uD83D\uDCF1 ").append(appName);
            }
        } catch (Exception e) {
            Log.e(TAG, "App name error: " + e.getMessage());
        }

        if (showMedia) {
            try {
                String media = MediaListenerService.getNowPlaying(this);
                if (media != null && !media.isEmpty()) {
                    if (sb.length() > 0) sb.append("  |  ");
                    sb.append("\uD83C\uDFB5 ").append(media);
                }
            } catch (Exception e) {
                Log.e(TAG, "Media error: " + e.getMessage());
            }
        }

        if (showTime) {
            String time = new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date());
            if (sb.length() > 0) sb.append("  ");
            sb.append("[").append(time).append("]");
        }

        return sb.toString().trim();
    }

    private String getForegroundAppName() {
        if (!hasUsageStatsPermission()) return null;
        try {
            UsageStatsManager usm =
                (UsageStatsManager) getSystemService(Context.USAGE_STATS_SERVICE);
            if (usm == null) return null;

            long now   = System.currentTimeMillis();
            long begin = now - 10000;

            List<UsageStats> stats =
                usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, begin, now);

            if (stats == null || stats.isEmpty()) return null;

            SortedMap<Long, UsageStats> sorted = new TreeMap<>();
            for (UsageStats s : stats) {
                sorted.put(s.getLastTimeUsed(), s);
            }
            if (sorted.isEmpty()) return null;

            UsageStats top = sorted.get(sorted.lastKey());
            if (top == null) return null;

            String pkg = top.getPackageName();
            if (pkg.equals(getPackageName()))       return null;
            if (pkg.equals("com.android.systemui")) return null;

            try {
                android.content.pm.ApplicationInfo ai =
                    getPackageManager().getApplicationInfo(pkg, 0);
                return (String) getPackageManager().getApplicationLabel(ai);
            } catch (Exception e) {
                return pkg;
            }
        } catch (Exception e) {
            Log.e(TAG, "UsageStats error: " + e.getMessage());
            return null;
        }
    }

    private boolean hasUsageStatsPermission() {
        try {
            AppOpsManager aom =
                (AppOpsManager) getSystemService(Context.APP_OPS_SERVICE);
            if (aom == null) return false;
            int mode = aom.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                getPackageName());
            return mode == AppOpsManager.MODE_ALLOWED;
        } catch (Exception e) {
            return false;
        }
    }

    private void createNotificationChannel() {
        try {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID, "OSC Tracker", NotificationManager.IMPORTANCE_LOW);
            NotificationManager nm =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) nm.createNotificationChannel(channel);
        } catch (Exception e) {
            Log.e(TAG, "Channel error: " + e.getMessage());
        }
    }

    private Notification buildNotification(String content) {
        Intent openIntent = new Intent(this, MainActivity.class);
        PendingIntent openPi = PendingIntent.getActivity(
            this, 0, openIntent, PendingIntent.FLAG_IMMUTABLE);

        Intent stopIntent = new Intent(this, TrackerService.class);
        stopIntent.setAction(ACTION_STOP);
        PendingIntent stopPi = PendingIntent.getService(
            this, 0, stopIntent, PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("VRChat OSC Tracker")
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(openPi)
            .addAction(android.R.drawable.ic_media_pause, "Stop", stopPi)
            .setOngoing(true)
            .build();
    }

    private void updateNotification(String content) {
        try {
            NotificationManager nm =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) nm.notify(NOTIF_ID, buildNotification(content));
        } catch (Exception e) {
            Log.e(TAG, "Notification update error: " + e.getMessage());
        }
    }
}
