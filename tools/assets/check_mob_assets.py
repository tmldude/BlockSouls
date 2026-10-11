#!/usr/bin/env python3
"""Checks the mobs module's art is complete and in sync. Exits non-zero on any problem.

    python3 tools/assets/check_mob_assets.py

- MobGeometry.java matches what mob_models.py generates (nobody hand-edited it, nobody forgot to regenerate);
- every mob texture exists at its model's texture size, and every renderer with a glow layer has its _glow.png;
- every spawn egg texture exists;
- every sound event in MobSounds.java has a sounds.json entry, and every file it names exists.
"""

import json
import os
import re
import sys

from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import mob_models  # noqa: E402

REPO = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..")
ASSETS = os.path.join(REPO, "mobs", "src", "main", "resources", "assets", "blocksouls_mobs")
CLIENT = os.path.join(REPO, "mobs", "src", "client", "java", "io", "github", "tmldude", "blocksouls", "mobs", "client")
SOUNDS_JAVA = os.path.join(REPO, "mobs", "src", "main", "java", "io", "github", "tmldude", "blocksouls", "mobs", "registry",
                           "MobSounds.java")


def main():
    problems = []
    with open(mob_models.JAVA_OUT) as f:
        if f.read() != mob_models.java():
            problems.append("MobGeometry.java is stale: run python3 tools/assets/mob_models.py")

    with open(os.path.join(CLIENT, "BlockSoulsMobsClient.java")) as f:
        client = f.read()
    glowing = set(re.findall(r'"(\w+)", [\d.]+f, true\)', client))
    for name, model in mob_models.MODELS.items():
        for suffix in [""] + (["_glow"] if name in glowing else []):
            path = os.path.join(ASSETS, "textures", "entity", name + suffix + ".png")
            if not os.path.exists(path):
                problems.append(f"missing {path}")
            elif Image.open(path).size != model.tex:
                problems.append(f"{path} is {Image.open(path).size}, model expects {model.tex}")
        egg = os.path.join(ASSETS, "textures", "item", name + "_spawn_egg.png")
        if not os.path.exists(egg):
            problems.append(f"missing {egg}")

    with open(SOUNDS_JAVA) as f:
        events = set(re.findall(r'register\("([^"]+)"\)', f.read()))
    with open(os.path.join(ASSETS, "sounds.json")) as f:
        sounds = json.load(f)
    for event in sorted(events - set(sounds)):
        problems.append(f"sound event {event} has no sounds.json entry")
    for event, entry in sounds.items():
        for s in entry["sounds"]:
            name = s["name"] if isinstance(s, dict) else s
            if isinstance(s, dict) and s.get("type") == "event":
                continue
            path = os.path.join(ASSETS, "sounds", name.split(":", 1)[1] + ".ogg")
            if not os.path.exists(path):
                problems.append(f"{event}: missing {path}")

    for p in problems:
        print("PROBLEM:", p)
    print("mob assets OK" if not problems else f"{len(problems)} problem(s)")
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main())
