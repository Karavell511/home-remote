# Home Remote 0.11.0 / receiver 0.7.1

Verified on 2026-11-21 with JDK 21, Gradle 8.12 and Android SDK 35.

| Area | Evidence |
| --- | --- |
| Build | Debug and release APKs built; app/receiver Android lint passed |
| JVM | 96 checks: ADB, HID, authenticated discovery, NEC parsing and frame timing |
| Receiver | 43 Android integration checks, including unauthorized power/diagnostics rejection and mute |
| Reconnection | 7 scenarios: process restart, resume, changed IP, offline resume, WiFi recovery, unavailable receiver, receiver restart |
| Focus | Both main keys send commands; a known projector overrides the mistaken TV profile; offline and missing geometry do not hide focus |
| Layout | Inspected 320dp, 360dp and 420dp with 1.3 font scale; main controls and scrolling power dialog remain reachable |
| Startup | Dedicated emulator process death, APK replacement and full reboot recover with the original key; revoking Accessibility stops the service |
| Physical reboot | Akenori/Jixin t950s_be311 on Android 13 rebooted from the remote power menu. Receiver restarted, accepted the existing key, and phone returned to connected with F− / F+ enabled. No receiver activity or new PIN was needed |
| Physical projection | Phone UI turned projection off and on; OEM state changed false/true and the authenticated receiver remained reachable while projection was off |
| Infrared | Phone has an emitter. Both discovered firmware mappings failed to produce an observed response. Full power-on from shutdown remains unsupported on this device |
| Distribution | Both APKs have the existing signing certificate; the phone's embedded receiver equals the standalone release APK byte for byte |

The physical test is evidence for this projector/firmware, not every Android device. Projection standby leaves Android and WiFi running. It is distinct from full shutdown. Acknowledged focus commands do not establish optical movement without observation.

The design-document lint has no errors and four orphan-token warnings because runtime native Android styling remains in Java. The static UI audit passed; the runtime checks above provide the layout evidence.

Private addresses, pairing keys, physical phone captures and vendor APKs are excluded from the repository. The README phone image is an emulator capture.
