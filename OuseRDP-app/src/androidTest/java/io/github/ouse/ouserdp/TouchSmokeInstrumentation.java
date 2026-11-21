package io.github.ouse.ouserdp;

import android.app.Instrumentation;
import android.os.Bundle;
import com.freerdp.freerdpcore.services.LibFreeRDP;
import com.undatech.opaque.NativeRdpTouch;

/** Device-only JNI/ABI and lifecycle check, without connecting to a remote computer. */
public final class TouchSmokeInstrumentation extends Instrumentation {
    @Override public void onCreate(Bundle arguments) { super.onCreate(arguments); start(); }
    @Override public void onStart() {
        Bundle result = new Bundle();
        long instance = 0;
        boolean attached = false;
        try {
            instance = LibFreeRDP.newInstance(getTargetContext());
            if (instance == 0) throw new AssertionError("FreeRDP instance creation failed");
            attached = NativeRdpTouch.attach(instance);
            if (!attached) throw new AssertionError("RDPEI JNI attach failed");
            if (!NativeRdpTouch.attach(instance)) throw new AssertionError("Attach must be idempotent");
            if (NativeRdpTouch.ready(instance)) throw new AssertionError("Unconnected channel reported ready");
            if (NativeRdpTouch.send(instance, 0, 0, 10, 10)) throw new AssertionError("Unconnected channel accepted touch");
            if (NativeRdpTouch.send(instance, 0, 32, 10, 10)) throw new AssertionError("Invalid contact accepted");
            NativeRdpTouch.cancel(instance);
            NativeRdpTouch.detach(instance); attached = false;
            NativeRdpTouch.detach(instance);
            if (NativeRdpTouch.ready(instance)) throw new AssertionError("Detached channel reported ready");
            result.putString("result", "PASS: JNI loading, FreeRDP ABI, attach/detach, unavailable-channel and invalid-contact guards");
            finish(-1, result);
        } catch (Throwable error) {
            result.putString("result", "FAIL: " + android.util.Log.getStackTraceString(error));
            finish(0, result);
        } finally {
            if (instance != 0) { if (attached) NativeRdpTouch.detach(instance); LibFreeRDP.freeInstance(instance); }
        }
    }
}
