package com.edgevolume.app;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {

    public static final String PREFS_NAME = "EdgeVolumePrefs";
    public static final String KEY_RUNNING = "service_running";
    public static final String KEY_LEFT_ENABLED = "left_enabled";
    public static final String KEY_RIGHT_ENABLED = "right_enabled";
    public static final String KEY_VISIBLE_STRIP = "visible_strip";
    public static final String KEY_EDGE_WIDTH_DP = "edge_width_dp";
    public static final String KEY_EDGE_HEIGHT_PCT = "edge_height_pct";
    public static final String KEY_SENSITIVITY_DP = "sensitivity_dp";

    private SharedPreferences prefs;
    private TextView statusText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        ScrollView scrollView = new ScrollView(this);
        scrollView.setBackgroundColor(Color.parseColor("#F5F7FA"));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dpToPx(20);
        root.setPadding(pad, pad, pad, pad);
        scrollView.addView(root);

        TextView title = new TextView(this);
        title.setText("Edge Volume Control");
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24);
        title.setTextColor(Color.parseColor("#1A202C"));
        root.addView(title);

        statusText = new TextView(this);
        statusText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        statusText.setPadding(0, dpToPx(6), 0, dpToPx(14));
        root.addView(statusText);

        // Quick Backup Volume Buttons
        LinearLayout quickRow = new LinearLayout(this);
        quickRow.setOrientation(LinearLayout.HORIZONTAL);
        quickRow.setPadding(0, 0, 0, dpToPx(14));

        Button volDownBtn = createButton("Vol -", "#3182CE");
        volDownBtn.setOnClickListener(v -> adjustVolume(AudioManager.ADJUST_LOWER));

        Button volUpBtn = createButton("Vol +", "#3182CE");
        volUpBtn.setOnClickListener(v -> adjustVolume(AudioManager.ADJUST_RAISE));

        Button showPanelBtn = createButton("Volume Panel", "#2B6CB0");
        showPanelBtn.setOnClickListener(v -> adjustVolume(AudioManager.ADJUST_SAME));

        LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        btnLp.setMargins(dpToPx(4), 0, dpToPx(4), 0);
        quickRow.addView(volDownBtn, btnLp);
        quickRow.addView(volUpBtn, btnLp);
        quickRow.addView(showPanelBtn, btnLp);
        root.addView(quickRow);

        // Button 1: Start Service & Grant Overlay
        Button startBtn = createButton("1. Start Edge Swipe (Auto-Start Ready)", "#2F855A");
        startBtn.setOnClickListener(v -> startEdgeService());
        root.addView(startBtn, createMarginParams(0, dpToPx(8)));

        // Button 2: Battery Exemption for 24/7 Background Active
        Button batteryBtn = createButton("2. Keep Active in Background (Battery Free)", "#D69E2E");
        batteryBtn.setOnClickListener(v -> requestBatteryExemption());
        root.addView(batteryBtn, createMarginParams(0, dpToPx(8)));

        // Button 3: Turn Off Notification Permanently
        Button hideNotifBtn = createButton("3. Hide Notification Forever (Turn Off)", "#4A5568");
        hideNotifBtn.setOnClickListener(v -> openNotificationSettings());
        root.addView(hideNotifBtn, createMarginParams(0, dpToPx(8)));

        // Stop Service Button
        Button stopBtn = createButton("Stop Edge Swipe Service", "#C53030");
        stopBtn.setOnClickListener(v -> stopEdgeService());
        root.addView(stopBtn, createMarginParams(0, dpToPx(16)));

        // Toggles
        Switch leftSwitch = new Switch(this);
        leftSwitch.setText("Enable Left Edge Swipe");
        leftSwitch.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        leftSwitch.setChecked(prefs.getBoolean(KEY_LEFT_ENABLED, true));
        leftSwitch.setOnCheckedChangeListener((b, checked) -> {
            prefs.edit().putBoolean(KEY_LEFT_ENABLED, checked).apply();
            restartIfRunning();
        });
        root.addView(leftSwitch, createMarginParams(0, dpToPx(12)));

        Switch rightSwitch = new Switch(this);
        rightSwitch.setText("Enable Right Edge Swipe");
        rightSwitch.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        rightSwitch.setChecked(prefs.getBoolean(KEY_RIGHT_ENABLED, true));
        rightSwitch.setOnCheckedChangeListener((b, checked) -> {
            prefs.edit().putBoolean(KEY_RIGHT_ENABLED, checked).apply();
            restartIfRunning();
        });
        root.addView(rightSwitch, createMarginParams(0, dpToPx(12)));

        Switch visibleSwitch = new Switch(this);
        visibleSwitch.setText("Show Visible Edge Handle (Off = 100% Invisible)");
        visibleSwitch.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        visibleSwitch.setChecked(prefs.getBoolean(KEY_VISIBLE_STRIP, false));
        visibleSwitch.setOnCheckedChangeListener((b, checked) -> {
            prefs.edit().putBoolean(KEY_VISIBLE_STRIP, checked).apply();
            restartIfRunning();
        });
        root.addView(visibleSwitch, createMarginParams(0, dpToPx(16)));

        // Edge Width SeekBar
        int currentWidth = prefs.getInt(KEY_EDGE_WIDTH_DP, 22);
        TextView widthLabel = new TextView(this);
        widthLabel.setText("Edge Touch Width: " + currentWidth + " dp");
        widthLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        root.addView(widthLabel);

        SeekBar widthSeek = new SeekBar(this);
        widthSeek.setMax(40);
        widthSeek.setProgress(currentWidth - 10);
        widthSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int val = Math.max(10, progress + 10);
                widthLabel.setText("Edge Touch Width: " + val + " dp");
                prefs.edit().putInt(KEY_EDGE_WIDTH_DP, val).apply();
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) { restartIfRunning(); }
        });
        root.addView(widthSeek, createMarginParams(0, dpToPx(14)));

        // Edge Height SeekBar
        int currentHeightPct = prefs.getInt(KEY_EDGE_HEIGHT_PCT, 50);
        TextView heightLabel = new TextView(this);
        heightLabel.setText("Edge Strip Height: " + currentHeightPct + "% of screen");
        heightLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        root.addView(heightLabel);

        SeekBar heightSeek = new SeekBar(this);
        heightSeek.setMax(65);
        heightSeek.setProgress(currentHeightPct - 20);
        heightSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int val = Math.max(20, progress + 20);
                heightLabel.setText("Edge Strip Height: " + val + "% of screen");
                prefs.edit().putInt(KEY_EDGE_HEIGHT_PCT, val).apply();
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) { restartIfRunning(); }
        });
        root.addView(heightSeek, createMarginParams(0, dpToPx(14)));

        // Swipe Sensitivity SeekBar
        int currentSens = prefs.getInt(KEY_SENSITIVITY_DP, 26);
        TextView sensLabel = new TextView(this);
        sensLabel.setText("Swipe Step Distance (Kam = Fast): " + currentSens + " dp");
        sensLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        root.addView(sensLabel);

        SeekBar sensSeek = new SeekBar(this);
        sensSeek.setMax(45);
        sensSeek.setProgress(currentSens - 15);
        sensSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int val = Math.max(15, progress + 15);
                sensLabel.setText("Swipe Step Distance (Kam = Fast): " + val + " dp");
                prefs.edit().putInt(KEY_SENSITIVITY_DP, val).apply();
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) { restartIfRunning(); }
        });
        root.addView(sensSeek, createMarginParams(0, dpToPx(16)));

        setContentView(scrollView);
        updateStatusText();

        if (prefs.getBoolean(KEY_RUNNING, true) && Settings.canDrawOverlays(this)) {
            startEdgeService();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateStatusText();
    }

    private void adjustVolume(int direction) {
        AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        if (am != null) {
            am.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_SHOW_UI);
        }
    }

    private void startEdgeService() {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "'Display over other apps' permission ON karein", Toast.LENGTH_LONG).show();
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivity(intent);
            return;
        }
        prefs.edit().putBoolean(KEY_RUNNING, true).apply();
        Intent svc = new Intent(this, EdgeOverlayService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(svc);
        } else {
            startService(svc);
        }
        updateStatusText();
    }

    private void stopEdgeService() {
        prefs.edit().putBoolean(KEY_RUNNING, false).apply();
        stopService(new Intent(this, EdgeOverlayService.class));
        updateStatusText();
    }

    private void restartIfRunning() {
        if (prefs.getBoolean(KEY_RUNNING, false) && Settings.canDrawOverlays(this)) {
            stopService(new Intent(this, EdgeOverlayService.class));
            startEdgeService();
        }
    }

    private void requestBatteryExemption() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (pm != null && !pm.isIgnoringBatteryOptimizations(getPackageName())) {
                try {
                    Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                    intent.setData(Uri.parse("package:" + getPackageName()));
                    startActivity(intent);
                } catch (Exception e) {
                    startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
                }
            } else {
                Toast.makeText(this, "Battery Background Mode pehle se Unrestricted hai!", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void openNotificationSettings() {
        try {
            Intent intent = new Intent();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                intent.setAction(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
                intent.putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
            } else {
                intent.setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                intent.setData(Uri.parse("package:" + getPackageName()));
            }
            startActivity(intent);
            Toast.makeText(this, "Yahan 'Allow Notifications' OFF kar dein — notification hamesha ke liye band ho jayegi!", Toast.LENGTH_LONG).show();
        } catch (Exception ignored) {}
    }

    private void updateStatusText() {
        boolean hasPerm = Settings.canDrawOverlays(this);
        boolean running = prefs.getBoolean(KEY_RUNNING, false) && hasPerm;
        if (!hasPerm) {
            statusText.setText("Status: Permission Needed (Button 1 dabayein)");
            statusText.setTextColor(Color.parseColor("#C53030"));
        } else if (running) {
            statusText.setText("Status: ACTIVE IN BACKGROUND (Auto-Start ON)");
            statusText.setTextColor(Color.parseColor("#2F855A"));
        } else {
            statusText.setText("Status: STOPPED");
            statusText.setTextColor(Color.parseColor("#718096"));
        }
    }

    private Button createButton(String text, String hexColor) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextColor(Color.WHITE);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor(hexColor));
        bg.setCornerRadius(dpToPx(8));
        b.setBackground(bg);
        return b;
    }

    private LinearLayout.LayoutParams createMarginParams(int top, int bottom) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, top, 0, bottom);
        return lp;
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    // =========================================================================
    // 24/7 BACKGROUND OVERLAY SERVICE (SILENT MIN-PRIORITY CHANNEL)
    // =========================================================================
    public static class EdgeOverlayService extends Service {

        private static final String CHANNEL_ID = "edge_silent_channel";
        private WindowManager windowManager;
        private View leftEdgeView;
        private View rightEdgeView;
        private AudioManager audioManager;
        private Vibrator vibrator;

        @Override
        public void onCreate() {
            super.onCreate();
            windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
            audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
            vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
            startSilentForeground();
        }

        @Override
        public int onStartCommand(Intent intent, int flags, int startId) {
            removeOverlays();
            setupOverlays();
            return START_STICKY;
        }

        private void startSilentForeground() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                NotificationChannel channel = new NotificationChannel(
                        CHANNEL_ID,
                        "Silent Background Service",
                        NotificationManager.IMPORTANCE_MIN
                );
                channel.setShowBadge(false);
                channel.setSound(null, null);
                NotificationManager nm = getSystemService(NotificationManager.class);
                if (nm != null) {
                    nm.createNotificationChannel(channel);
                }
            }

            Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                    ? new Notification.Builder(this, CHANNEL_ID)
                    : new Notification.Builder(this).setPriority(Notification.PRIORITY_MIN);

            Notification notification = builder
                    .setContentTitle("Edge Volume")
                    .setContentText("Tap Button 3 in app to hide this notification")
                    .setSmallIcon(android.R.drawable.ic_lock_silent_mode_off)
                    .setOngoing(true)
                    .build();

            startForeground(101, notification);
        }

        private void setupOverlays() {
            if (!Settings.canDrawOverlays(this)) return;

            SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
            boolean leftEnabled = prefs.getBoolean(KEY_LEFT_ENABLED, true);
            boolean rightEnabled = prefs.getBoolean(KEY_RIGHT_ENABLED, true);
            boolean visibleStrip = prefs.getBoolean(KEY_VISIBLE_STRIP, false);
            int widthDp = prefs.getInt(KEY_EDGE_WIDTH_DP, 22);
            int heightPct = prefs.getInt(KEY_EDGE_HEIGHT_PCT, 50);
            int sensitivityDp = prefs.getInt(KEY_SENSITIVITY_DP, 26);

            DisplayMetrics dm = getResources().getDisplayMetrics();
            int stripWidthPx = Math.round(widthDp * dm.density);
            int stripHeightPx = Math.round(dm.heightPixels * (heightPct / 100f));
            int stepPx = Math.max(18, Math.round(sensitivityDp * dm.density));

            if (leftEnabled) {
                leftEdgeView = createEdgeView(visibleStrip, stepPx);
                windowManager.addView(leftEdgeView, createOverlayLayoutParams(
                        Gravity.START | Gravity.CENTER_VERTICAL, stripWidthPx, stripHeightPx));
            }

            if (rightEnabled) {
                rightEdgeView = createEdgeView(visibleStrip, stepPx);
                windowManager.addView(rightEdgeView, createOverlayLayoutParams(
                        Gravity.END | Gravity.CENTER_VERTICAL, stripWidthPx, stripHeightPx));
            }
        }

        private View createEdgeView(boolean visible, int stepPx) {
            View v = new View(this);
            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(16f);
            bg.setColor(visible ? Color.argb(65, 49, 130, 206) : Color.TRANSPARENT);
            v.setBackground(bg);

            v.setOnTouchListener(new View.OnTouchListener() {
                private float lastStepY = 0f;

                @Override
                public boolean onTouch(View view, MotionEvent event) {
                    switch (event.getActionMasked()) {
                        case MotionEvent.ACTION_DOWN:
                            lastStepY = event.getRawY();
                            if (visible) {
                                bg.setColor(Color.argb(140, 49, 130, 206));
                            }
                            return true;

                        case MotionEvent.ACTION_MOVE:
                            float currentY = event.getRawY();
                            float diffY = lastStepY - currentY;

                            if (Math.abs(diffY) >= stepPx) {
                                int direction = diffY > 0
                                        ? AudioManager.ADJUST_RAISE
                                        : AudioManager.ADJUST_LOWER;
                                if (audioManager != null) {
                                    audioManager.adjustStreamVolume(
                                            AudioManager.STREAM_MUSIC,
                                            direction,
                                            AudioManager.FLAG_SHOW_UI
                                    );
                                }
                                vibrateTick();
                                lastStepY = currentY;
                            }
                            return true;

                        case MotionEvent.ACTION_UP:
                        case MotionEvent.ACTION_CANCEL:
                            if (visible) {
                                bg.setColor(Color.argb(65, 49, 130, 206));
                            }
                            return true;
                    }
                    return false;
                }
            });
            return v;
        }

        private void vibrateTick() {
            if (vibrator == null || !vibrator.hasVibrator()) return;
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(12, VibrationEffect.DEFAULT_AMPLITUDE));
                } else {
                    vibrator.vibrate(12);
                }
            } catch (Exception ignored) {}
        }

        private WindowManager.LayoutParams createOverlayLayoutParams(int gravity, int width, int height) {
            int overlayType = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                    ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                    : WindowManager.LayoutParams.TYPE_PHONE;

            WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                    width,
                    height,
                    overlayType,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.TRANSLUCENT
            );
            params.gravity = gravity;
            return params;
        }

        private void removeOverlays() {
            if (leftEdgeView != null) {
                try { windowManager.removeView(leftEdgeView); } catch (Exception ignored) {}
                leftEdgeView = null;
            }
            if (rightEdgeView != null) {
                try { windowManager.removeView(rightEdgeView); } catch (Exception ignored) {}
                rightEdgeView = null;
            }
        }

        @Override
        public void onDestroy() {
            removeOverlays();
            super.onDestroy();
        }

        @Override
        public IBinder onBind(Intent intent) {
            return null;
        }
    }

    // =========================================================================
    // BOOT RECEIVER: AUTO-START SERVICE WHEN REALME PHONE RESTARTS
    // =========================================================================
    public static class BootReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null || intent.getAction() == null) return;
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
            if (prefs.getBoolean(KEY_RUNNING, true) && Settings.canDrawOverlays(context)) {
                Intent svc = new Intent(context, EdgeOverlayService.class);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(svc);
                } else {
                    context.startService(svc);
                }
            }
        }
    }
}
