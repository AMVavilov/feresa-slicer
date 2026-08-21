# Data safety declaration draft

This is a conservative implementation-based draft for Play Console. Re-check it
against the final binary and the current Google form before submission.

## Collection and sharing

### Personal information — email address and user ID

- Collected: **Yes, optional**, only when the user signs in to OrcaCloud.
- Shared: **With OrcaCloud and the identity provider selected by the user** for
  authentication and profile synchronization. Confirm with counsel whether the
  Play Console user-initiated-action exemption applies before changing this to
  “not shared.”
- Purpose: App functionality and account management.
- Processing: The account identity is displayed in the app; the refresh token is
  encrypted locally with Android Keystore.
- Required: No. Local slicing works while signed out.

### Files and documents — 3D model files, profiles, and G-code

- Collected by the Feresa developer: **No**.
- Stored locally: Yes, in private app storage or a user-selected document
  destination.
- User-directed transfer: G-code can be sent directly to the user's configured
  Moonraker or OctoPrint server. It is not routed through a Feresa backend.

### Authentication information — printer API key or Basic Auth credentials

- Collected by the Feresa developer: **No**.
- Stored locally: Optional, encrypted with Android Keystore.
- User-directed transfer: Sent only to the printer address selected by the user.
  HTTPS is encrypted; local HTTP is not, and the application displays a warning.

### All other Play data categories

- Financial information: No.
- Health and fitness: No.
- Messages: No.
- Photos and videos: No.
- Audio: No.
- Contacts: No.
- Calendar: No.
- Location: No.
- Web browsing: No.
- App activity: No analytics or tracking collection.
- App info and performance: No crash-reporting or diagnostics SDK.
- Device or other identifiers: No advertising or analytics identifiers.

## Security and retention answers

- Data encrypted in transit: **Yes for OrcaCloud**. Direct printer traffic is
  controlled by the user and can be HTTP; disclose this in the policy and UI.
- Users can request deletion: Feresa creates no developer-operated account.
  Signing out deletes the local OrcaCloud session/cache; deleting a manual
  connection deletes its credentials; uninstalling clears private app data.
  External accounts must be deleted through their provider.
- Data committed to be handled ephemerally: No blanket claim; encrypted profiles
  and credentials may persist until the user removes them.
- Independent security review: No.
- Ads: No.

## Code evidence

- Encrypted Orca refresh token: `EncryptedRefreshTokenStore.kt`.
- Encrypted Orca profiles: `OrcaProfileCache.kt`.
- Encrypted manual printer credentials: `ManualPrinterConnectionStore.kt`.
- No backup: `android:allowBackup="false"` in `AndroidManifest.xml`.
- Declared permission: only `android.permission.INTERNET`.
