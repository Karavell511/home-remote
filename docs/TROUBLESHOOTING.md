# Troubleshooting

| Symptom | Check |
| --- | --- |
| No connection | Same router, current receiver IP, active Accessibility service; guest-network isolation or a VPN may block local traffic |
| Pairing code expired | Generate a new code on the receiver; this revokes the previous phone |
| Android blocks the Accessibility switch | If available in your firmware, open the receiver's system App Info menu and allow restricted settings, then enable the service yourself |
| Arrows work in one app but not another | Try the **Экран устройства** panel to activate accessible elements by label; OEM overlays may ignore injected keys |
| Focus does not move | Direct motor control requires a supported projector; ordinary TV navigation does not imply lens support |
| No browser on the TV | Copy the receiver APK using a USB drive and open it with the device's file manager |
| Receiver update asks for confirmation | Complete Android's system prompt on the screen; silent installation depends on Android and firmware |
| Self-built APK cannot update an official release | The signing certificates differ; use matching official updates or treat your build as a separate installation |
| Bluetooth connects but keys do nothing | Pairing alone is not HID keyboard support; both the phone and screen need the appropriate Bluetooth roles |

You do not need to open router ports. The app does not power on a fully switched-off device. UI labels may vary by firmware.
