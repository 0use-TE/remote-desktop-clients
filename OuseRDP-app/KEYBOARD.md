# Keyboard routing

Ctrl+Alt+F12 toggles keyboard routing between Windows (the default) and Android
while a connected OuseRDP session has focus and keyboard accessibility is enabled.
Android mode passes keyboard events to the system and prevents remaining events
from being forwarded by the remote canvas. It does not emulate app switching.
A large banner identifies Android mode and shows the shortcut to return.

Ctrl+Alt+Tab and Alt+Win+Tab have no special local mapping. In Windows mode they
follow the existing Windows shortcut forwarding. Holding Ctrl or Alt alone does
not switch modes. Long-press repeats of F12 do not toggle again.

The switch releases captured remote keys and consumes the rest of the physical
chord in both directions. The mode survives app switching for the same session;
a new session or restarting the accessibility service defaults to Windows.
