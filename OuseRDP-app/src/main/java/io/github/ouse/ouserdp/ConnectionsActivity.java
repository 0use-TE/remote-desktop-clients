package io.github.ouse.ouserdp;

import android.content.Context;
import android.content.Intent;
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

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.iiordanov.bVNC.ConnectionBean;
import com.iiordanov.bVNC.Constants;
import com.iiordanov.bVNC.OuseDesktopPreferences;
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

        MaterialAlertDialogBuilder dialogBuilder = new MaterialAlertDialogBuilder(this)
                .setTitle(adding ? R.string.ouse_add_computer : R.string.ouse_edit_computer)
                .setView(form)
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
            if (!adding && (!host.equalsIgnoreCase(existing.getAddress()) || port != existing.getPort())) {
                connection.setX509KeySignature("");
            }
            connection.setAddress(host);
            connection.setPort(port);
            connection.setNickname(endpoint(connection));
            connection.setUserName(user);
            connection.setRdpDomain(domainSeparator < 0 ? "" : enteredAccount.substring(0, domainSeparator));
            applyDesktopDefaults(connection);
            connection.save(this);
            dialog.dismiss();
            refreshConnections();
            if (adding) {
                requestPassword(connection);
            }
        });
    }

    private void requestPassword(ConnectionBean connection) {
        LinearLayout form = dialogForm();
        EditText password = field(R.string.ouse_password, InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        form.addView(password);
        new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.ouse_connect_to, endpoint(connection)))
                .setView(form)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.ouse_connect, (dialog, which) -> {
                    applyDesktopDefaults(connection);
                    connection.setPassword(password.getText().toString());
                    getPreferences(Context.MODE_PRIVATE).edit().putString(LAST_CONNECTION, connection.getId()).apply();
                    Intent intent = new Intent(this, SessionActivity.class);
                    intent.putExtra(Utils.getConnectionString(this), connection.Gen_getValues());
                    connection.setPassword("");
                    startActivity(intent);
                })
                .show();
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
