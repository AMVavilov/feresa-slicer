# Play Console App content answers

## App category

- Type: App
- Suggested category: Tools
- Pricing: Free
- Contains ads: No

## App access

Core model import, placement, slicing, preview, and G-code save features are
available without authentication.

Optional restricted features:

- Orca Cloud profile synchronization requires the reviewer to choose Google or
  GitHub in the app and complete Orca Cloud authentication in the browser.
- A local Google Play review demo exposes the same signed-in profile-selection
  UI with bundled printer, filament, and process profiles. It does not create an
  Orca Cloud session, contact a network service, access user data, or configure
  a printer endpoint.
- Printer status and sending require a Moonraker or OctoPrint endpoint on a
  network accessible from the review device.

Play Console answer: **All or some functionality is restricted**. Enter the
non-secret local demo credentials below in the App access form. They remain
valid without 2FA, an external identity provider, or network access:

- Username: `play-review@feresa.local`
- Password: `feresa-local-demo`

Review path: **Menu → OrcaCloud account → Local Google Play review demo**. Expand
the form, enter the credentials, and choose **Open local demo**. The Printer,
Filament, and Print screens then contain one local demo profile each. Refresh in
demo mode only restores those bundled profiles. Leaving the demo removes them.

Suggested reviewer note:

> Core slicing does not require sign-in. Open Model, import an STL/OBJ/3MF,
> select built-in printer, filament, and process profiles, and tap Slice. To
> review the signed-in profile UI without an external account, open Menu, expand
> Local Google Play review demo, enter the supplied local demo credentials, and
> tap Open local demo. The three displayed profiles are bundled samples; this
> mode never contacts OrcaCloud, a printer, or user data. Live Orca Cloud import
> remains an optional third-party Google/GitHub OAuth integration. Direct
> printer status, upload, and print start require a
> user-controlled Moonraker or OctoPrint endpoint reachable from the review
> device; these hardware/network-dependent functions do not use a Feresa-hosted
> demonstration printer.

## Account creation and deletion

- Google or GitHub sign-in can create an account in the independent Orca Cloud
  service, so answer that the app supports account creation.
- Before release, the in-app account area must include a readily discoverable
  link to the account-deletion instructions.
- External account-deletion URL:
  `https://sync-and-slice-g24.lovable.app/account-deletion`.
- Provider deletion path: sign in at `https://cloud.orcaslicer.com/`, open
  **User settings**, and choose **Delete account** in **Danger Zone**.
- Official provider instructions:
  `https://cloud.orcaslicer.com/wiki/#delete-account`.
- Signing out deletes only the local Orca Cloud session and profile cache; it is
  not a substitute for deleting the Orca Cloud account and cloud data.

## Target audience and content

- Intended audience: general 3D-printing users; select age groups 13–15, 16–17,
  and 18+ only if these match the final marketing decision.
- Not primarily directed to children.
- No ads, gambling, user-generated social content, news, financial services,
  health features, or government affiliation.
- Complete the IARC questionnaire as a utility/tool with no violent, sexual,
  gambling, drug, or strong-language content.

## Permissions

- Internet only. It is used for optional Orca Cloud sync and direct communication
  with a user-configured Moonraker or OctoPrint server.
- Model import and G-code export use Android's system document picker; broad file
  access is not requested.

## Declarations expected in Play Console

- Ads: No.
- News app: No.
- Government app: No.
- Financial features: None.
- Health features: None.
- Data safety: complete from `data-safety.md`.
- Privacy policy: `https://sync-and-slice-g24.lovable.app/privacy`.
- Account deletion: `https://sync-and-slice-g24.lovable.app/account-deletion`.
