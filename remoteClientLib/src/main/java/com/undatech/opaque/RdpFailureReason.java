package com.undatech.opaque;

/** FreeRDP 2.x error classes. Keep retry policy separate from UI wording. */
public final class RdpFailureReason {
    public enum Kind { TRANSPORT, AUTHENTICATION, SERVER_ENDED }
    private RdpFailureReason() {}
    public static Kind classify(long packed) {
        int error = (int) (packed >>> 32);
        int info = (int) packed;
        if (info == 0 && (error >>> 16) == 1) info = error & 0xffff;
        if ((error >>> 16) == 2) {
            int code = error & 0xffff;
            if (code == 9 || code == 10 || code == 14 || code == 15 || code == 16
                    || (code >= 18 && code <= 27)) return Kind.AUTHENTICATION;
        }
        if (info == 7 || info == 9 || info == 10) return Kind.AUTHENTICATION;
        if (info == 2 || info == 3 || info == 5 || info == 11 || info == 12)
            return Kind.SERVER_ENDED;
        return Kind.TRANSPORT;
    }
}
