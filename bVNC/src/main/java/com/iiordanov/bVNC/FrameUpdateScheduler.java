package com.iiordanov.bVNC;

import android.graphics.Rect;

/** Merge all dirty regions received before the next display frame. */
final class FrameUpdateScheduler {
    interface Target {
        void postFrame(Runnable callback);
        void invalidate(Rect bounds);
    }
    private final Target target;
    private final Rect pending = new Rect();
    private boolean scheduled;
    private long generation;

    FrameUpdateScheduler(Target target) { this.target = target; }

    void request(Rect bounds) {
        if (bounds == null || bounds.isEmpty()) return;
        long ticket;
        synchronized (this) {
            pending.union(bounds);
            if (scheduled) return;
            scheduled = true;
            ticket = generation;
        }
        target.postFrame(() -> flush(ticket));
    }

    private void flush(long ticket) {
        Rect bounds;
        synchronized (this) {
            if (ticket != generation || !scheduled) return;
            bounds = new Rect(pending);
            pending.setEmpty();
            scheduled = false;
        }
        target.invalidate(bounds);
    }

    synchronized void clear() {
        generation++;
        scheduled = false;
        pending.setEmpty();
    }

    static Rect viewBounds(float x, float y, float w, float h, float shiftX, float shiftY, float scale) {
        if (w <= 0 || h <= 0 || scale <= 0 || Float.isNaN(x + y + w + h + shiftX + shiftY + scale)
                || Float.isInfinite(x + y + w + h + shiftX + shiftY + scale)) return new Rect();
        // Round outwards: thin updates and fractional zoom must not leave stale edge pixels.
        return new Rect((int) Math.floor((x - shiftX - 1) * scale),
                (int) Math.floor((y - shiftY - 1) * scale),
                (int) Math.ceil((x - shiftX + w + 1) * scale),
                (int) Math.ceil((y - shiftY + h + 1) * scale));
    }
}
