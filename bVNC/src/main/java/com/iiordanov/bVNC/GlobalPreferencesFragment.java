package com.iiordanov.bVNC;

import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;

import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.SwitchPreferenceCompat;

import com.undatech.remoteClientUi.R;

public class GlobalPreferencesFragment extends PreferenceFragmentCompat {
    private static final String CAPTURE_WINDOWS_KEY = "captureWindowsKey";

    @Override
    public void onCreatePreferences(Bundle bundle, String s) {
        getPreferenceManager().setSharedPreferencesName(Constants.generalSettingsTag);
        setPreferencesFromResource(R.xml.global_preferences, s);
        if (Utils.isVnc(getContext())) {
            addPreferencesFromResource(R.xml.global_preferences_vnc);
        } else if (Utils.isRdp(getContext())) {
            addPreferencesFromResource(R.xml.global_preferences_rdp);
            findPreference(CAPTURE_WINDOWS_KEY).setOnPreferenceChangeListener((preference, enabled) -> {
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
                return false;
            });
        } else if (Utils.isSpice(getContext())) {
            addPreferencesFromResource(R.xml.global_preferences_spice);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        SwitchPreferenceCompat capture = findPreference(CAPTURE_WINDOWS_KEY);
        if (capture != null) {
            capture.setChecked(WindowsKeyCaptureService.isEnabled(requireContext()));
        }
    }

    @Override
    public boolean onPreferenceTreeClick(Preference preference) {
        if ("openDefaultConnectionSettings".equals(preference.getKey())) {
            Utils.openDefaultConnectionSettings(requireContext());
            return true;
        }
        return super.onPreferenceTreeClick(preference);
    }
}
