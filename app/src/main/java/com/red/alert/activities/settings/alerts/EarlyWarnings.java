package com.red.alert.activities.settings.alerts;

import android.content.Context;
import android.content.res.Configuration;
import android.os.Bundle;
import android.view.MenuItem;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.FragmentTransaction;

import com.google.android.material.appbar.MaterialToolbar;
import com.red.alert.R;
import com.red.alert.ui.localization.rtl.RTLSupport;
import com.red.alert.utils.feedback.Volume;
import com.red.alert.utils.localization.Localization;

public class EarlyWarnings extends AppCompatActivity {
    EarlyWarningsPreferenceFragment mFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Support for RTL languages
        RTLSupport.mirrorActionBar(this);

        // Load UI elements
        initializeUI();

        // Volume keys should control alert volume
        Volume.setVolumeKeysAction(this);
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Support for RTL languages
        RTLSupport.mirrorActionBar(this);
    }

    @Override
    protected void attachBaseContext(Context base) {
        // Reapply locale
        Localization.overridePhoneLocale(base);
        super.attachBaseContext(base);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        // Support for RTL languages
        RTLSupport.mirrorActionBar(this);
    }

    void initializeUI() {
        // Set up layout with toolbar
        setContentView(R.layout.preference_activity);

        // Set up Material Toolbar
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        // Allow click on home button
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        // Support for RTL languages
        RTLSupport.mirrorActionBar(this);

        // Load preference fragment
        mFragment = new EarlyWarningsPreferenceFragment();
        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        transaction.replace(R.id.preference_container, mFragment);
        transaction.commit();
    }

    public void onFragmentPreferencesReady() {
        // Preferences have been loaded - nothing special needed for this simple screen
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
