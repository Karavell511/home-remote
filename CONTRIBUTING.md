# Contributing

Device reports are especially useful: include manufacturer/model, Android version, connection method, and the exact buttons tested. An accepted HTTP response alone does not prove that a lens moved or a setting changed.

For code changes:

1. Keep direct hardware actions allowlisted and bounded. Do not add arbitrary shell execution, provider calls or firmware/calibration writes.
2. Preserve pairing authentication, update signature checks and Android permission prompts.
3. Build both APKs, run Android lint and `python tools/test_protocols.py`.
4. Use a dedicated emulator for integration tests; they change Accessibility settings and clear the test phone app's data.
5. Include screenshots for UI changes and explain what was tested on real hardware versus an emulator.

Useful starting points: English UI localization, model-specific compatibility documentation, and navigation fixes for Android apps with limited Accessibility support. See the open issues before starting larger changes.

Do not commit private IP/MAC/serial values from your devices, pairing credentials, signing keys, UI dumps, router screenshots or manufacturer APKs. Contributions are under the project's MIT license.
