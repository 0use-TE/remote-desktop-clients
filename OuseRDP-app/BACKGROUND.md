# Background sessions and recovery (0.1.9)

OuseRDP starts a connected-device foreground service while a session is active. The notification returns to the existing session activity or explicitly disconnects it. The service holds the current backend and its close action; switching applications only releases held input. CPU/Wi-Fi locks and a network callback are scoped to the active service and released when it stops.

Background retention and automatic reconnection are enabled by default and can be changed under Settings → 连接保持. Disabling retention leaves the visible connection active. Closing the session, using the notification's disconnect action, or removing the app task stops the session and cancels retries. The service is not restarted with missing credentials after process death.

After a previously successful session loses transport, both FreeRDP terminal outcomes (`OnConnectionFailure` and `OnDisconnected`) enter the same guarded recovery path. Retry delays are 1, 2, 4, 8, 16, 30, 30 and 30 seconds. Offline devices wait for a network instead of consuming attempts. Retry exhaustion leaves a manual retry control in the session. Initial address/authentication errors retain the existing interactive error handling.

Each retry frees the old native session, creates a fresh instance using in-memory bookmark settings, clears transient input state and negotiates RDPEI again. Input queued for a previous instance is discarded; late lifecycle events from old instances cannot close the active one. Server-driven framebuffer size changes update the existing drawable rather than opening a second RDP session.

The existing rendering/clipboard components still reference the session UI context. This change retains one active in-process backend; it does not persist passwords or native state to disk, and it cannot guarantee a live connection after Android kills the entire process. Reopening a destroyed activity can establish a new connection using the saved computer entry. Windows decides whether the remote user's existing desktop session remains available.

Unit tests cover retry bounds/reset/stop, either terminal callback, duplicate callbacks, stale-instance callbacks, explicit disconnect, notification stop, and disabling background retention. Device checks should include switching applications, a temporary network loss, notification disconnect, and confirming that disconnect never restarts the session.
