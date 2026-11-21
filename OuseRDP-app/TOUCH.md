# Native touch in OuseRDP 0.1.8

Touchscreen finger input is sent over the standard RDPEI dynamic channel through the bundled FreeRDP 2.11.7 core. `NativeRdpTouch.attach` enables MultiTouchInput before FreeRDP parses connection options / loads add-ins, and subscribes to channel lifecycle events. Mouse and keyboard handling are preserved. The local software cursor is hidden after a native touch gesture and restored by mouse/trackpad input.

The session menu reports whether the native touch channel is connected. Until the server opens that channel, the existing compatibility gestures remain available. A gesture never switches from compatibility to native touch partway through. Coordinates account for canvas scaling and panning; contact IDs remain stable. Cancellation, lost focus and pause release active contacts.

## Native bridge build

Existing prebuilt FreeRDP libraries and matching headers must first be installed by the repository's dependency workflow. With an Android NDK installed, run:

```powershell
.\remoteClientLib\src\main\cpp\build-touch.ps1 -NdkRoot C:\path\to\android-ndk
.\gradlew.bat :bVNC:testDebugUnitTest :OuseRDP-app:assembleDebug
```

The script emits `libouse-rdpei.so` for all four ABIs into the existing ignored `remoteClientLib/src/main/jniLibs` build directory; it links against the same bundled FreeRDP/WinPR binaries. Rebuild the bridge when changing FreeRDP versions or ABIs. It does not modify ignored FreeRDP Java sources.

## Verification

`NativeTouchInputTest` checks multiple fingers, stable IDs, coordinate conversion/clamping, cancellation and compatibility fallback. `TouchSmokeInstrumentation` exercises JNI loading and native session lifecycle on an Android device without connecting to a remote PC:

```powershell
.\gradlew.bat :OuseRDP-app:assembleDebugAndroidTest
adb install -r OuseRDP-app/build/outputs/apk/androidTest/debug/OuseRDP-app-debug-androidTest.apk
adb shell am instrument -w io.github.ouse.ouserdp.test/io.github.ouse.ouserdp.TouchSmokeInstrumentation
```

End-to-end touch behavior requires a Windows RDP host that opens the RDPEI channel. Test single-finger content scrolling, two-finger pinch in a touch-aware application, lifting fingers in different orders, switching to mouse input, and leaving/re-entering the session while fingers are down. Host/application handling and network/display latency affect the result.
