package io.github.ouse.ouserdp;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Bundle;
import android.os.Build;
import android.view.Gravity;
import android.view.PointerIcon;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.graphics.Rect;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.view.View;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import com.iiordanov.bVNC.Constants;

import com.iiordanov.bVNC.RemoteCanvasActivity;
import com.iiordanov.bVNC.WindowsKeyCaptureService;
import com.iiordanov.bVNC.input.NativeTouchInput;
import com.undatech.opaque.RdpCommunicator;

public final class SessionActivity extends RemoteCanvasActivity {
    private ImageButton controls;
    private NativeTouchInput nativeTouch;
    private TextView connectionStatus;
    private TextView keyboardStatus;
    private KeyboardServiceMonitor keyboardHealth;
    private final Handler sessionHandler = new Handler(Looper.getMainLooper());
    private RdpCommunicator attachedBackend;
    private final Runnable attachSession = new Runnable() {
        @Override public void run() {
            if (isFinishing() || isDestroyed()) return;
            RdpCommunicator backend = rdp();
            if (backend == null) { sessionHandler.postDelayed(this, 250); return; }
            attachedBackend = backend;
            if (getSharedPreferences(Constants.generalSettingsTag, Context.MODE_PRIVATE).getBoolean("ouseBackgroundConnection", true)) {
                try { RdpKeepAliveService.attach(SessionActivity.this, backend); }
                catch (RuntimeException error) { android.util.Log.e("OuseSession", "Unable to start foreground session", error); RdpKeepAliveService.detach(SessionActivity.this, backend); backend.setConnectionStateListener(SessionActivity.this::showConnectionState); }
            } else {
                RdpKeepAliveService.detach(SessionActivity.this, backend);
                backend.setConnectionStateListener(SessionActivity.this::showConnectionState);
            }
        }
    };

    static String stateText(RdpCommunicator.ConnectionState state, int attempt, int delay) {
        return switch (state) {
            case CONNECTED -> "已连接";
            case CONNECTING -> "正在连接远程桌面";
            case WAITING_FOR_NETWORK -> "等待网络恢复";
            case RECONNECTING -> delay > 0 ? "连接中断，" + Math.max(1, delay / 1000) + " 秒后重连（" + attempt + "/8）" : "正在重新连接（" + attempt + "/8）";
            case RETRY_FAILED -> "暂时无法连接，点击此处重试";
            case DISCONNECTED -> "连接已断开";
        };
    }
    void showConnectionState(RdpCommunicator.ConnectionState state, int attempt, int delay) {
        if (isDestroyed() || connectionStatus == null) return;
        connectionStatus.setText(stateText(state, attempt, delay));
        connectionStatus.setVisibility(state == RdpCommunicator.ConnectionState.CONNECTED ? View.GONE : View.VISIBLE);
    }

    private RdpCommunicator rdp() {
        return getRemoteConnection() != null && getRemoteConnection().getRfbConn() instanceof RdpCommunicator rdp ? rdp : null;
    }

    @Override
    protected boolean usesCompactSessionUi() {
        return true;
    }

    @Override
    protected boolean capturesExtendedShortcuts() {
        return true;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        keyboardHealth = new KeyboardServiceMonitor(this);
        nativeTouch = new NativeTouchInput(new NativeTouchInput.Sink() {
            public boolean ready() { RdpCommunicator rdp = rdp(); return rdp != null && rdp.isNativeTouchReady(); }
            public void send(int action, int id, int x, int y) { RdpCommunicator rdp = rdp(); if (rdp != null) rdp.writeTouchEvent(action, id, x, y); }
            public void cancel() { RdpCommunicator rdp = rdp(); if (rdp != null) rdp.cancelTouches(); }
        });
        FrameLayout canvas = (FrameLayout) getCanvas().getParent();
        controls = new ImageButton(this);
        controls.setImageResource(R.drawable.ouse_settings_icon);
        controls.setContentDescription(getString(R.string.ouse_settings));
        controls.setFocusable(false);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            PointerIcon hidden = PointerIcon.getSystemIcon(this, PointerIcon.TYPE_NULL);
            getWindow().getDecorView().setPointerIcon(hidden);
            canvas.setPointerIcon(hidden);
            getCanvas().setPointerIcon(hidden);
            controls.setPointerIcon(hidden);
        }

        GradientDrawable background = new GradientDrawable();
        background.setColor(0xCC172231);
        background.setCornerRadius(dp(22));
        controls.setBackground(new RippleDrawable(ColorStateList.valueOf(0x55FFFFFF), background, null));
        controls.setOnClickListener(view -> showControls());

        canvas.addView(controls, new FrameLayout.LayoutParams(dp(44), dp(44), Gravity.TOP | Gravity.START));
        new SessionControlsPosition(canvas, controls, dp(12));
        connectionStatus = new TextView(this);
        connectionStatus.setTextSize(20);
        connectionStatus.setTextColor(0xFFFFFFFF);
        connectionStatus.setBackgroundColor(0xE6172231);
        connectionStatus.setPadding(dp(20), dp(12), dp(20), dp(12));
        connectionStatus.setText("正在连接远程桌面");
        connectionStatus.setOnClickListener(view -> { RdpCommunicator backend = rdp(); if (backend != null) backend.retryConnectionNow(); });
        canvas.addView(connectionStatus, new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL));
        keyboardStatus = new TextView(this);
        keyboardStatus.setTextSize(20);
        keyboardStatus.setTextColor(0xFFFFFFFF);
        keyboardStatus.setBackgroundColor(0xE6172231);
        keyboardStatus.setPadding(dp(20), dp(12), dp(20), dp(12));
        keyboardStatus.setText("键盘：安卓 · Ctrl+Alt+F12 返回 Windows");
        keyboardStatus.setVisibility(View.GONE);
        canvas.addView(keyboardStatus, new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.CENTER_HORIZONTAL));
        sessionHandler.post(attachSession);
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 190);
    }

    private void showControls() {
        if (nativeTouch != null) nativeTouch.cancel();
        PopupMenu menu = new PopupMenu(this, controls);
        menu.getMenu().add(WindowsKeyCaptureService.isAndroidKeyboardMode(this)
                ? "键盘：安卓（Ctrl+Alt+F12 切换）" : "键盘：Windows（Ctrl+Alt+F12 切换）").setEnabled(false);
        RdpCommunicator rdp = rdp();
        menu.getMenu().add("立即重连").setEnabled(rdp != null && !rdp.isInNormalProtocol()).setOnMenuItemClickListener(item -> { rdp.retryConnectionNow(); return true; });
        menu.getMenu().add(rdp != null && rdp.isNativeTouchReady() ? "原生触摸：已连接" : "原生触摸：未连接，使用兼容操作").setEnabled(false);
        menu.getMenu().add(R.string.ouse_settings)
                .setOnMenuItemClickListener(item -> {
                    startActivity(new Intent(this, SettingsActivity.class));
                    return true;
                });
        menu.getMenu().add(R.string.ouse_windows_keyboard)
                .setOnMenuItemClickListener(item -> {
                    showRemoteWindowsKeyboard();
                    return true;
                });
        menu.getMenu().add(R.string.ouse_send_alt_f4)
                .setOnMenuItemClickListener(item -> {
                    sendRemoteAltF4();
                    return true;
                });
        menu.getMenu().add(R.string.ouse_disconnect)
                .setOnMenuItemClickListener(item -> {
                    finish();
                    return true;
                });
        menu.show();
    }

    @Override
    public void onBackPressed() {
        showControls();
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (nativeTouch != null && getCanvas() != null) {
            boolean finger = event.isFromSource(InputDevice.SOURCE_TOUCHSCREEN)
                    && event.getToolType(0) == MotionEvent.TOOL_TYPE_FINGER;
            if (finger) {
                Rect area = new Rect();
                Rect button = new Rect();
                getCanvas().getGlobalVisibleRect(area);
                if (controls != null) controls.getGlobalVisibleRect(button);
                boolean inside = area.contains((int) event.getRawX(), (int) event.getRawY())
                        && !button.contains((int) event.getRawX(), (int) event.getRawY());
                if (nativeTouch.isActive() || (event.getActionMasked() == MotionEvent.ACTION_DOWN && inside)) {
                    int[] location = new int[2];
                    getCanvas().getLocationOnScreen(location);
                    MotionEvent local = MotionEvent.obtain(event);
                    local.offsetLocation(event.getRawX() - event.getX() - location[0], event.getRawY() - event.getY() - location[1]);
                    RdpCommunicator rdp = rdp();
                    boolean handled = rdp != null && nativeTouch.onTouch(local, getCanvas().getZoomFactor(),
                            getCanvas().getAbsX(), getCanvas().getAbsY(), rdp.framebufferWidth(), rdp.framebufferHeight());
                    local.recycle();
                    if (handled) { getCanvas().setNativeTouchMode(true); return true; }
                }
            } else if (event.isFromSource(InputDevice.SOURCE_MOUSE)) {
                nativeTouch.cancel();
                getCanvas().setNativeTouchMode(false);
            }
        }
        return super.dispatchTouchEvent(event);
    }

    @Override
    public boolean dispatchGenericMotionEvent(MotionEvent event) {
        if (getCanvas() != null && event.isFromSource(InputDevice.SOURCE_MOUSE)) {
            if (nativeTouch != null) nativeTouch.cancel();
            getCanvas().setNativeTouchMode(false);
        }
        return super.dispatchGenericMotionEvent(event);
    }

    @Override
    protected void onPause() {
        if (keyboardHealth != null) keyboardHealth.stop();
        if (nativeTouch != null) nativeTouch.cancel();
        super.onPause();
    }

    @Override protected void onResume() {
        super.onResume();
        onKeyboardRoutingChanged(WindowsKeyCaptureService.isAndroidKeyboardMode(this));
        if (keyboardHealth != null) keyboardHealth.start();
        sessionHandler.removeCallbacks(attachSession);
        sessionHandler.post(attachSession);
    }

    @Override protected void onKeyboardRoutingChanged(boolean androidMode) {
        if (keyboardStatus != null) keyboardStatus.setVisibility(androidMode ? View.VISIBLE : View.GONE);
    }

    @Override protected boolean keepConnectionAfterActivityDestroyed() {
        return !isFinishing() && attachedBackend != null && RdpKeepAliveService.owns(attachedBackend);
    }

    @Override protected void onDestroy() {
        if (keyboardHealth != null) keyboardHealth.stop();
        sessionHandler.removeCallbacksAndMessages(null);
        if (attachedBackend != null && isFinishing()) RdpKeepAliveService.detach(this, attachedBackend);
        if (attachedBackend != null && !RdpKeepAliveService.owns(attachedBackend)) attachedBackend.setConnectionStateListener(null);
        super.onDestroy();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (!hasFocus && nativeTouch != null) nativeTouch.cancel();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
