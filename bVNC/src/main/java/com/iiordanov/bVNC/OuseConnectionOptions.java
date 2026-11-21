package com.iiordanov.bVNC;

import android.content.Context;
import android.content.SharedPreferences;

public final class OuseConnectionOptions {
    public final boolean clipboard;
    public final boolean sound;
    public final boolean microphone;

    private OuseConnectionOptions(boolean clipboard, boolean sound, boolean microphone) {
        this.clipboard = clipboard;
        this.sound = sound;
        this.microphone = microphone;
    }

    public static OuseConnectionOptions load(Context context, String profile) {
        SharedPreferences settings = preferences(context);
        return new OuseConnectionOptions(
                settings.getBoolean(profile + ":clipboard", true),
                settings.getBoolean(profile + ":sound", false),
                settings.getBoolean(profile + ":microphone", false));
    }

    public static void save(Context context, String profile, boolean clipboard, boolean sound,
                            boolean microphone) {
        preferences(context).edit().putBoolean(profile + ":clipboard", clipboard)
                .putBoolean(profile + ":sound", sound)
                .putBoolean(profile + ":microphone", microphone).apply();
    }

    public static void remove(Context context, String profile) {
        preferences(context).edit().remove(profile + ":clipboard")
                .remove(profile + ":sound").remove(profile + ":microphone").apply();
    }

    private static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences("ouseConnectionOptions", Context.MODE_PRIVATE);
    }
}
