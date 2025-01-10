package com.github.timboode.NYP_alert_android.activities;

import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;

import android.provider.Settings;
import android.view.Gravity;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import com.github.timboode.NYP_alert_android.R;
import com.github.timboode.NYP_alert_android.config.Alerts;
import com.github.timboode.NYP_alert_android.config.Safety;
import com.github.timboode.NYP_alert_android.config.ThreatTypes;
import com.github.timboode.NYP_alert_android.logic.alerts.AlertTypes;
import com.github.timboode.NYP_alert_android.logic.communication.intents.AlertPopupParameters;
import com.github.timboode.NYP_alert_android.logic.feedback.sound.SoundLogic;
import com.github.timboode.NYP_alert_android.logic.phone.PowerManagement;
import com.github.timboode.NYP_alert_android.logic.settings.AppPreferences;
import com.github.timboode.NYP_alert_android.ui.localization.rtl.RTLSupport;
import com.github.timboode.NYP_alert_android.utils.feedback.Volume;
import com.github.timboode.NYP_alert_android.utils.localization.DateTime;
import com.github.timboode.NYP_alert_android.utils.ui.DensityUtil;

import java.util.Arrays;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class Popup extends AppCompatActivity {
    Timer mTimer;

    TextView mCities;
    TextView mCounter;
    TextView mInstructions;

    Button mClose;
    Button mSilence;

    ImageView mThreatIcon;

    public static void showAlertPopup(String alertType, String message, String threatType, Context context) {
        // Only display popup for primary/secondary alerts (no test/sound/system notifications)
        if (!alertType.equals(AlertTypes.PRIMARY) && !alertType.equals(AlertTypes.SECONDARY)) {
            return;
        }

        // User hasn't enabled popup for primary alerts?
        if (alertType.equals(AlertTypes.PRIMARY) && !AppPreferences.getPopupEnabled(context)) {
            return;
        }

        // User hasn't enabled popup for secondary alerts?
        if (alertType.equals(AlertTypes.SECONDARY) && !AppPreferences.getSecondaryPopupEnabled(context)) {
            return;
        }

        // Android M: Check if we have permission to draw over other apps
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(context)) {
            return;
        }

        // Turn screen on
        PowerManagement.wakeUpScreen(context);

        // Create new popup intent
        Intent popupIntent = new Intent();

        // Set class to popup activity
        popupIntent.setClass(context, Popup.class);

        // Pass on alert cities & threat type
        popupIntent.putExtra(AlertPopupParameters.THREAT_TYPE, threatType);
        popupIntent.putExtra(AlertPopupParameters.THREAT_MESSAGE, message);

        // Clear top, set as new task
        popupIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        popupIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        popupIntent.addFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK);

        // Display popup activity
        context.startActivity(popupIntent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Load UI elements
        initializeUI();

        // Populate UI elements
        initializeAlert(getIntent());

        // Volume keys should control alert volume
        Volume.setVolumeKeysAction(this);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);

        // Update popup with new alert info
        initializeAlert(intent);
    }

    void initializeUI() {
        // RTL action bar hack
        RTLSupport.mirrorActionBar(this);

        // Set up UI
        setContentView(R.layout.popup);

        // Cache UI objects
        mClose = (Button) findViewById(R.id.close);
        mCities = (TextView) findViewById(R.id.cities);
        mSilence = (Button) findViewById(R.id.silence);
        mCounter = (TextView) findViewById(R.id.counter);
        mThreatIcon = (ImageView) findViewById(R.id.threatIcon);
        mInstructions = (TextView) findViewById(R.id.instructions);

        // Set up listeners
        initializeListeners();
    }

    void initializeListeners() {
        // Close button clicked
        mClose.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // Stop the media service
                SoundLogic.stopSound(Popup.this);

                // Close popup
                finish();
            }
        });

        // Silence button clicked
        mSilence.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // Stop the media service
                SoundLogic.stopSound(Popup.this);
            }
        });
    }

    void initializeAlert(Intent intent) {
        String threatType = intent.getStringExtra(AlertPopupParameters.THREAT_TYPE);
        String threatMessage = intent.getStringExtra(AlertPopupParameters.THREAT_MESSAGE);
        long timestamp = intent.getLongExtra(AlertPopupParameters.TIMESTAMP, DateTime.getUnixTimestamp());

        // Set localized threat type as popup title
        setTitle(threatType);

        // Display threat icon, localized cities, safety instructions
        mThreatIcon.setImageResource(R.drawable.ic_radiological_event);
        mInstructions.setText(threatMessage);

        // Missile alert?
        if (threatType.contains(ThreatTypes.MISSILES)) {
            // Only display threat icon for non-missile threats
            mThreatIcon.setVisibility(View.GONE);
        }

        // System alert?
        if (threatType.equals(ThreatTypes.SYSTEM)) {
            // Hide instructions text as it's redundant
            mInstructions.setVisibility(View.GONE);
        }

        mCounter.setVisibility(View.GONE);
    }

    @Override
    protected void onUserLeaveHint() {
        super.onUserLeaveHint();

        // End activity
        if (!isFinishing()) {
            finish();
        }
    }

    void scheduleRocketCountdown(long timestamp, int seconds) {
        // Cancel previous timer if set
        if (mTimer != null) {
            mTimer.cancel();
        }

        // Offset impact to account for delivery delay
        // Seconds = Seconds - Main.IMPACT_COUNTDOWN_OFFSET;

        // Calculate time to impact
        final long impactTimestamp = (timestamp * 1000) + (seconds * 1000);

        // Schedule a new timer
        mTimer = new Timer();

        // Run every 100ms
        mTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        // Activity died?
                        if (isFinishing() || isDestroyed()) {
                            return;
                        }

                        // Update countdown
                        updateCountdownTimer(impactTimestamp);
                    }
                });
            }
        }, 0, 100);
    }

    void updateCountdownTimer(long impactTimestamp) {
        // Get current time
        long currentTimestamp = System.currentTimeMillis();

        // Calculate seconds left to brace for impact
        int seconds = (int) Math.abs((impactTimestamp - currentTimestamp) / 1000);

        // Did rocket already impact?
        if (currentTimestamp <= impactTimestamp) {
            // Convert it
            updateCountdownTimerText(seconds, R.color.colorCountdownPreImpact);
        }
        else if (currentTimestamp > impactTimestamp) {
            // Number of seconds to wait after impact
            int postImpactSeconds = (Safety.POST_IMPACT_WAIT_MINUTES * 60);

            // Stop counting after X minutes
            if (seconds >= postImpactSeconds) {
                // Show green counter
                updateCountdownTimerText(postImpactSeconds, R.color.colorCountdownPostImpactSafe);

                // Shut down activity after post impact + X seconds (padding)
                if (seconds >= postImpactSeconds + Alerts.ALERT_POPUP_DONE_PADDING) {
                    // Done with activity (shuts down screen as well)
                    finish();
                }

                // Stop execution
                return;
            }

            // Convert it
            updateCountdownTimerText(seconds, R.color.colorCountdownPostImpact);
        }
    }

    void updateCountdownTimerText(int seconds, int color) {
        // Get human-readable minutes
        int minutes = seconds / 60;

        // Convert seconds to support the minutes
        seconds = seconds % 60;

        // Set text color
        mCounter.setTextColor(getResources().getColor(color));

        // Set countdown text
        mCounter.setText(String.format("%02d:%02d", minutes, seconds));
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        // Support for RTL languages
        RTLSupport.mirrorActionBar(this);
    }

    @Override
    protected void onPause() {
        super.onPause();

        // Fade out gradually
        overridePendingTransition(0, R.anim.fade_out);
    }

    void centerPopupInParent(Window Window) {
        // Fade out
        FrameLayout rootView = (FrameLayout) Window.getDecorView();

        // Get action bar layout
        View actionBarLayout = rootView.getChildAt(0);

        // Detach it from decor view
        rootView.removeView(actionBarLayout);

        // Create new frame layout
        FrameLayout wrapLayout = new FrameLayout(this);

        // Set params to WRAP_CONTENT
        FrameLayout.LayoutParams layoutParams = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT);

        // Get 25 as PX value
        int dpToPixels = DensityUtil.convertDPToPixels(this, 25);

        // Set margins
        layoutParams.setMargins(dpToPixels, dpToPixels, dpToPixels, dpToPixels);

        // Set gravity to CENTER_VERTICAL
        layoutParams.gravity = Gravity.CENTER_VERTICAL;

        // Set layout params
        wrapLayout.setLayoutParams(layoutParams);

        // Add ActionBar to layout
        wrapLayout.addView(actionBarLayout);

        // Add it to root view
        rootView.addView(wrapLayout);
    }

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();

        // Get window object
        Window window = getWindow();

        // Turn screen on, show when locked and keep screen on
        window.addFlags(
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                        //| WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
                        | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                        | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // Call parent
        centerPopupInParent(window);
    }

    public boolean onOptionsItemSelected(final MenuItem Item) {
        // Check item ID
        switch (Item.getItemId()) {
            // Home button?
            case android.R.id.home:
                onBackPressed();
        }

        return super.onOptionsItemSelected(Item);
    }
}
