package com.vrchat.osctracker;

import android.app.AppOpsManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceManager;

public class MainActivity extends AppCompatActivity {

    private TextView tvStatus, tvPermUsage, tvPermMedia;
    private Button   btnStartStop;
    private Switch   swShowTime, swShowMedia;

    private android.widget.EditText etHost, etPort, etInterval;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        etHost       = findViewById(R.id.et_host);
        etPort       = findViewById(R.id.et_port);
        etInterval   = findViewById(R.id.et_interval);
        swShowTime   = findViewById(R.id.sw_show_time);
        swShowMedia  = findViewById(R.id.sw_show_media);
        btnStartStop = findViewById(R.id.btn_start_stop);
        tvStatus     = findViewById(R.id.tv_status);
        tvPermUsage  = findViewById(R.id.tv_perm_usage);
        tvPermMedia  = findViewById(R.id.tv_perm_media);

        loadPrefs();

        findViewById(R.id.btn_save).setOnClickListener(v -> savePrefs());

        btnStartStop.setOnClickListener(v -> {
            if (TrackerService.isRunning) {
                // Stop the service
                Intent stop = new Intent(this, TrackerService.class);
                stop.setAction(TrackerService.ACTION_STOP);
                startService(stop);
                TrackerService.isRunning = false;
                updateServiceUI(false);
            } else {
                // Save first then start
                savePrefs();
                Intent start = new Intent(this, TrackerService.class);
                start.setAction(TrackerService.ACTION_START);
                startForegroundService(start);
                TrackerService.isRunning = true;
                updateServiceUI(true);
            }
        });

        findViewById(R.id.btn_grant_usage).setOnClickListener(v ->
            startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)));

        findViewById(R.id.btn_grant_media).setOnClickListener(v ->
            startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)));
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshPermissionStatus();
        updateServiceUI(TrackerService.isRunning);
    }

    private void loadPrefs() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        etHost.setText(prefs.getString("osc_host", "192.168.5.220"));
        etPort.setText(prefs.getString("osc_port", "9000"));
        etInterval.setText(prefs.getString("interval", "5"));
        swShowTime.setChecked(prefs.getBoolean("show_time", true));
        swShowMedia.setChecked(prefs.getBoolean("show_media", true));
    }

    private void savePrefs() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        prefs.edit()
            .putString("osc_host", etHost.getText().toString().trim())
            .putString("osc_port", etPort.getText().toString().trim())
            .putString("interval", etInterval.getText().toString().trim())
            .putBoolean("show_time",  swShowTime.isChecked())
            .putBoolean("show_media", swShowMedia.isChecked())
            .apply();
        Toast.makeText(this, "Settings saved", Toast.LENGTH_SHORT).show();
    }

    private void updateServiceUI(boolean running) {
        if (running) {
            btnStartStop.setText("Stop tracker");
            tvStatus.setText("Status: Running");
            tvStatus.setTextColor(0xFF388E3C);
        } else {
            btnStartStop.setText("Start tracker");
            tvStatus.setText("Status: Stopped");
            tvStatus.setTextColor(0xFF888888);
        }
    }

    private void refreshPermissionStatus() {
        boolean usagePerm = hasUsageStatsPermission();
        boolean mediaPerm = isNotificationListenerEnabled();

        tvPermUsage.setText(usagePerm ? "✓ Usage access granted" : "✗ Usage access required");
        tvPermUsage.setTextColor(usagePerm ? 0xFF388E3C : 0xFFD32F2F);

        tvPermMedia.setText(mediaPerm ? "✓ Notification listener granted" : "✗ Notification listener (optional)");
        tvPermMedia.setTextColor(mediaPerm ? 0xFF388E3C : 0xFF888888);
    }

    private boolean hasUsageStatsPermission() {
        try {
            AppOpsManager aom = (AppOpsManager) getSystemService(Context.APP_OPS_SERVICE);
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

    private boolean isNotificationListenerEnabled() {
        try {
            String flat = Settings.Secure.getString(
                getContentResolver(), "enabled_notification_listeners");
            return !TextUtils.isEmpty(flat) && flat.contains(getPackageName());
        } catch (Exception e) {
            return false;
        }
    }
}
