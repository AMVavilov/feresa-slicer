#!/usr/bin/env bash
set -euo pipefail

ORCA_MOBILE_COMMIT="6fc2e14b9a222301f4432cee26d7ab37d3be86d0"
ORCA_SOURCE_ARCHIVE_SHA256="d92f8e28ebdd2ee39f34a6a00b3004299b069b140bb53c5b5f8e7e79eb686832"
ORCA_PRESET_MANIFEST_SHA256="e6cd5b0f71b0d1f2b0b1202e177d2df2b4af0bb2a8a91f2872715a72ee37b98d"
ORCA_PRESET_COUNT="64"
ORCA_SOURCE_ARCHIVE_URL="https://codeload.github.com/CodeMasterCody3D/OrcaSlicer-Mobile/zip/$ORCA_MOBILE_COMMIT"

PROJECT_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
TARGET_DIR="${FERESA_ORCA_PRESET_TARGET_DIR:-$PROJECT_ROOT/app/build/generated/orcaSystemPresets/assets/orca_profiles}"
MARKER_FILE="$TARGET_DIR/.orca-system-presets.sha256"
CACHE_ROOT="${TMPDIR:-/tmp}/feresa-orca-port-assets"
ARCHIVE_FILE="$CACHE_ROOT/OrcaSlicer-Mobile-6fc2e14b9a.zip"

if [[ -f "$MARKER_FILE" ]] && [[ "$(tr -d '\r\n' < "$MARKER_FILE")" == "$ORCA_PRESET_MANIFEST_SHA256" ]]; then
  actual_count="$(find "$TARGET_DIR" -maxdepth 1 -type f -name '*.ini' | wc -l | tr -d ' ')"
  if [[ "$actual_count" == "$ORCA_PRESET_COUNT" ]]; then
    exit 0
  fi
fi

mkdir -p "$CACHE_ROOT"
if [[ ! -f "$ARCHIVE_FILE" ]] || [[ "$(shasum -a 256 "$ARCHIVE_FILE" | awk '{print $1}')" != "$ORCA_SOURCE_ARCHIVE_SHA256" ]]; then
  curl --fail --location --retry 3 --output "$ARCHIVE_FILE.download" "$ORCA_SOURCE_ARCHIVE_URL"
  mv "$ARCHIVE_FILE.download" "$ARCHIVE_FILE"
fi
actual_archive_sha="$(shasum -a 256 "$ARCHIVE_FILE" | awk '{print $1}')"
if [[ "$actual_archive_sha" != "$ORCA_SOURCE_ARCHIVE_SHA256" ]]; then
  echo "Orca source archive checksum mismatch: $actual_archive_sha" >&2
  exit 1
fi

STAGE_DIR="$(mktemp -d "${TMPDIR:-/tmp}/feresa-orca-presets.XXXXXX")"
trap 'rm -rf "$STAGE_DIR"' EXIT
STAGED_PRESETS="$STAGE_DIR/orca_profiles"
mkdir -p "$STAGED_PRESETS"
unzip -jq "$ARCHIVE_FILE" '*/app/src/main/assets/orca_profiles/*.ini' -d "$STAGED_PRESETS"
actual_count="$(find "$STAGED_PRESETS" -maxdepth 1 -type f -name '*.ini' | wc -l | tr -d ' ')"
if [[ "$actual_count" != "$ORCA_PRESET_COUNT" ]]; then
  echo "Expected $ORCA_PRESET_COUNT Orca preset bundles, found $actual_count" >&2
  exit 1
fi

MANIFEST_FILE="$STAGE_DIR/manifest.sha256"
find "$STAGED_PRESETS" -maxdepth 1 -type f -name '*.ini' -exec basename {} \; | LC_ALL=C sort |
  while IFS= read -r preset_name; do
    preset_sha="$(shasum -a 256 "$STAGED_PRESETS/$preset_name" | awk '{print $1}')"
    printf '%s  %s\n' "$preset_sha" "$preset_name"
  done > "$MANIFEST_FILE"
actual_manifest_sha="$(shasum -a 256 "$MANIFEST_FILE" | awk '{print $1}')"
if [[ "$actual_manifest_sha" != "$ORCA_PRESET_MANIFEST_SHA256" ]]; then
  echo "Orca system preset manifest mismatch: $actual_manifest_sha" >&2
  exit 1
fi

mkdir -p "$TARGET_DIR"
find "$TARGET_DIR" -maxdepth 1 -type f -name '*.ini' -delete
cp "$STAGED_PRESETS"/*.ini "$TARGET_DIR"/
printf '%s\n' "$ORCA_PRESET_MANIFEST_SHA256" > "$MARKER_FILE"
printf 'Installed %s Orca system preset bundles from %s\n' "$actual_count" "$ORCA_MOBILE_COMMIT"
