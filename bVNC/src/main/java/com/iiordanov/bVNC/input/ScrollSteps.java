package com.iiordanov.bVNC.input;

/** Converts wheel notches to high-resolution RDP units (120 units per notch). */
final class ScrollSteps {
    private float fraction;

    int take(float movement) {
        if (movement == 0 || Float.isNaN(movement) || Float.isInfinite(movement)) {
            return 0;
        }
        if (fraction * movement < 0) {
            reset();
        }
        float total = fraction + movement * 120f;
        fraction = total % 1f;
        return (int) Math.max(-240f, Math.min(240f, total));
    }

    void reset() {
        fraction = 0;
    }
}
