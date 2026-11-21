package io.github.ouse.ouserdp;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Bundle;
import android.os.Build;
import android.view.Gravity;
import android.view.PointerIcon;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.PopupMenu;

import com.iiordanov.bVNC.RemoteCanvasActivity;

public final class SessionActivity extends RemoteCanvasActivity {
    private ImageButton controls;

    @Override
    protected boolean usesCompactSessionUi() {
        return true;
    }

    @Override
    protected boolean capturesExtendedShortcuts() {
        return true;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        FrameLayout canvas = (FrameLayout) getCanvas().getParent();
        controls = new ImageButton(this);
        controls.setImageResource(R.drawable.ouse_settings_icon);
        controls.setContentDescription(getString(R.string.ouse_settings));
        controls.setFocusable(false);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            PointerIcon hidden = PointerIcon.getSystemIcon(this, PointerIcon.TYPE_NULL);
            getWindow().getDecorView().setPointerIcon(hidden);
            canvas.setPointerIcon(hidden);
            getCanvas().setPointerIcon(hidden);
            controls.setPointerIcon(hidden);
        }

        GradientDrawable background = new GradientDrawable();
        background.setColor(0xCC172231);
        background.setCornerRadius(dp(22));
        controls.setBackground(new RippleDrawable(ColorStateList.valueOf(0x55FFFFFF), background, null));
        controls.setOnClickListener(view -> showControls());

        canvas.addView(controls, new FrameLayout.LayoutParams(dp(44), dp(44), Gravity.TOP | Gravity.START));
        new SessionControlsPosition(canvas, controls, dp(12));
    }

    private void showControls() {
        PopupMenu menu = new PopupMenu(this, controls);
        menu.getMenu().add(R.string.ouse_settings)
                .setOnMenuItemClickListener(item -> {
                    startActivity(new Intent(this, SettingsActivity.class));
                    return true;
                });
        menu.getMenu().add(R.string.ouse_windows_keyboard)
                .setOnMenuItemClickListener(item -> {
                    showRemoteWindowsKeyboard();
                    return true;
                });
        menu.getMenu().add(R.string.ouse_send_alt_f4)
                .setOnMenuItemClickListener(item -> {
                    sendRemoteAltF4();
                    return true;
                });
        menu.getMenu().add(R.string.ouse_disconnect)
                .setOnMenuItemClickListener(item -> {
                    finish();
                    return true;
                });
        menu.show();
    }

    @Override
    public void onBackPressed() {
        showControls();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
