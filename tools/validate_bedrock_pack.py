#!/usr/bin/env python3
"""Structural validator for Minecraft Bedrock .mcpack/.zip resource or behavior packs.
Checks ZIP integrity, root manifest, required fields, UUIDs, modules and dependencies.
It cannot prove in-game compatibility; test the pack in the target Bedrock build.
"""
import json, sys, uuid, zipfile
from pathlib import Path

REQUIRED = ("format_version", "header", "modules")
def fail(message):
    print(f"FAIL: {message}")
    raise SystemExit(1)

def main(path):
    p = Path(path)
    if not p.is_file(): fail(f"File not found: {p}")
    try:
        with zipfile.ZipFile(p) as z:
            bad = z.testzip()
            if bad: fail(f"ZIP CRC error in {bad}")
            names = [n for n in z.namelist() if not n.endswith('/')]
            manifests = [n for n in names if n == "manifest.json" or n.endswith("/manifest.json")]
            if "manifest.json" not in manifests: fail("manifest.json must be at the archive root (no enclosing folder)")
            data = json.loads(z.read("manifest.json").decode("utf-8-sig"))
    except zipfile.BadZipFile: fail("Not a valid ZIP archive")
    except (UnicodeDecodeError, json.JSONDecodeError) as e: fail(f"Invalid manifest JSON: {e}")
    for key in REQUIRED:
        if key not in data: fail(f"Missing manifest field: {key}")
    header = data["header"]
    if not isinstance(header, dict) or not header.get("name") or not header.get("description"):
        fail("header.name and header.description must be non-empty")
    def check_uuid(value, label):
        try: uuid.UUID(str(value))
        except Exception: fail(f"Invalid {label} UUID")
    check_uuid(header.get("uuid"), "header")
    if not isinstance(header.get("version"), list) or len(header["version"]) != 3 or not all(isinstance(x, int) and x >= 0 for x in header["version"]):
        fail("header.version must be three non-negative integers")
    modules = data["modules"]
    if not isinstance(modules, list) or not modules: fail("modules must be a non-empty array")
    seen = {header["uuid"]}
    for i, mod in enumerate(modules):
        if not isinstance(mod, dict) or mod.get("type") not in {"resources", "data", "script"}:
            fail(f"modules[{i}].type is missing or unsupported")
        check_uuid(mod.get("uuid"), f"module {i}")
        if mod["uuid"] in seen: fail("UUIDs must be unique across header and modules")
        seen.add(mod["uuid"])
        if not isinstance(mod.get("version"), list) or len(mod["version"]) != 3:
            fail(f"modules[{i}].version must contain three integers")
    for i, dep in enumerate(data.get("dependencies", [])):
        if not isinstance(dep, dict): fail(f"dependencies[{i}] must be an object")
        if dep.get("uuid"): check_uuid(dep["uuid"], f"dependency {i}")
        if not dep.get("uuid") and not dep.get("module_name"): fail(f"dependencies[{i}] needs uuid or module_name")
    print("PASS: ZIP integrity, root manifest, metadata, UUIDs, modules and dependencies look structurally valid.")
    print("NOTE: In-game import and version/API compatibility are not tested by this script.")

if __name__ == "__main__":
    if len(sys.argv) != 2: print("Usage: python validate_bedrock_pack.py path/to/pack.mcpack"); raise SystemExit(2)
    main(sys.argv[1])
