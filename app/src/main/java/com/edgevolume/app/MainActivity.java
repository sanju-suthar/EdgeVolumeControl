package com.edgevolume.app;

import android.accessibilityservice.AccessibilityService;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
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
import android.os.PowerManager;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {

    public static final String PREFS_NAME = "EdgeVolumePrefs";
    public static final String KEY_MASTER_ENABLED = "master_enabled";
    public static final String KEY_LEFT_ENABLED = "left_enabled";
    public static final String KEY_RIGHT_ENABLED = "right_enabled";
    public static final String KEY_VISIBLE_STRIP = "visible_strip";
    public static final String KEY_EDGE_WIDTH_DP = "edge_width_dp";
    public static final String KEY_EDGE_HEIGHT_PCT = "edge_height_pct";
    public static final String KEY_SENSITIVITY_DP = "sensitivity_dp";

    private SharedPreferences prefs;
    private TextView statusText;
    private TextView batteryStatusText;

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

        TextView subtitle = new TextView(this);
        subtitle.setText("Auto-Start • Background Active • Zero Notification");
        subtitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        subtitle.setTextColor(Color.parseColor("#4A5568"));
        subtitle.setPadding(0, dpToPx(2), 0, dpToPx(12));
        root.addView(subtitle);

        statusText = new TextView(this);
        statusText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        statusText.setPadding(0, dpToPx(4), 0, dpToPx(6));
        root.addView(statusText);

        batteryStatusText = new TextView(this);
        batteryStatusText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        batteryStatusText.setPadding(0, 0, 0, dpToPx(14));
        root.addView(batteryStatusText);

        // Quick Volume Buttons (Backup Controls)
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

        // Step 1: Accessibility Service Button (No Notification + Auto-Start)
        Button enableServiceBtn = createButton("1. Enable Auto-Start Background Service", "#2F855A");
        enableServiceBtn.setOnClickListener(v -> {
            Toast.makeText(this, "Downloaded Services / Installed Apps me jaakar 'Edge Volume Control' ON karein", Toast.LENGTH_LONG).show();
            Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            startActivity(intent);
        });
        root.addView(enableServiceBtn, createMarginParams(0, dpToPx(8)));

        // Step 2: Ignore Battery Optimization Button (Keep Alive in Background on Realme UI)
        Button batteryBtn = createButton("2. Keep Active in Background (Battery Free)", "#D69E2E");
        batteryBtn.setOnClickListener(v -> requestBatteryExemption());
        root.addView(batteryBtn, createMarginParams(0, dpToPx(16)));

        // Master Switch (Pause/Resume Edge Strips anytime without opening Settings)
        Switch masterSwitch = new Switch(this);
        masterSwitch.setText("Edge Swipe Active (Master Switch)");
        masterSwitch.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        masterSwitch.setChecked(prefs.getBoolean(KEY_MASTER_ENABLED, true));
        masterSwitch.setOnCheckedChangeListener((b, checked) -> {
            prefs.edit().putBoolean(KEY_MASTER_ENABLED, checked).apply();
            updateStatusText();
        });
        root.addView(masterSwitch, createMarginParams(0, dpToPx(12)));

        // Left & Right Edge Toggles
        Switch leftSwitch = new Switch(this);
        leftSwitch.setText("Enable Left Edge Swipe");
        leftSwitch.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        leftSwitch.setChecked(prefs.getBoolean(KEY_LEFT_ENABLED, true));
        leftSwitch.setOnCheckedChangeListener((b, checked) ->
                prefs.edit().putBoolean(KEY_LEFT_ENABLED, checked).apply());
        root.addView(leftSwitch, createMarginParams(0, dpToPx(12)));

        Switch rightSwitch = new Switch(this);
        rightSwitch.setText("Enable Right Edge Swipe");
        rightSwitch.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        rightSwitch.setChecked(prefs.getBoolean(KEY_RIGHT_ENABLED, true));
        rightSwitch.setOnCheckedChangeListener((b, checked) ->
                prefs.edit().putBoolean(KEY_RIGHT_ENABLED, checked).apply());
        root.addView(rightSwitch, createMarginParams(0, dpToPx(12)));

        Switch visibleSwitch = new Switch(this);
        visibleSwitch.setText("Show Visible Edge Handle (Off = 100% Invisible)");
        visibleSwitch.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        visibleSwitch.setChecked(prefs.getBoolean(KEY_VISIBLE_STRIP, false));
        visibleSwitch.setOnCheckedChangeListener((b, checked) ->
                prefs.edit().putBoolean(KEY_VISIBLE_STRIP, checked).apply());
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
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
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
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
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
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        root.addView(sensSeek, createMarginParams(0, dpToPx(18)));

        TextView note = new TextView(this);
        note.setText("Realme 6 Setup Guide:\n"
                + "• Button 1 dabakar Accessibility me 'Edge Volume Control' ON kar dein — isse bina kisi notification ke app 24/7 background me chalega aur phone restart par khud start hoga.\n"
                + "• Button 2 dabakar Battery Optimization 'Allow' kar dein taaki Realme UI background me service ko kabhi roke nahi.");
        note.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        note.setTextColor(Color.parseColor("#4A5568"));
        root.addView(note);

        setContentView(scrollView);
        updateStatusText();
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

    private void requestBatteryExemption() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (pm != null && !pm.isIgnoringBatteryOptimizations(getPackageName())) {
                try {
                    Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                    intent.setData(Uri.parse("package:" + getPackageName()));
                    startActivity(intent);
                } catch (Exception e) {
                    Intent intent = new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
                    startActivity(intent);
                }
            } else {
                Toast.makeText(this, "Battery Unrestricted pehle se ON hai!", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private boolean isAccessibilityServiceEnabled() {
        ComponentName expected = new ComponentName(this, EdgeAccessibilityService.class);
        String enabledServices = Settings.Secure.getString(
                getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        );
        if (enabledServices == null) return false;
        TextUtils.SimpleStringSplitter splitter = new TextUtils.SimpleStringSplitter(':');
        splitter.setString(enabledServices);
        while (splitter.hasNext()) {
            ComponentName cn = ComponentName.unflattenFromString(splitter.next());
            if (cn != null && cn.equals(expected)) {
                return true;
            }
        }
        return false;
    }

    private void updateStatusText() {
        boolean serviceOn = isAccessibilityServiceEnabled();
        boolean masterOn = prefs.getBoolean(KEY_MASTER_ENABLED, true);

        if (!serviceOn) {
            statusText.setText("Service Status: OFF (Button 1 dabakar Accessibility ON karein)");
            statusText.setTextColor(Color.parseColor("#C53030"));
        } else if (!masterOn) {
            statusText.setText("Service Status: PAUSED (Master Switch OFF hai)");
            statusText.setTextColor(Color.parseColor("#D69E2E"));
        } else {
            statusText.setText("Service Status: ACTIVE IN BACKGROUND (No Notification)");
            statusText.setTextColor(Color.parseColor("#2F855A"));
        }

        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        boolean batteryFree = Build.VERSION.SDK_INT < Build.VERSION_CODES.M
                || (pm != null && pm.isIgnoringBatteryOptimizations(getPackageName()));
        if (batteryFree) {
            batteryStatusText.setText("Battery Background Mode: UNRESTRICTED (Auto-Start Ready)");
            batteryStatusText.setTextColor(Color.parseColor("#2F855A"));
        } else {
            batteryStatusText.setText("Battery Background Mode: RESTRICTED (Button 2 dabayein)");
            batteryStatusText.setTextColor(Color.parseColor("#D69E2E"));
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
    // ZERO-NOTIFICATION, AUTO-START, 24/7 BACKGROUND ACCESSIBILITY OVERLAY
    // =========================================================================
    public static class EdgeAccessibilityService extends AccessibilityService
            implements SharedPreferences.OnSharedPreferenceChangeListener {

        private WindowManager windowManager;
        private View leftEdgeView;
        private View rightEdgeView;
        private AudioManager audioManager;
        private Vibrator vibrator;
        private SharedPreferences prefs;

        @Override
        protected void onServiceConnected() {
            super.onServiceConnected();
            windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
            audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
            vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
            prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
            prefs.registerOnSharedPreferenceChangeListener(this);
            refreshOverlays();
        }

        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
            refreshOverlays();
        }

        private void refreshOverlays() {
            removeOverlays();
            if (prefs == null || !prefs.getBoolean(KEY_MASTER_ENABLED, true)) {
                return;
            }

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

            try {
                if (leftEnabled) {
                    leftEdgeView = createEdgeView(visibleStrip, stepPx);
                    windowManager.addView(leftEdgeView, createOverlayParams(
                            Gravity.START | Gravity.CENTER_VERTICAL, stripWidthPx, stripHeightPx));
                }
                if (rightEnabled) {
                    rightEdgeView = createEdgeView(visibleStrip, stepPx);
                    windowManager.addView(rightEdgeView, createOverlayParams(
                            Gravity.END | Gravity.CENTER_VERTICAL, stripWidthPx, stripHeightPx));
                }
            } catch (Exception ignored) {}
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
                            float diffY = lastStepY - currentY; // Positive = Swipe Up

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

        private WindowManager.LayoutParams createOverlayParams(int gravity, int width, int height) {
            WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                    width,
                    height,
                    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.TRANSLUCENT
            );
            params.gravity = gravity;
            return params;
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
        public void onAccessibilityEvent(AccessibilityEvent event) {}

        @Override
        public void onInterrupt() {}

        @Override
        public void onDestroy() {
            if (prefs != null) {
                prefs.unregisterOnSharedPreferenceChangeListener(this);
            }
            removeOverlays();
            super.onDestroy();
        }
    }

    // =========================================================================
    // BOOT RECEIVER FOR AUTO-START ON REALME REBOOT
    // =========================================================================
    public static class BootReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null || intent.getAction() == null) return;
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
            prefs.edit().putBoolean(KEY_MASTER_ENABLED, true).apply();
        }
    }
}
                                      
