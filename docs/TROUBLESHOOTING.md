# Troubleshooting

| Symptom | Check |
| --- | --- |
| No connection | Same router, current receiver IP, active Accessibility service; guest-network isolation or a VPN may block local traffic |
| Connection drops after reopening | Install phone 0.11.0 and receiver 0.7.1 over your current apps. Pairing is preserved and the remote retries automatically |
| Receiver must be opened after every boot | Receiver 0.7.1 has a foreground LAN service, a boot/update receiver and process recovery. Leave Accessibility enabled. If the firmware has an app auto-start/background restriction, allow the receiver there. Android force-stop intentionally blocks background startup until the app is opened again |
| Focus buttons disappeared | Compatible projectors now override an accidental TV profile. F− / F+ are on the main projector screen. For other projectors, select **Ещё → Тип устройства: проектор / ТВ**. Focus no longer depends on the geometry/scale service returning a value |
| Router changed the receiver IP | Keep the remote open. It finds and authenticates the remembered receiver over local UDP discovery. If the router or VPN blocks discovery, allow local network traffic or enter the current IP and leave the code empty to use the saved pairing |
| Pairing code expired | A code is only needed for a new phone. An already paired phone reconnects with its saved key. Generating a new code requires confirmation because it revokes the previous phone |
| Android blocks the Accessibility switch | If available in your firmware, open the receiver's system App Info menu and allow restricted settings, then enable the service yourself |
| Arrows work in one app but not another | Try the **Экран устройства** panel to activate accessible elements by label; OEM overlays may ignore injected keys |
| Focus does not move | Direct motor control requires a supported projector; ordinary TV navigation does not imply lens support |
| No browser on the TV | Copy the receiver APK using a USB drive and open it with the device's file manager |
| Receiver update asks for confirmation | Complete Android's system prompt on the screen; silent installation depends on Android and firmware |
| Self-built APK cannot update an official release | The signing certificates differ; use matching official updates or treat your build as a separate installation |
| Bluetooth connects but keys do nothing | Pairing alone is not HID keyboard support; both the phone and screen need the appropriate Bluetooth roles |

| Projection is off but sound continues | WiFi projection control leaves Android running; use the separate mute button |
| Projector cannot be turned on after full shutdown | WiFi is unavailable then. Use the physical device button or a compatible infrared remote. Experimental NEC profiles did not work on the tested Akenori; do not confirm the Home probe unless the projector actually responds |

You do not need to open router ports. UI labels may vary by firmware.
