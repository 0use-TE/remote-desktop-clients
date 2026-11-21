package com.iiordanov.bVNC.input;

/** Converts fractional movement to wheel notches without queuing oversized bursts. */
final class ScrollSteps {
    private float fraction;

    int take(float movement) {
        if (movement == 0 || Float.isNaN(movement) || Float.isInfinite(movement)) {
            return 0;
        }
        if (fraction * movement < 0) {
            reset();
        }
        float total = fraction + movement;
        fraction = total % 1f;
        return Math.max(-2, Math.min(2, (int) total));
    }

    void reset() {
        fraction = 0;
    }
}
