package com.undatech.opaque;

/** Bounded retries; a successful connection resets the backoff. */
public final class ReconnectBackoff {
    private int attempts;
    private boolean connectedBefore;
    private boolean stopped;
    public synchronized void connected() { connectedBefore = true; attempts = 0; }
    public synchronized int nextDelayMillis() {
        if (stopped || !connectedBefore || attempts >= 8) return -1;
        int delay = (int) Math.min(30_000L, 1_000L << attempts);
        attempts++;
        return delay;
    }
    public synchronized int attempts() { return attempts; }
    public synchronized boolean connectedBefore() { return connectedBefore; }
    public synchronized void restart() { stopped = false; connectedBefore = true; attempts = 0; }
    public synchronized void stop() { stopped = true; }
}
