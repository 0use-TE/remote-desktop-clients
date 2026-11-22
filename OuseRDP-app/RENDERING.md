# Rendering changes in 0.1.14

OuseRDP keeps the existing FreeRDP/JNI bitmap pipeline. Dirty rectangles are
merged until the next display animation callback, replacing the fixed 16.7 ms
check and repeated 100 ms trailing redraw delay. Outward rounding covers thin
updates at fractional zoom. Returning to the foreground requests a full redraw.

The local RDP buffer uses ARGB_8888 instead of RGB_565, preserving 8 bits per
color channel at twice the pixel-buffer memory. The bundled FreeRDP JNI already
supports Android RGBA_8888 destinations. This preserves colors locally; it does
not change the server codec or recover detail lost before decoding.

JNI copies, bitmap drawing, and recycling share the bitmap monitor. The native
copy releases it before requesting a redraw to avoid reversing the view/disposal
lock order. Old OuseRDP buffers are retired after the UI switches drawable.
Updates are clipped to the current buffer with overflow-safe bounds. Recycled
buffers and invalid updates are ignored; failed copies do not publish frames.
The first-frame notification follows a successful copy.

Unit tests cover damage merging, final updates, fractional edges, stale queued
callbacks, buffer clipping and lock ownership, failed copies, true-color pixel
values, and disposal. Native copy behavior is replaced by a test shadow on the
host. This is not a hardware decoder or zero-copy renderer, and no tablet frame
rate or latency measurement has been claimed. Those require installing and
profiling the build against a real Windows session.
