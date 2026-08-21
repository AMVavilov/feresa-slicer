# Data safety declaration draft

This is a conservative implementation-based draft for Play Console. Re-check it
against the final binary and the current Google form before submission.

## Collection and sharing

### Personal information — name, email address, user ID, and authentication tokens

- Collected: **Yes, optional**, only when the user signs in to Orca Cloud. The
  app transmits an authorization code and PKCE verifier and later transmits
  access or refresh tokens for authentication and profile synchronization. The
  Orca Cloud access token is a Supabase-style JWT: it carries a user ID and email
  claim and may carry a display name in user metadata, then is sent as a bearer
  token to the profile service. Select **Name**, **Email address**, and **User
  IDs** conservatively for the release form.
- Shared: **Yes, conservatively**, with Orca Cloud. Google or GitHub also handles
  identity-provider data during the browser-based sign-in flow.
- Purpose: App functionality and account management.
- Processing: The refresh token is encrypted locally with Android Keystore. An
  Orca Cloud account ID is stored with the encrypted local profile cache. The
  returned display name and email address may be displayed locally.
- Required: No. Local slicing works while signed out.

### Files and documents — 3D model files, profiles, and G-code

- 3D models and locally stored profiles: **Not collected**. They remain in
  private app storage or a user-selected document destination and are not sent
  off the device by Feresa Slicer.
- G-code: **Collected, optional**, when the user explicitly sends it from the
  device to a configured Moonraker or OctoPrint server. Google Play defines
  collection as transmission off the device, even when the Feresa developer
  never receives the data.
- Shared: **No**, using Play's user-initiated-action exception. The user selects
  the endpoint and explicitly starts the transfer, and the file is not routed
  through a Feresa backend. Re-confirm that exception against the form presented
  for the final release.
- Purpose: App functionality.
- Required: No. G-code can be saved locally instead of sent to a printer.

### Authentication information — printer API key or Basic Auth credentials

- Transmitted off-device: **Yes, optional**, when testing a connection or sending
  G-code to the printer address selected by the user. The Feresa developer does
  not receive the credentials, but that distinction does not by itself make the
  transfer "not collected" under Google Play's definition.
- Stored locally: Optional, encrypted with Android Keystore.
- Purpose: App functionality.
- Shared: **No**, using Play's user-initiated-action exception for the direct
  connection to the printer selected by the user. Re-confirm that exception
  against the form presented for the final release.
- Data-category mapping: select **Other personal info** for an API key or Basic
  Authentication secret if that category is available in the current form. An
  identifiable Basic Authentication username also falls under **User IDs**.
  Do not omit the transfer merely because it goes directly to a user-controlled
  printer.
- Security: HTTPS is encrypted; local HTTP is not, and the application displays
  a warning.

### Technical network metadata

- Orca Cloud, the selected Google or GitHub identity provider, and the user's
  Moonraker or OctoPrint endpoint may receive the device's IP address, request
  timestamps, and protocol headers as part of normal network communication.
- The Feresa developer does not receive or store this metadata because requests
  are not proxied through a Feresa-operated server.
- Re-check the current provider disclosures before submission. If a provider
  retains or uses an IP address to infer location or identify a device, map that
  handling to the current Play data categories instead of relying on the
  “no location” or “no device identifiers” defaults below.

### All other Play data categories

- Financial information: No.
- Health and fitness: No.
- Messages: No.
- Photos and videos: No.
- Audio: No.
- Contacts: No.
- Calendar: No.
- Location: The app does not request Android location permissions or read device
  location. Re-check whether a selected service infers approximate location from
  network metadata before submitting the final form.
- Web browsing: No.
- App activity: No analytics or tracking collection.
- App info and performance: No crash-reporting or diagnostics SDK.
- Device or other identifiers: No advertising or analytics identifiers. Re-check
  whether the current form or provider disclosures classify network metadata as
  a device or other identifier.

## Security and retention answers

- Data encrypted in transit: **No** for the current binary. Orca Cloud
  authentication and synchronization use HTTPS, but direct printer traffic may
  use HTTP. Play allows **Yes** only when encryption applies to every declared
  user-data transfer.
- Account creation: **Yes**. Google or GitHub sign-in can create an account in the
  independent Orca Cloud service.
- Users can request account deletion: **Yes only after the matching in-app path is
  present in the submitted binary**. The external instructions are hosted in
  `ACCOUNT_DELETION.md`; the provider flow is Orca Cloud **User settings → Delete
  account**. Official provider instructions:
  `https://cloud.orcaslicer.com/wiki/#delete-account`.
- Local deletion: signing out deletes the local Orca Cloud session/cache;
  deleting a manual connection deletes its credentials; uninstalling clears the
  app's private data. These actions do not delete the independent Orca Cloud
  account or its cloud data.
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
