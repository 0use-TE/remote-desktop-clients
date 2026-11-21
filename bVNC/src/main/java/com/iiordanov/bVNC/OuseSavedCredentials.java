package com.iiordanov.bVNC;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.ProviderException;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

public final class OuseSavedCredentials {
    private static final String PROVIDER = "AndroidKeyStore";
    private static final String KEY = "io.github.ouse.ouserdp.connectionPasswords";
    private static final String ALGORITHM = "AES/GCM/NoPadding";

    private OuseSavedCredentials() {}

    public static boolean isSupported() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.M;
    }

    public static boolean hasPassword(Context context, String profile) {
        return isSupported() && preferences(context).contains(profile);
    }

    public static String read(Context context, String profile) {
        if (!isSupported()) {
            return null;
        }
        String encoded = preferences(context).getString(profile, null);
        if (encoded == null) {
            return null;
        }
        try {
            String[] parts = encoded.split(":", 2);
            if (parts.length != 2) {
                return null;
            }
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, key(false),
                    new GCMParameterSpec(128, Base64.decode(parts[0], Base64.NO_WRAP)));
            cipher.updateAAD(profile.getBytes(StandardCharsets.UTF_8));
            byte[] plaintext = cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP));
            try {
                return new String(plaintext, StandardCharsets.UTF_8);
            } finally {
                java.util.Arrays.fill(plaintext, (byte) 0);
            }
        } catch (GeneralSecurityException | IOException | IllegalArgumentException | ProviderException error) {
            return null;
        }
    }

    public static boolean save(Context context, String profile, String password) {
        if (!isSupported() || password == null || password.isEmpty()) {
            return false;
        }
        byte[] plaintext = password.getBytes(StandardCharsets.UTF_8);
        try {
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, key(true));
            cipher.updateAAD(profile.getBytes(StandardCharsets.UTF_8));
            String encoded = Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP) + ":"
                    + Base64.encodeToString(cipher.doFinal(plaintext), Base64.NO_WRAP);
            return preferences(context).edit().putString(profile, encoded).commit();
        } catch (GeneralSecurityException | IOException | ProviderException error) {
            return false;
        } finally {
            java.util.Arrays.fill(plaintext, (byte) 0);
        }
    }

    public static void remove(Context context, String profile) {
        preferences(context).edit().remove(profile).apply();
    }

    private static synchronized SecretKey key(boolean create)
            throws GeneralSecurityException, IOException {
        KeyStore store = KeyStore.getInstance(PROVIDER);
        store.load(null);
        java.security.Key existing = store.getKey(KEY, null);
        if (existing instanceof SecretKey) {
            return (SecretKey) existing;
        }
        if (!create) {
            throw new GeneralSecurityException("Credential key unavailable");
        }
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER);
        KeyGenParameterSpec spec = new KeyGenParameterSpec.Builder(KEY,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build();
        generator.init(spec);
        return generator.generateKey();
    }

    private static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences("ouseSavedCredentials", Context.MODE_PRIVATE);
    }
}
