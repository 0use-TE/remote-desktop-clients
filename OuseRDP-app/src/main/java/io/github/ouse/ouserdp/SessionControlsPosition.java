package io.github.ouse.ouserdp;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.MotionEvent;
import android.widget.FrameLayout;
import android.widget.ImageButton;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

/** Keeps the settings button inside the usable area and remembers its relative position. */
final class SessionControlsPosition {
    private final FrameLayout canvas;
    private final ImageButton button;
    private final SharedPreferences preferences;
    private final int margin;
    private Insets cutout = Insets.NONE;
    private float positionX;
    private float positionY;
    private float downX;
    private float downY;
    private float startX;
    private float startY;
    private boolean dragging;

    SessionControlsPosition(FrameLayout canvas, ImageButton button, int margin) {
        this.canvas = canvas;
        this.button = button;
        this.margin = margin;
        preferences = canvas.getContext().getSharedPreferences("ouseSessionControls", Context.MODE_PRIVATE);
        positionX = clamp(preferences.getFloat("x", 1));
        positionY = clamp(preferences.getFloat("y", 0));

        canvas.addOnLayoutChangeListener((view, l, t, r, b, oldL, oldT, oldR, oldB) -> place());
        button.addOnLayoutChangeListener((view, l, t, r, b, oldL, oldT, oldR, oldB) -> place());
        ViewCompat.setOnApplyWindowInsetsListener(button, (view, insets) -> {
            cutout = insets.getInsets(WindowInsetsCompat.Type.displayCutout());
            place();
            return insets;
        });
        ViewCompat.requestApplyInsets(button);

        button.setOnLongClickListener(view -> {
            dragging = true;
            return true;
        });
        button.setOnTouchListener((view, event) -> handleTouch(event));
    }

    private boolean handleTouch(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                dragging = false;
                downX = event.getRawX();
                downY = event.getRawY();
                startX = button.getX();
                startY = button.getY();
                break;
            case MotionEvent.ACTION_MOVE:
                if (dragging) {
                    moveTo(startX + event.getRawX() - downX, startY + event.getRawY() - downY);
                    return true;
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (dragging) {
                    preferences.edit().putFloat("x", positionX).putFloat("y", positionY).apply();
                    dragging = false;
                }
                break;
            default:
                break;
        }
        return false;
    }

    private void moveTo(float x, float y) {
        positionX = clamp((x - margin - cutout.left) / Math.max(1, horizontalRoom()));
        positionY = clamp((y - margin - cutout.top) / Math.max(1, verticalRoom()));
        place();
    }

    private void place() {
        if (canvas.getWidth() == 0 || button.getWidth() == 0) {
            return;
        }
        button.setX(margin + cutout.left + positionX * horizontalRoom());
        button.setY(margin + cutout.top + positionY * verticalRoom());
    }

    private int horizontalRoom() {
        return Math.max(0, canvas.getWidth() - button.getWidth() - 2 * margin - cutout.left - cutout.right);
    }

    private int verticalRoom() {
        return Math.max(0, canvas.getHeight() - button.getHeight() - 2 * margin - cutout.top - cutout.bottom);
    }

    private static float clamp(float fraction) {
        return Math.max(0f, Math.min(1f, fraction));
    }
}
