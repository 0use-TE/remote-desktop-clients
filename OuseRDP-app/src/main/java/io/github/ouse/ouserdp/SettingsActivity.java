package io.github.ouse.ouserdp;

import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.iiordanov.bVNC.Constants;
import com.iiordanov.bVNC.WindowsKeyCaptureService;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

public final class SettingsActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.displayCutout());
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });

        MaterialToolbar toolbar = new MaterialToolbar(this);
        toolbar.setTitle(R.string.ouse_settings);
        toolbar.setNavigationIcon(R.drawable.ouse_back_icon);
        toolbar.setNavigationContentDescription(R.string.ouse_back);
        toolbar.setNavigationOnClickListener(view -> finish());
        root.addView(toolbar, new LinearLayout.LayoutParams(-1, -2));

        FrameLayout preferences = new FrameLayout(this);
        preferences.setId(R.id.ouse_settings_content);
        root.addView(preferences, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
        ViewCompat.requestApplyInsets(root);
        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.ouse_settings_content, new DefaultsFragment()).commit();
        }
    }

    private void showAbout() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.ouse_app_name)
                .setMessage(R.string.ouse_credits)
                .setNeutralButton(R.string.ouse_license, (dialog, which) -> showLicense())
                .setPositiveButton(android.R.string.ok, null).show();
    }

    private void showLicense() {
        try (InputStream input = getAssets().open("LICENSE");
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
            new MaterialAlertDialogBuilder(this).setTitle(R.string.ouse_license)
                    .setMessage(output.toString("UTF-8")).setPositiveButton(android.R.string.ok, null).show();
        } catch (IOException error) {
            android.util.Log.e("OuseRDP", "Unable to read the bundled license", error);
        }
    }

    public static final class DefaultsFragment extends PreferenceFragmentCompat {
        private Preference shortcutSetup;

        @Override
        public void onCreatePreferences(Bundle state, String rootKey) {
            getPreferenceManager().setSharedPreferencesName(Constants.generalSettingsTag);
            setPreferencesFromResource(R.xml.ouse_defaults, rootKey);
            shortcutSetup = findPreference("ouseShortcutSetup");
            shortcutSetup.setOnPreferenceClickListener(preference -> {
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
                return true;
            });
            findPreference("ouseAbout").setOnPreferenceClickListener(preference -> {
                ((SettingsActivity) requireActivity()).showAbout();
                return true;
            });
        }

        @Override
        public void onResume() {
            super.onResume();
            shortcutSetup.setSummary(WindowsKeyCaptureService.isEnabled(requireContext())
                    ? R.string.ouse_shortcuts_enabled : R.string.ouse_shortcuts_disabled);
        }
    }
}
