# OuseRDP 0.1.17 — test build

The source is available for testing; no release tag or APK release has been
published. Device installation and acceptance testing are left to the owner.

## Scope

- Adds the app-owned OuseRDP drive and Android file/folder import/export UI.
  Copies stream with progress/cancellation and keep colliding files. See [SHARED_FOLDER.md](SHARED_FOLDER.md).

- Includes the 0.1.15 stability work below, plus single-image offers from Android's
  photo picker to the Windows clipboard. PNG/DIBV5 retain alpha; legacy DIB uses
  a white background. The keyboard menu label is shortened. See [CLIPBOARD.md](CLIPBOARD.md).

- Resolution modes keep their existing values: automatic (long edge <= 2560),
  viewport pixels (long edge <= 8192), or long edge <= 1920. Aspect ratio is
  preserved and dimensions are even. Automatic scaling still accounts for DPI.
- A display-control bridge negotiates server capabilities and sends one primary
  monitor layout after 500 ms of stable layout. Requests respect the advertised
  monitor area and protocol bounds. Capability waiting is bounded to 20 attempts.
  The setting can be disabled. Unsupported servers keep their negotiated size;
  no forced reconnect is used to resize. Returning from settings reapplies the
  selected size and scale when supported. Windows decides how it applies scaling.
- Loss of focus, pause, and device removal release held keys, Unicode input,
  mouse buttons and touch contacts. The input repeat timer and modifier mapper
  are reset. Ctrl+Alt+F12 routing remains unchanged.
- Transport failures retain bounded reconnect/backoff. Network availability
  can restart exhausted transport retries. Credential/account failures stop
  automatic retries; remote logoff, idle timeout or another login ends automatic
  recovery until an explicit reconnect. Server shutdown/disconnect remains
  recoverable. First-connection errors use the session status UI.
- Hidden activities stop local redraw requests. Where supported, the server is
  asked to suppress output; return to the activity reenables output and requests
  a full refresh. The CPU lock remains while a live connection is being kept;
  the high-performance Wi-Fi lock is limited to visible, screen-on use. Terminal
  states release both locks. This is not a promise of survival after process kill.
- Close stops executors and clears the owned global FreeRDP listener. Handler
  ownership is per connection, teardown is idempotent, and closing releases
  displayed and retired buffers. A late old session must not clear a new UI.
- Settings exports bounded metadata (32 recent events, negotiated/view size,
  scale, received-update/copy counts and timing, memory, state, error codes,
  keyboard service health and process exits). Update rate is not labelled FPS
  or network latency. No key, clipboard, credential or address payloads are added.

## Automated validation

Run with JDK 21 and the Android SDK/native dependencies used by this checkout:

```powershell
.\remoteClientLib\src\main\cpp\build-touch.ps1 -NdkRoot C:\path\to\android-ndk
.\gradlew.bat :bVNC:testDebugUnitTest :OuseRDP-app:testDebugUnitTest :OuseRDP-app:assembleDebug
```

The host suite covers frame damage, true-color pixels, touch/scroll/key routing,
recovery callbacks and error classes, resize debounce/capability timeout, wake
and Wi-Fi locks, diagnostic bounds/persistence, independent handler ownership,
repeated close, and 100 canvas-buffer cleanup cycles. Host native calls are
shadowed or mocked where necessary.

`remoteClientLib/src/main/cpp/native_smoke.c` builds as a standalone Android
executable with the same headers and bridge source. On the TB378FC it checks
capabilities, monitor requests and limits, duplicate channel notifications,
output pause/refresh, error-code packing, callback restoration and 100
attach/detach cycles. It uses an isolated synthetic channel, opens no remote
connection, and does not install or alter the app. All four bridge ABIs compile.

## Owner acceptance — not yet completed

| Check | Acceptance | Status |
| --- | --- | --- |
| Cover-install over 0.1.14 | Same certificate; saved computers/options/credential access retained | Owner test pending |
| Native / automatic / 1920 modes | Text, thin lines and gradients look correct; no unacceptable lag | Owner test pending |
| Rotation and split-screen | Supported server changes size after layout settles; input coordinates match | Owner test pending |
| Switch apps 30 times | Same live session returns, complete frame, no held keys/buttons | Owner test pending |
| Lock/unlock 10 times | Connection preserved or clearly recoverable; complete frame on return | Owner test pending |
| Network outage 5 / 30 / 180 seconds | Transport recovery works; stop action prevents retries | Owner test pending |
| Restart the Windows host | Recover after the host is available; no duplicate connections | Owner test pending |
| Invalid credentials / remote logoff | Correct state and no automatic login loop | Owner test pending |
| Four hours of use | No crash, stale blocks or progressive slowdown; memory not continually growing | Owner test pending |
| One week of everyday use | No issue that blocks normal usage | Owner test pending |

## Delivery / freeze

The APK keeps the existing package and signing certificate and increments
versionCode to 18 (versionName 0.1.17). Keep earlier APKs as the previous baseline.
An Android downgrade may require ADB rather than the file manager. Record hashes
and the passing checks with the test build. Source pushes are separate from APK
releases; publish a release tag and APK only after the owner's acceptance.
Remote-host restarts, network interruptions and automated device installation
are not part of this delivery.
