package io.github.ouse.ouserdp;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.PopupMenu;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.iiordanov.bVNC.RemoteCanvasActivity;

public final class SessionActivity extends RemoteCanvasActivity {
    private ImageButton controls;

    @Override
    protected boolean usesCompactSessionUi() {
        return true;
    }

    @Override
    protected boolean capturesAltTab() {
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

        GradientDrawable background = new GradientDrawable();
        background.setColor(0xCC172231);
        background.setCornerRadius(dp(22));
        controls.setBackground(new RippleDrawable(ColorStateList.valueOf(0x55FFFFFF), background, null));
        controls.setOnClickListener(view -> showControls());

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(dp(44), dp(44),
                Gravity.TOP | Gravity.END);
        params.topMargin = dp(12);
        params.setMarginEnd(dp(12));
        canvas.addView(controls, params);
        ViewCompat.setOnApplyWindowInsetsListener(controls, (view, insets) -> {
            Insets cutout = insets.getInsets(WindowInsetsCompat.Type.displayCutout());
            boolean rtl = view.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;
            int top = dp(12) + cutout.top;
            int end = dp(12) + (rtl ? cutout.left : cutout.right);
            if (params.topMargin != top || params.getMarginEnd() != end) {
                params.topMargin = top;
                params.setMarginEnd(end);
                view.setLayoutParams(params);
            }
            return insets;
        });
        ViewCompat.requestApplyInsets(controls);
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
