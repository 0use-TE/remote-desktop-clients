package io.github.ouse.ouserdp;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.iiordanov.bVNC.ConnectionBean;
import com.iiordanov.bVNC.Constants;
import com.iiordanov.bVNC.OuseConnectionOptions;
import com.iiordanov.bVNC.OuseDesktopPreferences;
import com.iiordanov.bVNC.OuseSavedCredentials;
import com.iiordanov.bVNC.Utils;
import com.undatech.opaque.Connection;
import com.undatech.opaque.util.ConnectionLoader;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ConnectionsActivity extends AppCompatActivity {
    private static final String LAST_CONNECTION = "lastConnection";
    private static final int DEFAULT_RDP_PORT = 3389;
    private LinearLayout list;
    private KeyboardServiceMonitor keyboardHealth;
    private ConnectionBean pendingMicrophoneConnection;
    private String pendingPassword;
    private final ActivityResultLauncher<String> microphonePermission = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(), granted -> {
                ConnectionBean connection = pendingMicrophoneConnection;
                String password = pendingPassword;
                pendingMicrophoneConnection = null;
                pendingPassword = null;
                if (connection != null) {
                    if (!granted) {
                        Toast.makeText(this, R.string.ouse_microphone_denied, Toast.LENGTH_LONG).show();
                    }
                    startSession(connection, password, granted);
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Utils.setSharedPreferenceBoolean(this, Constants.doNotShowDesktopThumbnails, true);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(color(R.color.ouse_background));
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        root.addView(scroll, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(dp(24), dp(24), dp(24), dp(24));
        FrameLayout.LayoutParams columnParams = new FrameLayout.LayoutParams(-1, -2, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        scroll.addView(column, columnParams);
        scroll.addOnLayoutChangeListener((view, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
            int width = Math.min(right - left, dp(760));
            if (width > 0 && width != columnParams.width) {
                columnParams.width = width;
                column.setLayoutParams(columnParams);
            }
        });

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        column.addView(header, new LinearLayout.LayoutParams(-1, -2));
        TextView title = text(getString(R.string.ouse_app_name), 32, R.color.ouse_text);
        title.setTypeface(null, Typeface.BOLD);
        header.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        header.addView(button(R.string.ouse_settings,
                () -> startActivity(new Intent(this, SettingsActivity.class)), true));

        keyboardHealth = new KeyboardServiceMonitor(this);

        MaterialButton add = button(R.string.ouse_add_computer, () -> edit(null), false);
        LinearLayout.LayoutParams action = new LinearLayout.LayoutParams(-2, -2);
        action.topMargin = dp(18);
        column.addView(add, action);

        TextView heading = text(getString(R.string.ouse_saved_connections), 18, R.color.ouse_text);
        heading.setTypeface(null, Typeface.BOLD);
        LinearLayout.LayoutParams headingParams = new LinearLayout.LayoutParams(-1, -2);
        headingParams.topMargin = dp(22);
        headingParams.bottomMargin = dp(12);
        column.addView(heading, headingParams);

        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        column.addView(list, new LinearLayout.LayoutParams(-1, -2));
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.displayCutout());
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });
        setContentView(root);
        ViewCompat.requestApplyInsets(root);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshConnections();
        keyboardHealth.start();
    }

    @Override protected void onPause() {
        if (keyboardHealth != null) keyboardHealth.stop();
        super.onPause();
    }

    private void refreshConnections() {
        list.removeAllViews();
        List<Connection> connections = new ArrayList<>(
                new ConnectionLoader(this, false).getConnectionsById().values());
        String recent = getPreferences(Context.MODE_PRIVATE).getString(LAST_CONNECTION, "");
        Collections.sort(connections, (first, second) -> {
            int order = Boolean.compare(!recent.equals(first.getId()), !recent.equals(second.getId()));
            return order == 0 ? first.getAddress().compareToIgnoreCase(second.getAddress()) : order;
        });
        if (connections.isEmpty()) {
            list.addView(text(getString(R.string.ouse_empty_connections), 16, R.color.ouse_secondary_text));
        }
        for (Connection connection : connections) {
            addConnectionRow((ConnectionBean) connection);
        }
    }

    private void addConnectionRow(ConnectionBean connection) {
        MaterialCardView card = new MaterialCardView(this);
        card.setCardBackgroundColor(color(R.color.ouse_surface));
        card.setRadius(dp(16));
        card.setCardElevation(0);
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(-1, -2);
        cardParams.bottomMargin = dp(10);
        list.addView(card, cardParams);

        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), dp(8), dp(8), dp(8));
        card.addView(row, new FrameLayout.LayoutParams(-1, -2));

        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.setMinimumHeight(dp(52));
        labels.setGravity(Gravity.CENTER_VERTICAL);
        labels.setFocusable(true);
        labels.setOnClickListener(view -> requestPassword(connection));
        labels.setContentDescription(getString(R.string.ouse_connect_to, endpoint(connection)));
        row.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));
        TextView address = text(endpoint(connection), 18, R.color.ouse_text);
        address.setTypeface(null, Typeface.BOLD);
        labels.addView(address);
        labels.addView(text(username(connection), 14, R.color.ouse_secondary_text));
        row.addView(button(R.string.ouse_edit_computer, () -> edit(connection), true));
    }

    private void edit(ConnectionBean existing) {
        boolean adding = existing == null;
        LinearLayout form = dialogForm();
        EditText address = field(R.string.ouse_address, InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_VARIATION_URI);
        EditText account = field(R.string.ouse_username, InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        form.addView(address);
        form.addView(account);
        if (!adding) {
            address.setText(endpoint(existing));
            account.setText(username(existing));
        }
        OuseConnectionOptions options = OuseConnectionOptions.load(this, adding ? "" : existing.getFilename());
        TextView redirectionTitle = text(getString(R.string.ouse_redirection), 16, R.color.ouse_text);
        redirectionTitle.setTypeface(null, Typeface.BOLD);
        redirectionTitle.setPadding(0, dp(14), 0, 0);
        form.addView(redirectionTitle);
        MaterialCheckBox clipboard = checkbox(form, R.string.ouse_redirect_clipboard, options.clipboard);
        MaterialCheckBox sound = checkbox(form, R.string.ouse_redirect_sound, options.sound);
        MaterialCheckBox microphone = checkbox(form, R.string.ouse_redirect_microphone, options.microphone);
        MaterialCheckBox forgetPassword = null;
        if (!adding && OuseSavedCredentials.hasPassword(this, existing.getFilename())) {
            forgetPassword = checkbox(form, R.string.ouse_forget_password, false);
        }
        MaterialCheckBox forgetSavedPassword = forgetPassword;
        ScrollView scroll = new ScrollView(this);
        scroll.addView(form);

        MaterialAlertDialogBuilder dialogBuilder = new MaterialAlertDialogBuilder(this)
                .setTitle(adding ? R.string.ouse_add_computer : R.string.ouse_edit_computer)
                .setView(scroll)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(adding ? R.string.ouse_save_and_connect : R.string.ouse_save, null);
        if (!adding) {
            dialogBuilder.setNeutralButton(R.string.ouse_remove,
                    (dialog, which) -> confirmRemoval(existing));
        }
        AlertDialog dialog = dialogBuilder.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
            URI target = parseAddress(address.getText().toString().trim());
            if (target == null) {
                address.setError(getString(R.string.ouse_address_error));
                address.requestFocus();
                return;
            }
            String enteredAccount = account.getText().toString().trim();
            int domainSeparator = enteredAccount.indexOf('\\');
            String user = enteredAccount.substring(domainSeparator + 1).trim();
            if (user.isEmpty()) {
                account.setError(getString(R.string.ouse_username_error));
                account.requestFocus();
                return;
            }
            ConnectionBean connection = adding ? new ConnectionBean(this) : existing;
            String host = target.getHost().replace("[", "").replace("]", "");
            int port = target.getPort() < 0 ? DEFAULT_RDP_PORT : target.getPort();
            String domain = domainSeparator < 0 ? "" : enteredAccount.substring(0, domainSeparator);
            boolean addressChanged = !adding &&
                    (!host.equalsIgnoreCase(existing.getAddress()) || port != existing.getPort());
            if (addressChanged) {
                connection.setX509KeySignature("");
            }
            if (!adding && (addressChanged || !user.equals(existing.getUserName())
                    || !domain.equals(existing.getRdpDomain())
                    || (forgetSavedPassword != null && forgetSavedPassword.isChecked()))) {
                OuseSavedCredentials.remove(this, existing.getFilename());
            }
            connection.setAddress(host);
            connection.setPort(port);
            connection.setNickname(endpoint(connection));
            connection.setUserName(user);
            connection.setRdpDomain(domain);
            applyDesktopDefaults(connection);
            connection.setPassword("");
            connection.save(this);
            OuseConnectionOptions.save(this, connection.getFilename(), clipboard.isChecked(),
                    sound.isChecked(), microphone.isChecked());
            dialog.dismiss();
            refreshConnections();
            if (adding) {
                requestPassword(connection);
            }
        });
    }

    private void requestPassword(ConnectionBean connection) {
        String saved = OuseSavedCredentials.read(this, connection.getFilename());
        if (saved != null) {
            connect(connection, saved);
            return;
        }
        LinearLayout form = dialogForm();
        EditText password = field(R.string.ouse_password, InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        form.addView(password);
        MaterialCheckBox remember = checkbox(form, R.string.ouse_remember_password, false);
        if (!OuseSavedCredentials.isSupported()) {
            remember.setVisibility(android.view.View.GONE);
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.ouse_connect_to, endpoint(connection)))
                .setView(form)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.ouse_connect, (dialog, which) -> {
                    String entered = password.getText().toString();
                    if (remember.isChecked() && !entered.isEmpty() &&
                            !OuseSavedCredentials.save(this, connection.getFilename(), entered)) {
                        Toast.makeText(this, com.undatech.remoteClientUi.R.string.ouse_credential_save_failed,
                                Toast.LENGTH_LONG).show();
                    }
                    connect(connection, entered);
                })
                .show();
    }

    private void connect(ConnectionBean connection, String password) {
        boolean microphone = OuseConnectionOptions.load(this, connection.getFilename()).microphone;
        if (microphone && ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            pendingMicrophoneConnection = connection;
            pendingPassword = password;
            microphonePermission.launch(Manifest.permission.RECORD_AUDIO);
        } else {
            startSession(connection, password, microphone);
        }
    }

    private void startSession(ConnectionBean connection, String password, boolean microphone) {
        applyDesktopDefaults(connection);
        boolean sound = OuseConnectionOptions.load(this, connection.getFilename()).sound;
        connection.setRemoteSoundType(sound ? Constants.REMOTE_SOUND_ON_DEVICE : Constants.REMOTE_SOUND_DISABLED);
        connection.setEnableRecording(microphone);
        connection.setPassword(password);
        getPreferences(Context.MODE_PRIVATE).edit().putString(LAST_CONNECTION, connection.getId()).apply();
        Intent intent = new Intent(this, SessionActivity.class);
        intent.putExtra(Utils.getConnectionString(this), connection.Gen_getValues());
        connection.setPassword("");
        startActivity(intent);
    }

    private void applyDesktopDefaults(ConnectionBean connection) {
        connection.setKeepPassword(false);
        connection.setExtraKeysToggleType(Constants.EXTRA_KEYS_OFF);
        connection.setPreferSendingUnicode(false);
        connection.setScaleMode(ImageView.ScaleType.FIT_CENTER);
        connection.setWindowContents(OuseDesktopPreferences.showWindowsWhileDragging(this));
        connection.setDesktopBackground(true);
        connection.setFontSmoothing(true);
        connection.setVisualStyles(true);
    }

    private static URI parseAddress(String value) {
        try {
            URI target = new URI("rdp://" + value);
            long port = target.getPort();
            return target.getHost() != null && target.getUserInfo() == null
                    && target.getPath().isEmpty() && target.getQuery() == null
                    && target.getFragment() == null && port != 0 && port <= 65535 ? target : null;
        } catch (URISyntaxException error) {
            return null;
        }
    }

    private static String endpoint(Connection connection) {
        String host = connection.getAddress();
        if (host.contains(":")) {
            host = "[" + host + "]";
        }
        return connection.getPort() == DEFAULT_RDP_PORT ? host : host + ":" + connection.getPort();
    }

    private static String username(ConnectionBean connection) {
        String domain = connection.getRdpDomain();
        return domain.isEmpty() ? connection.getUserName() : domain + "\\" + connection.getUserName();
    }

    private void confirmRemoval(ConnectionBean connection) {
        new MaterialAlertDialogBuilder(this)
                .setMessage(getString(R.string.ouse_remove_computer, endpoint(connection)))
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.ouse_remove, (dialog, which) -> {
                    OuseSavedCredentials.remove(this, connection.getFilename());
                    OuseConnectionOptions.remove(this, connection.getFilename());
                    connection.deleteSavedConnection(this);
                    refreshConnections();
                })
                .show();
    }

    private LinearLayout dialogForm() {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(24), dp(10), dp(24), 0);
        return form;
    }

    private EditText field(int hint, int inputType) {
        EditText field = new EditText(this);
        field.setSingleLine();
        field.setHint(hint);
        field.setInputType(inputType);
        return field;
    }

    private MaterialCheckBox checkbox(LinearLayout form, int label, boolean checked) {
        MaterialCheckBox item = new MaterialCheckBox(this);
        item.setText(label);
        item.setChecked(checked);
        form.addView(item, new LinearLayout.LayoutParams(-1, -2));
        return item;
    }

    private MaterialButton button(int label, Runnable action, boolean textStyle) {
        MaterialButton button = new MaterialButton(this, null, textStyle
                ? com.google.android.material.R.attr.materialButtonOutlinedStyle
                : com.google.android.material.R.attr.materialButtonStyle);
        button.setText(label);
        button.setOnClickListener(view -> action.run());
        return button;
    }

    private TextView text(String value, int size, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color(color));
        return view;
    }

    private int color(int id) {
        return androidx.core.content.ContextCompat.getColor(this, id);
    }

    private int dp(int amount) {
        return Math.round(amount * getResources().getDisplayMetrics().density);
    }
}
