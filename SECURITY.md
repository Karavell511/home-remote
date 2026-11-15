# Security and privacy

Use Home Remote only on a trusted private network. The receiver uses HTTP, not TLS; pairing tokens and commands can be observed by an attacker with access to the network traffic. Do not forward ports 8787, 8788 or 8085 to the internet. This is an early project, not a remotely administered internet service.

UDP discovery on port 8788 broadcasts a fresh random challenge, never a PIN or bearer token. A paired receiver signs the challenge and its advertised IP with HMAC SHA256. The phone verifies that proof before sending its token to a newly discovered address. Responses are rate limited and contain only the model, private address and proof. Discovery for a new phone lists nearby receivers but still requires the code on the screen. This does not encrypt the subsequent HTTP control traffic.

- The receiver requires an explicitly enabled Android Accessibility service. It can read accessible UI labels, activate elements and perform navigation commands.
- Pairing uses a one-time six-digit code, a five-minute expiry and a delay after repeated failures. A random token is stored in private application preferences. Android backups are disabled.
- A new pairing code revokes the old phone. Stopping control clears pairing and disables the service.
- The receiver accepts loopback for tests and local clients through its selected Wi-Fi/Ethernet interface.
- Updates require pairing, a bounded APK size, a SHA-256 match, the same package and signing certificate, and a higher version code. Android controls installation permissions and may require confirmation.
- Authenticated diagnostic endpoints can export the installed settings APK or the known focus/keystone provider APK. They do not expose arbitrary filesystem paths. Do not share a pairing token.
- No advertisements, analytics or cloud backend are implemented. The phone keeps a local device address, pairing token and user preferences.
- Bluetooth and ADB are optional advanced modes. Enabling Android debugging grants wider access; keep it limited to your own trusted network and devices.

For a vulnerability, use the repository's private vulnerability-reporting option if available. Otherwise open an issue requesting a private contact without publishing an exploit, token, device identifiers or personal screenshots.
