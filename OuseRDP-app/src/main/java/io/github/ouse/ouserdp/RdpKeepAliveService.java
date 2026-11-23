package io.github.ouse.ouserdp;

import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.net.*;
import android.net.wifi.WifiManager;
import android.os.*;
import androidx.core.app.NotificationCompat;
import com.undatech.opaque.RdpCommunicator;
import java.lang.ref.WeakReference;

/** Keeps one user-started session alive; notification Stop is an explicit disconnect. */
public final class RdpKeepAliveService extends Service {
    private static final String CHANNEL = "rdp_session";
    private static final String STOP = "io.github.ouse.ouserdp.STOP_SESSION";
    private static final int NOTIFICATION = 100;
    private static volatile OwnedSession owned;
    private static volatile RdpKeepAliveService running;
    private static String status = "正在连接远程桌面";
    private PowerManager.WakeLock wakeLock;
    private WifiManager.WifiLock wifiLock;
    private ConnectivityManager connectivity;
    private ConnectivityManager.NetworkCallback networkCallback;
    private OwnedSession activeOwner;
    private BroadcastReceiver screenReceiver;
    private boolean screenOn = true;

    private static final class OwnedSession {
        final RdpCommunicator backend;
        final Runnable close;
        final Intent resume;
        WeakReference<SessionActivity> activity;
        boolean visible = true;
        RdpCommunicator.ConnectionState state = RdpCommunicator.ConnectionState.CONNECTING;
        OwnedSession(SessionActivity ui, RdpCommunicator backend) {
            this.backend = backend;
            this.close = ui.getRemoteConnection()::closeConnection;
            this.resume = new Intent(ui.getIntent()).setClass(ui, SessionActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            this.activity = new WeakReference<>(ui);
        }
    }
    public static boolean owns(RdpCommunicator backend) { return owned != null && owned.backend == backend; }
    public static void attach(SessionActivity ui, RdpCommunicator backend) {
        OwnedSession current = owned;
        if (current == null || current.backend != backend) {
            if (current != null) { current.backend.setConnectionStateListener(null); current.close.run(); }
            current = new OwnedSession(ui, backend);
            owned = current;
        } else current.activity = new WeakReference<>(ui);
        OwnedSession target = current;
        backend.setConnectionStateListener((state, attempt, delay) -> {
            if (owned != target) return;
            target.state = state;
            status = SessionActivity.stateText(state, attempt, delay);
            RdpKeepAliveService service = running;
            if (service != null) service.updateLocks();
            if (service != null) ((NotificationManager) service.getSystemService(NOTIFICATION_SERVICE)).notify(NOTIFICATION, service.notification());
            SessionActivity activity = target.activity.get();
            if (activity != null && !activity.isDestroyed()) activity.showConnectionState(state, attempt, delay);
        });
        Intent intent = new Intent(ui, RdpKeepAliveService.class);
        if (Build.VERSION.SDK_INT >= 26) ui.startForegroundService(intent); else ui.startService(intent);
    }
    static void setVisible(RdpCommunicator backend, boolean visible) {
        OwnedSession current = owned;
        if (current != null && current.backend == backend) {
            current.visible = visible;
            RdpKeepAliveService service = running;
            if (service != null) service.updateLocks();
        }
    }
    private void updateLocks() {
        OwnedSession current = owned;
        boolean active = current != null && switch (current.state) {
            case CONNECTING, CONNECTED, RECONNECTING, WAITING_FOR_NETWORK -> true;
            default -> false;
        };
        boolean wifiBoost = active && current.visible && screenOn
                && current.state != RdpCommunicator.ConnectionState.WAITING_FOR_NETWORK;
        if (wakeLock != null) {
            if (active && !wakeLock.isHeld()) wakeLock.acquire();
            else if (!active && wakeLock.isHeld()) wakeLock.release();
        }
        if (wifiLock != null) {
            if (wifiBoost && !wifiLock.isHeld()) wifiLock.acquire();
            else if (!wifiBoost && wifiLock.isHeld()) wifiLock.release();
        }
    }
    /** Disable keepalive while leaving a visible activity's connection in place. */
    public static void detach(Context context, RdpCommunicator backend) {
        if (owns(backend)) {
            owned = null;
            backend.setConnectionStateListener(null);
            context.stopService(new Intent(context, RdpKeepAliveService.class));
        }
    }
    private Notification notification() {
        OwnedSession current = owned;
        Intent resume = current != null ? current.resume : new Intent(this, ConnectionsActivity.class);
        PendingIntent open = PendingIntent.getActivity(this, 0, resume, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        PendingIntent stop = PendingIntent.getService(this, 1, new Intent(this, RdpKeepAliveService.class).setAction(STOP), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new NotificationCompat.Builder(this, CHANNEL).setSmallIcon(R.drawable.ouse_settings_icon)
                .setContentTitle("OuseRDP · 后台保持连接").setContentText(status)
                .setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true)
                .addAction(0, "返回桌面", open).addAction(0, "断开连接", stop).build();
    }
    @Override public void onCreate() {
        super.onCreate(); running = this;
        if (Build.VERSION.SDK_INT >= 26) ((NotificationManager) getSystemService(NOTIFICATION_SERVICE))
                .createNotificationChannel(new NotificationChannel(CHANNEL, "远程桌面连接", NotificationManager.IMPORTANCE_LOW));
    }
    @Override public int onStartCommand(Intent intent, int flags, int id) {
        if (intent != null && STOP.equals(intent.getAction())) {
            OwnedSession current = owned; owned = null;
            if (current != null) {
                current.backend.setConnectionStateListener(null); current.close.run();
                SessionActivity activity = current.activity.get(); if (activity != null) activity.finish();
            }
            stopSelf(); return START_NOT_STICKY;
        }
        if (owned == null) { stopSelf(); return START_NOT_STICKY; }
        activeOwner = owned;
        if (Build.VERSION.SDK_INT >= 29) startForeground(NOTIFICATION, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE);
        else startForeground(NOTIFICATION, notification());
        if (wakeLock == null) {
            wakeLock = ((PowerManager) getSystemService(POWER_SERVICE)).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "OuseRDP:session");
            wakeLock.setReferenceCounted(false);
            WifiManager wifi = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
            if (wifi != null) { wifiLock = wifi.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "OuseRDP:session"); wifiLock.setReferenceCounted(false); }
            screenOn = ((PowerManager) getSystemService(POWER_SERVICE)).isInteractive();
            screenReceiver = new BroadcastReceiver() {
                @Override public void onReceive(Context context, Intent intent) {
                    screenOn = Intent.ACTION_SCREEN_ON.equals(intent.getAction());
                    OwnedSession current = owned;
                    if (current != null) current.backend.setSessionVisible(screenOn && current.visible);
                    updateLocks();
                }
            };
            IntentFilter screenFilter = new IntentFilter(Intent.ACTION_SCREEN_ON);
            screenFilter.addAction(Intent.ACTION_SCREEN_OFF);
            if (Build.VERSION.SDK_INT >= 33) registerReceiver(screenReceiver, screenFilter, Context.RECEIVER_NOT_EXPORTED);
            else registerReceiver(screenReceiver, screenFilter);
            connectivity = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
            networkCallback = new ConnectivityManager.NetworkCallback() {
                @Override public void onAvailable(Network network) { OwnedSession current = owned; if (current != null) current.backend.onNetworkAvailable(); }
            };
            connectivity.registerNetworkCallback(new NetworkRequest.Builder().addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build(), networkCallback);
        }
        updateLocks();
        return START_NOT_STICKY;
    }
    @Override public void onTaskRemoved(Intent rootIntent) {
        OwnedSession current = owned; owned = null;
        if (current != null) { current.backend.setConnectionStateListener(null); current.close.run(); }
        stopSelf();
    }
    @Override public void onDestroy() {
        if (running == this) running = null;
        if (connectivity != null && networkCallback != null) {
            try { connectivity.unregisterNetworkCallback(networkCallback); }
            catch (IllegalArgumentException ignored) { /* Callback already removed. */ }
        }
        if (screenReceiver != null) unregisterReceiver(screenReceiver);
        if (wifiLock != null && wifiLock.isHeld()) wifiLock.release();
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        OwnedSession current = owned;
        if (current != null && current == activeOwner) {
            owned = null; current.backend.setConnectionStateListener(null); current.close.run();
            SessionActivity activity = current.activity.get();
            if (activity != null && !activity.isDestroyed() && !activity.isFinishing())
                activity.showConnectionState(RdpCommunicator.ConnectionState.DISCONNECTED, 0, 0);
        }
        super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent) { return null; }
}
