# Licensing policy

This document records the project's implementation policy and is not legal
advice.

## Android application

The Android application, JNI boundary, build files, and native slicing engine
are distributed as one work under GNU AGPL version 3. Every distributed APK
must have a clear, no-charge link to the exact Corresponding Source used to
build it. The source release includes build scripts, interface definitions,
patches, dependency metadata, notices, and build instructions.

Signing keys, service credentials, production secrets, and user data are not
part of Corresponding Source and must never be committed.

## Paid profile synchronization

Local slicing, local profiles, and G-code export remain available without an
account. A separate service may charge for synchronization, backups, profile
history, and collaboration. The service is kept in a separate repository and
does not link to or execute the OrcaSlicer-derived slicing engine.

If AGPL-covered slicing code is later executed as a network service, users of
that service must be offered the Corresponding Source for the version running
on the server in accordance with AGPL section 13.

## Release checklist

- Preserve upstream copyright and license notices.
- Mark material modifications and their dates.
- Include the complete AGPL-3.0 license.
- Add an in-app `License and source code` entry.
- Point the APK and store listing to the exact public source tag.
- Generate and review a third-party license inventory.
- Exclude the proprietary Bambu networking plugin unless separately reviewed.
- Use an independent product name, logo, and store presentation.
