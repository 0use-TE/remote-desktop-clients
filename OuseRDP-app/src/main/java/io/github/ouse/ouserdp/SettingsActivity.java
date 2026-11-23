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
    private final androidx.activity.result.ActivityResultLauncher<String> saveDiagnostics = registerForActivityResult(
            new androidx.activity.result.contract.ActivityResultContracts.CreateDocument("text/plain"), uri -> {
                if (uri == null) return;
                try (java.io.OutputStream output = getContentResolver().openOutputStream(uri)) {
                    if (output == null) throw new IOException("No output stream");
                    output.write((KeyboardServiceHealth.diagnostics(this) + "\n" + com.undatech.opaque.RdpSessionDiagnostics.export(this)).getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    android.widget.Toast.makeText(this, "诊断日志已保存", android.widget.Toast.LENGTH_LONG).show();
                } catch (IOException | SecurityException error) {
                    android.widget.Toast.makeText(this, "日志保存失败，请重新选择文件", android.widget.Toast.LENGTH_LONG).show();
                }
            });
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
        String version = "";
        try { version = getPackageManager().getPackageInfo(getPackageName(), 0).versionName; }
        catch (android.content.pm.PackageManager.NameNotFoundException ignored) {}
        var dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.ouse_app_name)
                .setMessage(getString(R.string.ouse_credits) + "\n\n" + version)
                .setNeutralButton(R.string.ouse_license, (licenseDialog, which) -> showLicense())
                .setPositiveButton(android.R.string.ok, null).show();
        android.widget.TextView text = dialog.findViewById(android.R.id.message);
        if (text != null) text.setTextSize(20);
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
        private final android.os.Handler healthHandler = new android.os.Handler(android.os.Looper.getMainLooper());
        private final Runnable refreshHealth = new Runnable() {
            @Override public void run() {
                if (!isResumed()) return;
                KeyboardServiceHealth.poll(requireContext());
                shortcutSetup.setSummary(KeyboardServiceHealth.summary(KeyboardServiceHealth.state(requireContext())));
                healthHandler.postDelayed(this, 1000);
            }
        };

        @Override
        public void onCreatePreferences(Bundle state, String rootKey) {
            getPreferenceManager().setSharedPreferencesName(Constants.generalSettingsTag);
            setPreferencesFromResource(R.xml.ouse_defaults, rootKey);
            shortcutSetup = findPreference("ouseShortcutSetup");
            shortcutSetup.setOnPreferenceClickListener(preference -> {
                KeyboardServiceHealth.showRecovery(requireActivity());
                return true;
            });
            findPreference("ouseSessionDiagnostics").setOnPreferenceClickListener(preference -> {
                ((SettingsActivity) requireActivity()).saveDiagnostics.launch("OuseRDP-diagnostics.txt");
                return true;
            });
            findPreference("ouseSharedFolder").setOnPreferenceClickListener(preference -> {
                startActivity(new Intent(requireContext(), SharedFolderActivity.class));
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
            healthHandler.removeCallbacks(refreshHealth);
            healthHandler.post(refreshHealth);
        }

        @Override public void onPause() {
            healthHandler.removeCallbacks(refreshHealth);
            super.onPause();
        }
    }
}
