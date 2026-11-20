# Development

The source uses Java and Android framework APIs. Modules:

- `app`: phone UI, Wi-Fi client, APK transfer, optional Bluetooth HID and classic ADB/TCP.
- `receiver`: Accessibility service, HTTP pairing/control, OEM projector adapter and signed self-update.
- `testdriver`: emulator-only fixtures and UI inspection; never install it on users' devices.

## Build

Install JDK 17+ and Android SDK platform 35 / build-tools 35.0.0. Set `ANDROID_HOME` or an untracked `local.properties` with `sdk.dir`. Run the Gradle wrapper from the repository root:

```sh
./gradlew :app:assembleDebug :receiver:assembleDebug :app:lintDebug :receiver:lintDebug
./gradlew :app:assembleRelease :receiver:assembleRelease
```

The phone's build first assembles the matching receiver variant and includes its APK as a generated asset. Do not commit the generated APK.

Release variants disable debugging but currently use the developer's local Android debug signing configuration. Maintainer download APKs retain a private, stable signing key; it is not in the repository or CI. Configure your own production signing if distributing a fork. CI artifacts are development builds and are not automatic updates for the maintainer's signed releases.

## Tests

```sh
python tools/test_protocols.py
```

This uses `JAVA_HOME` or `java`/`javac` on PATH. The 96 checks cover ADB framing, authentication, errors, Bluetooth HID reports, discovery proofs (replay/address tampering), and NEC parsing/pulse timing. They do not establish physical device compatibility.

For Android integration, start a **dedicated disposable emulator** at `emulator-5580`, with display size 720×1600 and density 320, install SDK platform-tools on PATH, then run:

```sh
./gradlew :app:assembleDebug :receiver:assembleDebug :testdriver:assembleDebug
python tests/receiver_integration.py
python tests/receiver_client_ui.py
python tests/receiver_reconnect.py
python tests/receiver_focus_ui.py
python tests/receiver_layout.py
python tests/receiver_boot.py
```

`HOME_REMOTE_TEST_SERIAL` can select another emulator. The scripts refuse physical device serials. They enable the receiver Accessibility service and clear only the test phone application's data. UI inspection preserves Accessibility services; ordinary `uiautomator dump` may temporarily suppress them.

The 0.11.0 verification passed 43 Android receiver checks plus onboarding, profile selection, haptics and byte-exact receiver download checks. Layouts were checked at 320dp, 360dp and 420dp with 1.3 font scale. Physical testing covered the Akenori/Jixin projector listed in the README. Other hardware needs its own evidence.

The reconnection suite checks process restart, return from Home, stale IP discovery, WiFi loss and recovery, and receiver service restart. It runs after the UI suite on the same disposable emulator. Pairing keys stay out of test output. Closing the remote stops retries and queued control commands; returning starts a fresh connection without replaying buttons.

The focus fixture deliberately reports a projector with no scale/geometry fields and starts the phone with the TV profile. It checks automatic profile correction and both focus commands. The boot suite kills the receiver process, replaces its APK, reboots Android, verifies the original pairing without opening the receiver activity, and checks that revoking Accessibility stops the network service. These emulator checks do not establish OEM auto-start behavior on every projector.

ReceiverService owns the HTTP/discovery sockets and runs as a connected-device foreground service. BootReceiver starts it on boot/package replacement; the enabled Accessibility service also starts it. ControlService only handles Android input. Explicit stop revokes pairing, disables control and stops the foreground service; it must never be undone by a watchdog.

## Projector adapter

`JixinProjector` resolves known OEM service authorities. It calls bounded focus/keystone/projection methods and reads back state where the firmware exposes it. The focus method's boolean means **limit reached**, not success; actual lens motion needs observation. Scale percentage is polygon area, not the linear zoom value shown by some OEM menus.

No vendor APK or decompiled vendor source is required to build. The OEM classes used to decode runtime responses come from the user's installed settings application.

## Power adapter

WiFi projection commands call the OEM standby/wakeup screen methods and read back the state. This keeps Android running and does not imply full shutdown or network wake support. The Android power dialog uses the Accessibility global action.

Infrared is optional, with normal TRANSMIT_IR permission. Authenticated diagnostics read only bounded remote configuration files from fixed firmware directories. The phone recognizes legacy factory_code and modern custom_code NEC tables, sends one 38kHz frame, and refreshes its cached mapping after connecting. A user must confirm the Home probe before the power button appears. Confirmation is tied to the exact profile. Sending pulses is not proof of projector response; both discovered profiles failed on the tested Akenori.

API reference: [ConsumerIrManager](https://developer.android.com/reference/android/hardware/ConsumerIrManager), [connectedDevice foreground services](https://developer.android.com/develop/background-work/services/fgs/service-types#connected-device).
