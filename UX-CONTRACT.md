# Native Android UI contract

| Capability | Canonical owner | Source of truth | Allowed variants | Verification |
| --- | --- | --- | --- | --- |
| Form | ReceiverActivity pairing dialog | ReceiverClient validation and stored pairing | First pairing / replacement | receiver_client_ui.py, receiver_reconnect.py |
| Scrollbar | Android ScrollView | Native Android scrolling | Page / bounded sheet | Native screenshot and bounds checks |
| Toast | ReceiverActivity.failure and inline status | Current authenticated connection state | Foreground feedback / silent retry | receiver_reconnect.py |
| Select/Listbox | Android AlertDialog | Device type and discovered receivers | Native selection | receiver_focus_ui.py |

Known projector detection is retained with the current pairing. A stale IP, offline projector, process restart, or erroneous TV UI profile must not remove focus controls. A newly paired device clears that remembered capability before detecting the new device. Unknown TVs keep their explicit TV profile. A failed capability request is unknown, never proof that a previously supported feature disappeared.

The receiver starts after Android boot or APK replacement only with control enabled. Stopping control revokes pairing and stops the foreground service. Android force-stop remains a deliberate system stop. Discovery proves possession of the saved key; an unauthenticated nearby device cannot silently replace the saved projector.

Focus, navigation and geometry commands are never queued for replay after reconnect. UI buttons disable during disconnect. The phone retries while its remote is open and checks connection health without replacing successful command feedback. Native dialogs preserve system keyboard/back behavior; critical errors remain visible inline.
