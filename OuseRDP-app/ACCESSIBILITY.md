# Keyboard service recovery

The settings switch, a system-bound service, and the local `onServiceConnected`
callback are distinct states. The connected callback is authoritative; the
manager list is diagnostic only because OEM lists may lag. Settings shows disabled, connecting, running, or
unavailable. The launcher and RDP session do not display accessibility banners
or service status menu items. It allows a four-second binding grace period on process startup.
`onUnbind` and `onDestroy` clear connection state and listener registration for their own instance only,
so an old instance cannot disconnect its replacement.

The launcher and session monitor while visible. Without privileged permission,
the recovery action opens the public accessibility settings entry. An optional
highlight hint identifies OuseRDP; OEM settings may ignore it. Android 16/ZUI
protects the service-details action with a permission unavailable to ordinary
apps, so it is not used. SecurityException and missing-activity failures fall
back to general settings, then a short instruction if settings cannot open.
The user can toggle only OuseRDP off and on there.

Optional ADB setup after installing this version:

```sh
adb shell pm grant io.github.ouse.ouserdp android.permission.WRITE_SECURE_SETTINGS
```

With this explicit grant, an enabled but unavailable service can rebind on app
resume. Recovery removes only OuseRDP from the enabled-service list and restores
it after 700 ms. It rereads the list to preserve concurrent changes to other
services. A persisted pending marker completes interrupted recovery. It respects
a globally disabled accessibility setting and never starts a new recovery for a
service the user disabled. Automatic attempts have a one-minute cooldown; no
boot receiver or perpetual polling service is added.

The settings diagnostics export includes this app's service state and up to eight
historical process exits, together with bounded RDP session metadata, through
Android's document picker. It does not record keystrokes,
remote credentials, or other apps' events. Native crashes and OEM process kills
remain separate causes requiring their own diagnosis; this change does not
claim to prevent them.
