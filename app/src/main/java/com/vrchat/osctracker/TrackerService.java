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
import android.os.PowerManager;
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

    private Handler              handler;
    private Runnable             pollRunnable;
    private boolean              running = false;
    private PowerManager.WakeLock wakeLock;

    public static boolean isRunning = false;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        handler = new Handler(Looper.getMainLooper());

        // Wake lock keeps the CPU running so we keep sending in background
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        wakeLock = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK, "VRChatOSCTracker::WakeLock");
        wakeLock.acquire();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
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

        // START_STICKY means Android restarts the service if it gets killed
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        running   = false;
        isRunning = false;
        if (handler != null && pollRunnable != null) {
            handler.removeCall
