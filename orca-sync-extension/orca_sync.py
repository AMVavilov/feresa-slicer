#!/usr/bin/env python3
# SPDX-License-Identifier: AGPL-3.0-only
"""G24 Orca Sync Companion.

Synchronizes OrcaSlicer user preset JSON files through a small HTTPS API.
Uses only the Python standard library and never modifies OrcaSlicer binaries.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import platform
import shutil
import stat
import sys
import urllib.error
import urllib.request
from datetime import datetime, timezone
from pathlib import Path
from typing import Any


APP_NAME = "g24-orca-sync"
PROFILE_TYPES = {"machine", "filament", "process"}


def config_directory() -> Path:
    if platform.system() == "Windows":
        base = Path(os.environ.get("APPDATA", Path.home()))
    elif platform.system() == "Darwin":
        base = Path.home() / "Library" / "Application Support"
    else:
        base = Path(os.environ.get("XDG_CONFIG_HOME", Path.home() / ".config"))
    return base / APP_NAME


def config_path() -> Path:
    return config_directory() / "config.json"


def candidate_orca_roots() -> list[Path]:
    system = platform.system()
    if system == "Windows":
        base = Path(os.environ.get("APPDATA", Path.home())) / "OrcaSlicer"
        return [base]
    if system == "Darwin":
        return [Path.home() / "Library" / "Application Support" / "OrcaSlicer"]
    return [
        Path.home() / ".config" / "OrcaSlicer",
        Path.home() / ".var" / "app" / "com.orcaslicer.OrcaSlicer" / "config" / "OrcaSlicer",
        Path.home() / ".var" / "app" / "io.github.softfever.OrcaSlicer" / "config" / "OrcaSlicer",
    ]


def detect_profile_root() -> Path:
    for root in candidate_orca_roots():
        default = root / "user" / "default"
        if default.is_dir():
            return default
        user_root = root / "user"
        if user_root.is_dir():
            directories = sorted(path for path in user_root.iterdir() if path.is_dir())
            if directories:
                return directories[0]
    raise RuntimeError(
        "OrcaSlicer profiles were not found. Open Help > Show Configuration Folder "
        "and pass its user/default folder with --profile-root."
    )


def load_config() -> dict[str, Any]:
    path = config_path()
    if not path.exists():
        return {}
    return json.loads(path.read_text(encoding="utf-8"))


def save_config(data: dict[str, Any]) -> None:
    directory = config_directory()
    directory.mkdir(parents=True, exist_ok=True)
    path = config_path()
    path.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8")
    if os.name != "nt":
        path.chmod(stat.S_IRUSR | stat.S_IWUSR)


def resolve_profile_root(argument: str | None, config: dict[str, Any]) -> Path:
    if argument:
        return Path(argument).expanduser().resolve()
    if config.get("profile_root"):
        return Path(config["profile_root"]).expanduser().resolve()
    return detect_profile_root()


def checksum(content: str) -> str:
    return hashlib.sha256(content.encode("utf-8")).hexdigest()


def read_profiles(root: Path) -> list[dict[str, Any]]:
    profiles: list[dict[str, Any]] = []
    if not root.is_dir():
        raise RuntimeError(f"Profile root does not exist: {root}")
    for path in sorted(root.rglob("*.json")):
        relative = path.relative_to(root)
        if not relative.parts or relative.parts[0] not in PROFILE_TYPES:
            continue
        content = path.read_text(encoding="utf-8")
        parsed = json.loads(content)
        modified = datetime.fromtimestamp(path.stat().st_mtime, timezone.utc).isoformat()
        profiles.append(
            {
                "path": relative.as_posix(),
                "kind": relative.parts[0],
                "name": str(parsed.get("name") or path.stem),
                "content": parsed,
                "checksum": checksum(content),
                "modified_at": modified,
            }
        )
    return profiles


def api_request(config: dict[str, Any], action: str, payload: dict[str, Any]) -> dict[str, Any]:
    server_url = str(config.get("server_url", "")).rstrip("/")
    token = str(config.get("sync_token", ""))
    if not server_url or not token:
        raise RuntimeError("Run `orca_sync.py configure` before synchronization.")
    body = json.dumps({"action": action, **payload}).encode("utf-8")
    request = urllib.request.Request(
        server_url,
        data=body,
        method="POST",
        headers={
            "Content-Type": "application/json",
            "Accept": "application/json",
            "X-Sync-Token": token,
            "User-Agent": "G24-Orca-Sync/0.1.0",
        },
    )
    try:
        with urllib.request.urlopen(request, timeout=45) as response:
            return json.loads(response.read().decode("utf-8"))
    except urllib.error.HTTPError as error:
        detail = error.read().decode("utf-8", errors="replace")
        raise RuntimeError(f"Sync API returned HTTP {error.code}: {detail}") from error
    except urllib.error.URLError as error:
        raise RuntimeError(f"Cannot reach sync API: {error.reason}") from error


def safe_target(root: Path, relative_path: str) -> Path:
    relative = Path(relative_path)
    if relative.is_absolute() or ".." in relative.parts:
        raise RuntimeError(f"Unsafe server path: {relative_path}")
    if not relative.parts or relative.parts[0] not in PROFILE_TYPES or relative.suffix != ".json":
        raise RuntimeError(f"Unsupported profile path: {relative_path}")
    target = (root / relative).resolve()
    if root.resolve() not in target.parents:
        raise RuntimeError(f"Path escapes profile root: {relative_path}")
    return target


def write_profiles(root: Path, profiles: list[dict[str, Any]], force: bool) -> int:
    timestamp = datetime.now().strftime("%Y%m%d-%H%M%S")
    backup_root = config_directory() / "backups" / timestamp
    written = 0
    for profile in profiles:
        target = safe_target(root, str(profile["path"]))
        content = json.dumps(profile["content"], ensure_ascii=False, indent=2) + "\n"
        if target.exists():
            existing = target.read_text(encoding="utf-8")
            if checksum(existing) == profile.get("checksum"):
                continue
            if not force:
                print(f"skip changed local file: {profile['path']}")
                continue
            backup = backup_root / target.relative_to(root)
            backup.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(target, backup)
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(content, encoding="utf-8")
        written += 1
    if written and backup_root.exists():
        print(f"backup: {backup_root}")
    return written


def command_configure(args: argparse.Namespace) -> None:
    current = load_config()
    root = resolve_profile_root(args.profile_root, current)
    current.update(
        {
            "server_url": args.server_url.rstrip("/"),
            "sync_token": args.sync_token,
            "profile_root": str(root),
        }
    )
    save_config(current)
    print(f"configured: {config_path()}")
    print(f"profiles: {root}")


def command_detect(args: argparse.Namespace) -> None:
    root = resolve_profile_root(args.profile_root, load_config())
    profiles = read_profiles(root)
    counts = {kind: sum(1 for item in profiles if item["kind"] == kind) for kind in sorted(PROFILE_TYPES)}
    print(root)
    print(" ".join(f"{kind}={count}" for kind, count in counts.items()))


def command_push(args: argparse.Namespace) -> None:
    config = load_config()
    root = resolve_profile_root(args.profile_root, config)
    profiles = read_profiles(root)
    result = api_request(config, "push", {"device_name": platform.node(), "profiles": profiles})
    print(f"uploaded={result.get('uploaded', len(profiles))}")


def command_pull(args: argparse.Namespace) -> None:
    config = load_config()
    root = resolve_profile_root(args.profile_root, config)
    result = api_request(config, "pull", {"device_name": platform.node()})
    profiles = result.get("profiles")
    if not isinstance(profiles, list):
        raise RuntimeError("Sync API response does not contain a profiles list.")
    written = write_profiles(root, profiles, args.force)
    print(f"downloaded={len(profiles)} written={written}")
    if written:
        print("Restart OrcaSlicer to reload changed presets.")


def parser() -> argparse.ArgumentParser:
    result = argparse.ArgumentParser(description="Synchronize OrcaSlicer user profiles")
    subparsers = result.add_subparsers(dest="command", required=True)

    configure = subparsers.add_parser("configure", help="save portal endpoint and sync token")
    configure.add_argument("--server-url", required=True, help="profile-sync endpoint shown in the portal")
    configure.add_argument("--sync-token", required=True, help="token generated in the portal")
    configure.add_argument("--profile-root")
    configure.set_defaults(handler=command_configure)

    detect = subparsers.add_parser("detect", help="locate and count local OrcaSlicer profiles")
    detect.add_argument("--profile-root")
    detect.set_defaults(handler=command_detect)

    push = subparsers.add_parser("push", help="upload local profiles")
    push.add_argument("--profile-root")
    push.set_defaults(handler=command_push)

    pull = subparsers.add_parser("pull", help="download cloud profiles")
    pull.add_argument("--profile-root")
    pull.add_argument("--force", action="store_true", help="backup and replace changed local files")
    pull.set_defaults(handler=command_pull)
    return result


def main() -> int:
    try:
        args = parser().parse_args()
        args.handler(args)
        return 0
    except (RuntimeError, OSError, ValueError, json.JSONDecodeError) as error:
        print(f"error: {error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
