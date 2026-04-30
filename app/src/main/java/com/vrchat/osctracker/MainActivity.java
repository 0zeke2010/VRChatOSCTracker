package com.vrchat.osctracker;

import android.app.AppOpsManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceManager;

/**
 * Main UI – lets the user configure the OSC target and start/stop the service.
 */
public class MainActivity extends AppCompatActivity {

    private EditText  etHost, etPort, etInterval;
    private Switch    swShowTime, swShowMedia;
    private Button    btnStartStop;
    private TextView  tvStatus, tvPermUsage, tvPermMedia;

    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = PreferenceManager.getDefaultSharedPreferences(this);

        etHost      = findViewById(R.id.et_host);
        etPort      = findViewById(R.id.et_port);
        etInterval  = findViewById(R.id.et_interval);
        swShowTime  = findViewById(R.id.sw_show_time);
        swShowMedia = findViewById(R.id.sw_show_media);
        btnStartStop = findViewById(R.id.btn_start_stop);
        tvStatus    = findViewById(R.id.tv_status);
        tvPermUsage = findViewById(R.id.tv_perm_usage);
        tvPermMedia = findViewById(R.id.tv_perm_media);

        loadPrefs();

        btnStartStop.setOnClickListener(v -> toggleService());

        findViewById(R.id.btn_grant_usage).setOnClickListener(v ->
            startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)));

        findViewById(R.id.btn_grant_media).setOnClickListener(v ->
            startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)));

        // Save prefs on text changes (simple approach)
        findViewById(R.id.btn_save).setOnClickListener(v -> savePrefs());
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshPermissionStatus();
        refreshServiceStatus();
    }

    private void loadPrefs() {
        etHost.setText(prefs.getString("osc_host", "192.168.5.220"));
        etPort.setText(prefs.getString("osc_port", "9000"));
        etInterval.setText(prefs.getString("interval", "5"));
        swShowTime.setChecked(prefs.getBoolean("show_time", true));
        swShowMedia.setChecked(prefs.getBoolean("show_media", true));
    }

    private void savePrefs() {
        String host     = etHost.getText().toString().trim();
        String port     = etPort.getText().toString().trim();
        String interval = etInterval.getText().toString().trim();

        if (host.isEmpty() || port.isEmpty() || interval.isEmpty()) {
            Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show();
            return;
        }

        prefs.edit()
            .putString("osc_host",  host)
            .putString("osc_port",  port)
            .putString("interval",  interval)
            .putBoolean("show_time",  swShowTime.isChecked())
            .putBoolean("show_media", swShowMedia.isChecked())
            .apply();

        Toast.makeText(this, "Settings saved", Toast.LENGTH_SHORT).show();
    }

    private void toggleService() {
        if (TrackerService.isRunning) {
            Intent stop = new Intent(this, TrackerService.class);
            stop.setAction(TrackerService.ACTION_STOP);
            startService(stop);
        } else {
            if (!hasUsageStatsPermission()) {
                new AlertDialog.Builder(this)
                    .setTitle("Permission required")
                    .setMessage("Usage access permission is required to track the foreground app. Please grant it in Settings.")
                    .setPositiveButton("Open Settings", (d, w) ->
                        startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)))
                    .setNegativeButton("Cancel", null)
                    .show();
                return;
            }
            savePrefs();
            Intent start = new Intent(this, TrackerService.class);
            start.setAction(TrackerService.ACTION_START);
            startForegroundService(start);
        }
        // Slight delay so isRunning has time to update
        btnStartStop.postDelayed(this::refreshServiceStatus, 500);
    }

    private void refreshServiceStatus() {
        if (TrackerService.isRunning) {
            btnStartStop.setText("Stop tracker");
            tvStatus.setText("Status: Running");
            tvStatus.setTextColor(getColor(android.R.color.holo_green_dark));
        } else {
            btnStartStop.setText("Start tracker");
            tvStatus.setText("Status: Stopped");
            tvStatus.setTextColor(getColor(android.R.color.darker_gray));
        }
    }

    private void refreshPermissionStatus() {
        boolean usagePerm = hasUsageStatsPermission();
        boolean mediaPerm = isNotificationListenerEnabled();

        tvPermUsage.setText(usagePerm ? "✓ Usage access granted" : "✗ Usage access required");
        tvPermUsage.setTextColor(getColor(usagePerm
            ? android.R.color.holo_green_dark
            : android.R.color.holo_red_dark));

        tvPermMedia.setText(mediaPerm ? "✓ Notification listener granted" : "✗ Notification listener (optional)");
        tvPermMedia.setTextColor(getColor(mediaPerm
            ? android.R.color.holo_green_dark
            : android.R.color.darker_gray));
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

    private boolean isNotificationListenerEnabled() {
        String flat = Settings.Secure.getString(
            getContentResolver(), "enabled_notification_listeners");
        if (TextUtils.isEmpty(flat)) return false;
        return flat.contains(getPackageName());
    }
}
