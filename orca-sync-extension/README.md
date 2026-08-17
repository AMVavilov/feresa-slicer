# G24 Orca Sync Companion

Small cross-platform companion for synchronizing OrcaSlicer user presets with
the G24 profile portal. OrcaSlicer currently has no official plugin API, so the
companion reads and writes the documented JSON files in its user profile
folder. It never modifies OrcaSlicer itself.

## Requirements

- OrcaSlicer 2.3 or newer
- Python 3.10 or newer
- Windows, macOS, or Linux

## Install and configure

Extract this archive, open a terminal in the extracted folder, and run:

```bash
python3 orca_sync.py detect
python3 orca_sync.py configure \
  --server-url "ENDPOINT_FROM_PORTAL" \
  --sync-token "TOKEN_FROM_PORTAL"
```

Windows users can replace `python3` with `py`.

## Synchronize

```bash
python3 orca_sync.py push
python3 orca_sync.py pull
```

Pull does not overwrite a locally changed file by default. To back up and
replace changed local files:

```bash
python3 orca_sync.py pull --force
```

Restart OrcaSlicer after pulling profiles. Backups are kept under the
`g24-orca-sync/backups` configuration directory.

OrcaSlicer profile locations and JSON structure are documented by the upstream
project: <https://github.com/OrcaSlicer/OrcaSlicer/wiki/user_profiles>.

## Security

The sync token is stored in the current user's application configuration folder
with user-only permissions on macOS and Linux. Do not share the configuration
file or commit it to source control.
