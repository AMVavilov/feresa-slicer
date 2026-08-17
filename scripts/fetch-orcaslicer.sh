#!/usr/bin/env bash
# SPDX-License-Identifier: AGPL-3.0-only
set -euo pipefail

readonly UPSTREAM_URL="https://github.com/OrcaSlicer/OrcaSlicer.git"
readonly UPSTREAM_COMMIT="d5dbd96dd64b830076c81053ed5fda26d5a1771b"
readonly SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
readonly PROJECT_DIR="$(cd -- "${SCRIPT_DIR}/.." && pwd)"
readonly DESTINATION="${PROJECT_DIR}/external/orcaslicer"

if [[ -e "${DESTINATION}" ]]; then
    echo "Destination already exists: ${DESTINATION}" >&2
    exit 1
fi

git clone --filter=blob:none --no-checkout "${UPSTREAM_URL}" "${DESTINATION}"
git -C "${DESTINATION}" fetch --depth 1 origin "${UPSTREAM_COMMIT}"
git -C "${DESTINATION}" checkout --detach "${UPSTREAM_COMMIT}"
echo "Checked out OrcaSlicer ${UPSTREAM_COMMIT}"
