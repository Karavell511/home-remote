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

This uses `JAVA_HOME` or `java`/`javac` on PATH. The tests cover ADB framing/authentication/error handling and Bluetooth HID report structure, not physical-device compatibility.

For Android integration, start a **dedicated disposable emulator** at `emulator-5580`, with display size 720×1600 and density 320, install SDK platform-tools on PATH, then run:

```sh
./gradlew :app:assembleDebug :receiver:assembleDebug :testdriver:assembleDebug
python tests/receiver_integration.py
python tests/receiver_client_ui.py
```

`HOME_REMOTE_TEST_SERIAL` can select another emulator. The scripts refuse physical device serials. They enable the receiver Accessibility service and clear only the test phone application's data. UI inspection preserves Accessibility services; ordinary `uiautomator dump` may temporarily suppress them.

The initial local verification passed 38 Android receiver checks plus onboarding, profile selection, haptics and byte-exact receiver download checks. Physical testing covered the Akenori/Jixin projector listed in the README. Other hardware needs its own evidence.

## Projector adapter

`JixinProjector` resolves known OEM service authorities. It calls bounded focus/keystone/projection methods and reads back state where the firmware exposes it. The focus method's boolean means **limit reached**, not success; actual lens motion needs observation. Scale percentage is polygon area, not the linear zoom value shown by some OEM menus.

No vendor APK or decompiled vendor source is required to build. The OEM classes used to decode runtime responses come from the user's installed settings application.
