package com.iiordanov.bVNC.input;

import android.view.MotionEvent;

/** Keeps an entire gesture on one input path, with stable Android contact IDs. */
public final class NativeTouchInput {
    public interface Sink {
        boolean ready();
        void send(int action, int id, int x, int y);
        void cancel();
    }
    private final Sink sink;
    private boolean active;
    public NativeTouchInput(Sink sink) { this.sink = sink; }
    public boolean isActive() { return active; }
    public void cancel() {
        if (active) sink.cancel();
        active = false;
    }
    public boolean onTouch(MotionEvent event, float scale, float offsetX, float offsetY, int width, int height) {
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            cancel();
            active = sink.ready() && scale > 0 && width > 0 && height > 0;
        }
        if (!active) return false;
        int index = event.getActionIndex();
        if (action == MotionEvent.ACTION_CANCEL) {
            cancel();
        } else if (action == MotionEvent.ACTION_MOVE) {
            for (int i = 0; i < event.getPointerCount(); i++) send(1, event, i, scale, offsetX, offsetY, width, height);
        } else if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN) {
            send(0, event, index, scale, offsetX, offsetY, width, height);
        } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_POINTER_UP) {
            send(2, event, index, scale, offsetX, offsetY, width, height);
            if (action == MotionEvent.ACTION_UP) active = false;
        }
        return true;
    }
    private void send(int action, MotionEvent e, int index, float scale, float offsetX, float offsetY, int width, int height) {
        int x = Math.max(0, Math.min(width - 1, Math.round(offsetX + e.getX(index) / scale)));
        int y = Math.max(0, Math.min(height - 1, Math.round(offsetY + e.getY(index) / scale)));
        sink.send(action, e.getPointerId(index), x, y);
    }
}
