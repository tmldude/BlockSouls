#!/usr/bin/env python3
"""Checks every module's sounds are complete and in sync. Exits non-zero on any problem.

    python3 tools/assets/check_sounds.py

For mobs, guns, bosses and vehicles:
- every sound event a module registers in Java has a sounds.json entry, and every entry is a registered event;
- every file a sounds.json entry names exists (no third-party sounds: an entry may not point at a vanilla event,
  except those listed in VANILLA_OK with the reason);
- every subtitle key a sounds.json entry uses has an en_us.json line.
No dependencies beyond the standard library, so CI runs it as is.
"""

import json
import os
import re
import sys

REPO = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..")
MODULES = ["mobs", "guns", "bosses", "vehicles", "abilities"]
# event id -> why it may still point at a vanilla sound. Empty: everything is original.
VANILLA_OK = {}
# Registration calls in the module's sound classes: register("id"), sound("id"), BossSounds.register("id").
REGISTER = re.compile(r'\b(?:register|sound)\("([^"]+)"\)')


def find(module, *parts):
    for source_set in ("main", "client"):
        path = os.path.join(REPO, module, "src", source_set, "resources", "assets", f"blocksouls_{module}", *parts)
        if os.path.exists(path):
            return path
    return None


def registered_events(module):
    events = set()
    root = os.path.join(REPO, module, "src", "main", "java")
    for folder, _, files in os.walk(root):
        for name in files:
            if not name.endswith(".java"):
                continue
            with open(os.path.join(folder, name)) as f:
                text = f.read()
            if "createVariableRangeEvent" in text or "BossSounds.register(" in text:
                events |= set(REGISTER.findall(text))
    return events


def check(module):
    problems = []
    sounds_path = find(module, "sounds.json")
    if not sounds_path:
        return [f"{module}: no sounds.json"]
    assets = os.path.dirname(sounds_path)
    with open(sounds_path) as f:
        sounds = json.load(f)
    lang_path = find(module, "lang", "en_us.json")
    lang = {}
    if lang_path:
        with open(lang_path) as f:
            lang = json.load(f)

    events = registered_events(module)
    for event in sorted(events - set(sounds)):
        problems.append(f"{module}: sound event {event} has no sounds.json entry")
    for event in sorted(set(sounds) - events):
        problems.append(f"{module}: sounds.json entry {event} is not a registered sound event")

    for event, entry in sounds.items():
        subtitle = entry.get("subtitle")
        if subtitle and subtitle not in lang:
            problems.append(f"{module}: {event}: subtitle {subtitle} has no en_us.json line")
        if not entry.get("sounds"):
            problems.append(f"{module}: {event}: no sounds")
        for s in entry.get("sounds", []):
            name = s["name"] if isinstance(s, dict) else s
            if isinstance(s, dict) and s.get("type") == "event":
                if not name.startswith(f"blocksouls_{module}:") and event not in VANILLA_OK:
                    problems.append(f"{module}: {event} points at another event ({name}), not an original sound")
                continue
            namespace, path = name.split(":", 1) if ":" in name else ("minecraft", name)
            if namespace != f"blocksouls_{module}":
                if event not in VANILLA_OK:
                    problems.append(f"{module}: {event} uses {name}, which isn't one of this module's sounds")
                continue
            file = os.path.join(assets, "sounds", path + ".ogg")
            if not os.path.exists(file):
                problems.append(f"{module}: {event}: missing {os.path.relpath(file, REPO)}")
    return problems


def main():
    problems = [p for m in MODULES for p in check(m)]
    for p in problems:
        print("PROBLEM:", p)
    print("sounds OK" if not problems else f"{len(problems)} problem(s)")
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main())
