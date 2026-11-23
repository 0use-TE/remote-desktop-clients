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
    private DisplayResizeController displayResize;
    private boolean sessionVisible;
    private ImageClipboardController imageClipboard;
    private androidx.activity.result.ActivityResultLauncher<androidx.activity.result.PickVisualMediaRequest> imagePicker;
    private RdpCommunicator imageTarget;
    private long imageTargetSession;
    private android.hardware.input.InputManager inputDevices;
    private final android.hardware.input.InputManager.InputDeviceListener deviceListener = new android.hardware.input.InputManager.InputDeviceListener() {
        public void onInputDeviceAdded(int id) {}
        public void onInputDeviceChanged(int id) {}
        public void onInputDeviceRemoved(int id) { releaseRemoteInputs(); if (nativeTouch != null) nativeTouch.cancel(); }
    };
    private final Runnable attachSession = new Runnable() {
        @Override public void run() {
            if (isFinishing() || isDestroyed()) return;
            RdpCommunicator backend = rdp();
            if (backend == null) { sessionHandler.postDelayed(this, 250); return; }
            attachedBackend = backend;
            backend.setSessionVisible(sessionVisible);
            updateDisplay();
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
            case AUTHENTICATION_FAILED -> "账号或登录权限有误，请返回修改电脑配置";
            case SERVER_ENDED -> "远端已结束此会话，点击重新连接";
            case DISCONNECTED -> "连接已结束，点击返回电脑列表";
        };
    }
    void showConnectionState(RdpCommunicator.ConnectionState state, int attempt, int delay) {
        if (isDestroyed() || connectionStatus == null) return;
        if (state == RdpCommunicator.ConnectionState.CONNECTED && displayResize != null) {
            displayResize.reconnect();
            updateDisplay();
        }
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
        imageClipboard = new ImageClipboardController(this, message -> {
            if (!isFinishing() && !isDestroyed()) android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_SHORT).show();
        });
        imagePicker = registerForActivityResult(new androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia(), uri -> {
            RdpCommunicator target = imageTarget;
            long instance = imageTargetSession;
            imageTarget = null; imageTargetSession = 0;
            if (uri == null) return;
            if (target == null || target != rdp() || instance == 0 || target.clipboardSession() != instance) {
                android.widget.Toast.makeText(this, R.string.ouse_image_connection_changed, android.widget.Toast.LENGTH_SHORT).show();
                return;
            }
            imageClipboard.copy(uri, target, instance);
        });
        getOnBackPressedDispatcher().addCallback(this, new androidx.activity.OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() { showControls(); }
        });
        keyboardHealth = new KeyboardServiceMonitor(this);
        nativeTouch = new NativeTouchInput(new NativeTouchInput.Sink() {
            public boolean ready() { RdpCommunicator rdp = rdp(); return rdp != null && rdp.isNativeTouchReady(); }
            public void send(int action, int id, int x, int y) { RdpCommunicator rdp = rdp(); if (rdp != null) rdp.writeTouchEvent(action, id, x, y); }
            public void cancel() { RdpCommunicator rdp = rdp(); if (rdp != null) rdp.cancelTouches(); }
        });
        displayResize = new DisplayResizeController(sessionHandler, new DisplayResizeController.Target() {
            public boolean ready() { RdpCommunicator backend = rdp(); return backend != null && backend.isDisplayControlReady(); }
            public void resize(int width, int height, int scale) { RdpCommunicator backend = rdp(); if (backend != null) { releaseRemoteInputs(); nativeTouch.cancel(); backend.requestDesktopSize(width, height, scale); } }
        });
        getCanvas().addOnLayoutChangeListener((view, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
            if (right - left != oldRight - oldLeft || bottom - top != oldBottom - oldTop) updateDisplay();
        });
        inputDevices = (android.hardware.input.InputManager) getSystemService(INPUT_SERVICE);
        inputDevices.registerInputDeviceListener(deviceListener, sessionHandler);
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
        connectionStatus.setOnClickListener(view -> {
            RdpCommunicator backend = rdp();
            if (backend == null) return;
            if (backend.isAuthenticationFailure() || backend.isClosed()) { finish(); return; }
            backend.retryConnectionNow();
        });
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
                ? "键盘：安卓" : "键盘：Windows").setEnabled(false);
        RdpCommunicator rdp = rdp();
        menu.getMenu().add(R.string.ouse_shared_folder).setOnMenuItemClickListener(item -> {
            releaseRemoteInputs();
            startActivity(new Intent(this, SharedFolderActivity.class));
            return true;
        });
        menu.getMenu().add(R.string.ouse_copy_image).setEnabled(rdp != null && rdp.canCopyImage() && !imageClipboard.busy())
                .setOnMenuItemClickListener(item -> {
                    imageTarget = rdp;
                    imageTargetSession = rdp.clipboardSession();
                    releaseRemoteInputs();
                    try {
                        imagePicker.launch(new androidx.activity.result.PickVisualMediaRequest.Builder()
                                .setMediaType(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE).build());
                    } catch (android.content.ActivityNotFoundException error) {
                        imageTarget = null; imageTargetSession = 0;
                        android.widget.Toast.makeText(this, R.string.ouse_image_picker_failed, android.widget.Toast.LENGTH_SHORT).show();
                    }
                    return true;
                });
        menu.getMenu().add("立即重连").setEnabled(rdp != null && !rdp.isInNormalProtocol() && !rdp.isAuthenticationFailure() && !rdp.isClosed()).setOnMenuItemClickListener(item -> { rdp.retryConnectionNow(); return true; });
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

    private void updateDisplay() {
        if (getCanvas() == null || displayResize == null) return;
        int width = getCanvas().getWidth(), height = getCanvas().getHeight();
        RdpCommunicator backend = rdp();
        if (backend != null) backend.recordViewport(width, height);
        if (width < 200 || height < 200) return;
        var display = com.iiordanov.bVNC.OuseDesktopPreferences.displayFor(this, width, height);
        displayResize.update(display.width, display.height, display.scale);
    }

    @Override protected void onStart() {
        super.onStart();
        sessionVisible = true;
        if (getCanvas() != null) getCanvas().setRenderingEnabled(true);
        if (displayResize != null && getSharedPreferences(Constants.generalSettingsTag, MODE_PRIVATE).getBoolean("ouseDynamicResolution", true)) displayResize.start();
        RdpCommunicator backend = rdp();
        if (backend != null) { backend.setSessionVisible(true); RdpKeepAliveService.setVisible(backend, true); }
    }

    @Override protected void onStop() {
        sessionVisible = false;
        if (displayResize != null) displayResize.stop();
        if (getCanvas() != null) getCanvas().setRenderingEnabled(false);
        RdpCommunicator backend = rdp();
        if (backend != null) { backend.setSessionVisible(false); RdpKeepAliveService.setVisible(backend, false); }
        super.onStop();
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
        if (displayResize != null) {
            if (getSharedPreferences(Constants.generalSettingsTag, MODE_PRIVATE).getBoolean("ouseDynamicResolution", true)) displayResize.start();
            else displayResize.stop();
        }
        updateDisplay();
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
        if (imageClipboard != null) imageClipboard.close();
        imageTarget = null;
        if (inputDevices != null) inputDevices.unregisterInputDeviceListener(deviceListener);
        if (displayResize != null) displayResize.stop();
        if (keyboardHealth != null) keyboardHealth.stop();
        sessionHandler.removeCallbacksAndMessages(null);
        if (attachedBackend != null && isFinishing()) RdpKeepAliveService.detach(this, attachedBackend);
        if (attachedBackend != null && !RdpKeepAliveService.owns(attachedBackend)) attachedBackend.setConnectionStateListener(null);
        boolean keeping = keepConnectionAfterActivityDestroyed();
        super.onDestroy();
        if (!keeping && getCanvas() != null) getCanvas().releaseBuffers();
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
