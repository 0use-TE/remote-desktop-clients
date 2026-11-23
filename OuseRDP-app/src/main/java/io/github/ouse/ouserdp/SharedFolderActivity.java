package io.github.ouse.ouserdp;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.text.format.Formatter;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import com.undatech.opaque.RdpSharedFolder;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class SharedFolderActivity extends AppCompatActivity {
    private File root;
    private String relative = "", pendingImport = "";
    private ArrayList<String> pendingExport = new ArrayList<>();
    private final List<LocalTransferNode> entries = new ArrayList<>();
    private final Set<String> selected = new HashSet<>();
    private final ExecutorService browser = Executors.newSingleThreadExecutor();
    private final EntryAdapter adapter = new EntryAdapter();
    private SharedTransfers transfers;
    private TextView location, empty, progressText;
    private ProgressBar progress;
    private LinearLayout progressArea;
    private MaterialButton addFiles, addFolder, save, up, cancel;
    private int listing;
    private boolean destroyed;
    private boolean previousBusy;
    private final SharedTransfers.Observer observer = this::showProgress;

    private final ActivityResultLauncher<String[]> chooseFiles = registerForActivityResult(
            new ActivityResultContracts.OpenMultipleDocuments(), uris -> {
                if (uris.isEmpty()) return;
                for (Uri uri : uris) remember(uri, false);
                transfers.importFiles(uris, pendingImport, false);
            });
    private final ActivityResultLauncher<Uri> chooseFolder = registerForActivityResult(
            new ActivityResultContracts.OpenDocumentTree(), uri -> {
                if (uri == null) return;
                remember(uri, false);
                transfers.importFiles(java.util.Collections.singletonList(uri), pendingImport, true);
            });
    private final ActivityResultLauncher<Uri> chooseDestination = registerForActivityResult(
            new ActivityResultContracts.OpenDocumentTree(), uri -> {
                if (uri == null || pendingExport.isEmpty()) return;
                remember(uri, true);
                transfers.exportFiles(new ArrayList<>(pendingExport), uri);
                pendingExport.clear();
            });

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        transfers = SharedTransfers.get(this);
        if (state != null) {
            relative = state.getString("folder", ""); pendingImport = state.getString("import", "");
            ArrayList<String> pending = state.getStringArrayList("export");
            if (pending != null) pendingExport = pending;
            ArrayList<String> selection = state.getStringArrayList("selected");
            if (selection != null) selected.addAll(selection);
        }
        try { root = RdpSharedFolder.directory(this); }
        catch (IOException error) { toast(R.string.ouse_shared_unavailable); finish(); return; }
        LinearLayout page = column();
        page.setBackgroundColor(color(R.color.ouse_background));
        ViewCompat.setOnApplyWindowInsetsListener(page, (view, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom); return insets;
        });
        MaterialToolbar toolbar = new MaterialToolbar(this);
        toolbar.setTitle(R.string.ouse_shared_folder);
        toolbar.setTitleTextAppearance(this, R.style.OuseSharedTitle);
        toolbar.setNavigationIcon(R.drawable.ouse_back_icon);
        toolbar.setNavigationContentDescription(R.string.ouse_back);
        toolbar.setNavigationOnClickListener(view -> back());
        page.addView(toolbar, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout actions = column(); actions.setPadding(dp(20), 0, dp(20), dp(8));
        TextView windows = text(20);
        windows.setText(getString(R.string.ouse_shared_windows_path, RdpSharedFolder.WINDOWS_PATH));
        windows.setPadding(0, dp(10), 0, dp(14));
        windows.setTextColor(color(R.color.ouse_primary));
        windows.setOnClickListener(view -> {
            ((ClipboardManager) getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(
                    ClipData.newPlainText("OuseRDP", RdpSharedFolder.WINDOWS_PATH));
            toast(R.string.ouse_shared_path_copied);
        });
        actions.addView(windows);
        LinearLayout importRow = row();
        addFiles = button(R.string.ouse_shared_add_files, () -> {
            pendingImport = relative;
            try { chooseFiles.launch(new String[]{"*/*"}); } catch (android.content.ActivityNotFoundException error) { toast(R.string.ouse_shared_picker_failed); }
        });
        addFolder = button(R.string.ouse_shared_add_folder, () -> {
            pendingImport = relative;
            try { chooseFolder.launch(null); } catch (android.content.ActivityNotFoundException error) { toast(R.string.ouse_shared_picker_failed); }
        });
        addButton(importRow, addFiles); addButton(importRow, addFolder); actions.addView(importRow);
        LinearLayout exportRow = row();
        save = button(R.string.ouse_shared_save, () -> {
            pendingExport = new ArrayList<>(selected);
            try { chooseDestination.launch(null); } catch (android.content.ActivityNotFoundException error) { toast(R.string.ouse_shared_picker_failed); }
        });
        addButton(exportRow, save); addButton(exportRow, button(R.string.ouse_shared_refresh, this::refresh)); actions.addView(exportRow);
        page.addView(actions, new LinearLayout.LayoutParams(-1, -2));

        progressArea = column(); progressArea.setPadding(dp(24), dp(4), dp(24), dp(12));
        progressText = text(20); progressText.setMaxLines(4); progressText.setEllipsize(TextUtils.TruncateAt.END);
        progressArea.addView(progressText);
        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(1000); progressArea.addView(progress, new LinearLayout.LayoutParams(-1, dp(12)));
        cancel = button(R.string.ouse_transfer_cancel, () -> transfers.cancel());
        progressArea.addView(cancel, new LinearLayout.LayoutParams(-2, -2));
        page.addView(progressArea, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout navigation = row(); navigation.setPadding(dp(20), 0, dp(20), 0);
        up = button(R.string.ouse_shared_parent, this::back);
        navigation.addView(up, new LinearLayout.LayoutParams(-2, -2));
        location = text(20); location.setSingleLine(true); location.setEllipsize(TextUtils.TruncateAt.START);
        navigation.addView(location, new LinearLayout.LayoutParams(0, -2, 1));
        page.addView(navigation, new LinearLayout.LayoutParams(-1, -2));

        android.widget.FrameLayout listingArea = new android.widget.FrameLayout(this);
        ListView list = new ListView(this); list.setAdapter(adapter); list.setDividerHeight(dp(1));
        listingArea.addView(list, new android.widget.FrameLayout.LayoutParams(-1, -1));
        empty = text(20); empty.setGravity(Gravity.CENTER); empty.setPadding(dp(24), dp(16), dp(24), dp(16));
        empty.setText(R.string.ouse_shared_empty);
        listingArea.addView(empty, new android.widget.FrameLayout.LayoutParams(-1, -1));
        page.addView(listingArea, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(page); ViewCompat.requestApplyInsets(page);
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() { back(); }
        });
        showProgress(transfers.status());
    }

    @Override protected void onStart() { super.onStart(); if (root != null) { transfers.observe(observer); refresh(); } }
    @Override protected void onStop() { if (transfers != null) transfers.remove(observer); super.onStop(); }
    @Override protected void onDestroy() { destroyed = true; browser.shutdownNow(); super.onDestroy(); }
    @Override protected void onSaveInstanceState(Bundle state) {
        state.putString("folder", relative); state.putString("import", pendingImport);
        state.putStringArrayList("export", pendingExport); state.putStringArrayList("selected", new ArrayList<>(selected));
        super.onSaveInstanceState(state);
    }

    private void remember(Uri uri, boolean write) {
        try { getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                | (write ? Intent.FLAG_GRANT_WRITE_URI_PERMISSION : 0)); }
        catch (SecurityException ignored) { /* Temporary picker grants remain usable for this job. */ }
    }
    private void back() {
        if (relative.isEmpty()) { finish(); return; }
        int separator = relative.lastIndexOf(File.separatorChar);
        relative = separator < 0 ? "" : relative.substring(0, separator);
        selected.clear(); refresh();
    }
    private String relative(LocalTransferNode node) { return node.file.getAbsolutePath().substring(root.getAbsolutePath().length() + 1); }

    private void refresh() {
        if (destroyed || root == null) return;
        int request = ++listing; String folder = relative;
        location.setText(folder.isEmpty() ? getString(R.string.ouse_shared_folder) : folder);
        up.setVisibility(folder.isEmpty() ? View.GONE : View.VISIBLE);
        browser.execute(() -> {
            List<LocalTransferNode> result = new ArrayList<>();
            boolean success = true;
            try {
                for (TransferNode node : SharedTransfers.local(root, folder).children()) result.add((LocalTransferNode) node);
                java.util.Collections.sort(result, (first, second) -> {
                    int directories = Boolean.compare(!first.directory(), !second.directory());
                    return directories == 0 ? first.name().compareToIgnoreCase(second.name()) : directories;
                });
            } catch (IOException | RuntimeException error) { success = false; }
            boolean listed = success;
            runOnUiThread(() -> {
                if (destroyed || request != listing) return;
                entries.clear(); entries.addAll(result);
                Set<String> available = new HashSet<>(); for (LocalTransferNode node : entries) available.add(relative(node));
                selected.retainAll(available);
                adapter.notifyDataSetChanged(); empty.setVisibility(entries.isEmpty() ? View.VISIBLE : View.GONE);
                updateButtons(); if (!listed) toast(R.string.ouse_shared_unavailable);
            });
        });
    }

    private void showProgress(SharedTransfers.Status status) {
        if (progressArea == null || destroyed) return;
        boolean wasBusy = previousBusy; previousBusy = status.busy;
        progressArea.setVisibility(status.message == 0 ? View.GONE : View.VISIBLE);
        if (status.message != 0) {
            String summary = getString(status.message);
            if (status.busy && !status.name.isEmpty()) summary += "：" + status.name;
            if (status.totalFiles > 0 || status.bytes > 0) {
                summary += "\n" + getString(R.string.ouse_transfer_counts, status.files, status.totalFiles);
                summary += " · " + (status.total < 0 ? Formatter.formatShortFileSize(this, status.bytes)
                        : getString(R.string.ouse_transfer_bytes, Formatter.formatShortFileSize(this, status.bytes), Formatter.formatShortFileSize(this, status.total)));
            }
            progressText.setText(summary);
        }
        progress.setVisibility(status.busy ? View.VISIBLE : View.GONE);
        progress.setIndeterminate(status.total <= 0);
        if (status.total > 0) progress.setProgress((int) Math.min(1000, (double) status.bytes * 1000 / status.total));
        cancel.setVisibility(status.busy ? View.VISIBLE : View.GONE);
        updateButtons();
        if (!status.busy && wasBusy) refresh();
    }
    private void updateButtons() {
        boolean busy = transfers.status().busy;
        addFiles.setEnabled(!busy); addFolder.setEnabled(!busy); save.setEnabled(!busy && !selected.isEmpty());
    }

    private final class EntryAdapter extends BaseAdapter {
        @Override public int getCount() { return entries.size(); }
        @Override public Object getItem(int position) { return entries.get(position); }
        @Override public long getItemId(int position) { return position; }
        @Override public View getView(int position, View recycled, ViewGroup parent) {
            LocalTransferNode node = entries.get(position);
            LinearLayout item;
            Holder holder;
            if (recycled == null) {
                item = row(); item.setPadding(dp(20), dp(12), dp(20), dp(12));
                holder = new Holder(); holder.check = new CheckBox(SharedFolderActivity.this); holder.check.setFocusable(false);
                item.addView(holder.check, new LinearLayout.LayoutParams(dp(52), dp(56)));
                LinearLayout labels = column(); holder.name = text(22); holder.name.setSingleLine(true);
                holder.name.setEllipsize(TextUtils.TruncateAt.END); holder.name.setTypeface(null, Typeface.BOLD);
                holder.detail = text(20); holder.detail.setTextColor(color(R.color.ouse_secondary_text));
                labels.addView(holder.name); labels.addView(holder.detail);
                item.addView(labels, new LinearLayout.LayoutParams(0, -2, 1)); item.setTag(holder);
            } else { item = (LinearLayout) recycled; holder = (Holder) item.getTag(); }
            String path = relative(node);
            holder.name.setText(node.name());
            holder.detail.setText(node.directory() ? getString(R.string.ouse_shared_directory) : Formatter.formatShortFileSize(SharedFolderActivity.this, node.size()));
            holder.check.setOnCheckedChangeListener(null); holder.check.setChecked(selected.contains(path));
            holder.check.setContentDescription(node.name());
            holder.check.setOnCheckedChangeListener((button, checked) -> { if (checked) selected.add(path); else selected.remove(path); updateButtons(); });
            CheckBox selection = holder.check;
            item.setOnClickListener(view -> {
                if (node.directory()) { relative = path; selected.clear(); refresh(); }
                else selection.setChecked(!selection.isChecked());
            });
            return item;
        }
    }
    private static final class Holder { CheckBox check; TextView name, detail; }
    private LinearLayout column() { LinearLayout layout = new LinearLayout(this); layout.setOrientation(LinearLayout.VERTICAL); return layout; }
    private LinearLayout row() { LinearLayout layout = new LinearLayout(this); layout.setGravity(Gravity.CENTER_VERTICAL); return layout; }
    private MaterialButton button(int label, Runnable action) {
        MaterialButton button = new MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
        button.setText(label); button.setTextSize(20); button.setOnClickListener(view -> action.run()); return button;
    }
    private void addButton(LinearLayout row, MaterialButton button) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -2, 1); params.setMargins(dp(4), 0, dp(4), 0); row.addView(button, params);
    }
    private TextView text(int size) { TextView text = new TextView(this); text.setTextSize(size); text.setTextColor(color(R.color.ouse_text)); return text; }
    private int color(int color) { return androidx.core.content.ContextCompat.getColor(this, color); }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private void toast(int text) { Toast.makeText(this, text, Toast.LENGTH_SHORT).show(); }
}
