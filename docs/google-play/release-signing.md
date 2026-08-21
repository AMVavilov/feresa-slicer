# Google Play signing

Use Play App Signing. Google should hold the app-signing key; the team keeps a
separate permanent upload key.

## 1. Create an upload key once

Run this outside the repository and use a strong password from the team secret
manager:

```bash
keytool -genkeypair \
  -keystore /secure/path/feresa-upload.jks \
  -alias feresa-upload \
  -keyalg RSA -keysize 4096 -validity 10000
```

Store the JKS as a protected attachment/secret and store both passwords in
Passwork. Do not put any key or password in Git, chat, CI logs, or release assets.

## 2. Configure a local build

Copy `keystore.properties.example` to ignored `keystore.properties` and replace
the placeholders, or export these variables:

```bash
export FERESA_UPLOAD_STORE_FILE=/secure/path/feresa-upload.jks
export FERESA_UPLOAD_STORE_PASSWORD='...'
export FERESA_UPLOAD_KEY_ALIAS=feresa-upload
export FERESA_UPLOAD_KEY_PASSWORD='...'
```

## 3. Build

```bash
./gradlew clean test lintRelease bundleRelease
```

The signed bundle is written to:

```text
app/build/outputs/bundle/release/app-release.aab
```

Verify the signer before uploading:

```bash
jarsigner -verify -verbose -certs app/build/outputs/bundle/release/app-release.aab
```

## 4. Play Console

Upload the AAB to Internal testing first and enable Play App Signing. Never
replace or regenerate the upload key just because a developer changes computers;
share access through the secret manager instead.
