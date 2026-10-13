#!/usr/bin/env python3
"""Floor 6 (the Final Arena) sells tonics and ammo only (docs/decisions/v2-floor6-arena.md).

Board entries only narrow, so every other BlockSouls recipe gets an entry capping it at floor 5:
world/.../blocksouls_board/entry/floor6_arena/<namespace>__<path>.json = {"recipe": id, "maxFloor": 5}.

Kept (no file): recipes whose result is ammo (#blocksouls:ammo, the brew board tag), a tonic or antidote, and
recipes that already have their own entry file elsewhere (that file decides; two entries for one recipe would
make the last loaded win). Rerun after adding recipes:  python3 tools/content/gen_floor6_shop.py [--check]
"""
import glob
import json
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
OUT = f"{ROOT}/world/src/main/resources/data/blocksouls_world/blocksouls_board/entry/floor6_arena"
LAST_FLOOR = 5
TONIC = re.compile(r"(tonic|antidote|ammo)")


def tag_items(name):
    items = set()
    for path in glob.glob(f"{ROOT}/*/src/main/resources/data/*/tags/item/{name}.json"):
        with open(path) as f:
            items.update(v if isinstance(v, str) else v.get("id", "") for v in json.load(f).get("values", []))
    return items


def result_id(recipe):
    result = recipe.get("result")
    if isinstance(result, dict):
        return result.get("id", result.get("item", ""))
    return result if isinstance(result, str) else ""


def wanted():
    keep_items = tag_items("ammo") | tag_items("brew_board")
    claimed = set()
    for path in glob.glob(f"{ROOT}/*/src/main/resources/data/*/blocksouls_board/entry/**/*.json", recursive=True):
        if not os.path.abspath(path).startswith(OUT):
            with open(path) as f:
                claimed.add(json.load(f).get("recipe"))
    files = {}
    for path in sorted(glob.glob(f"{ROOT}/*/src/main/resources/data/blocksouls_*/recipe/**/*.json", recursive=True)):
        ns, rest = path.split("/data/", 1)[1].split("/recipe/", 1)
        rid = f"{ns}:{rest[:-len('.json')]}"
        with open(path) as f:
            result = result_id(json.load(f))
        if rid in claimed or result in keep_items or TONIC.search(rid) or TONIC.search(result):
            continue
        name = f"{ns}__{rest[:-len('.json')].replace('/', '__')}.json"
        files[name] = json.dumps({"recipe": rid, "maxFloor": LAST_FLOOR}, indent=2) + "\n"
    return files


def main():
    files = wanted()
    have = {os.path.basename(p) for p in glob.glob(f"{OUT}/*.json")}
    if "--check" in sys.argv:
        stale = sorted(have ^ set(files)) + sorted(n for n in files if n in have and open(f"{OUT}/{n}").read() != files[n])
        if stale:
            print("floor 6 shop entries out of date (run tools/content/gen_floor6_shop.py):", *stale, sep="\n  ")
            return 1
        print(f"floor 6 shop OK ({len(files)} recipes capped at floor {LAST_FLOOR})")
        return 0
    os.makedirs(OUT, exist_ok=True)
    for name in have - set(files):
        os.remove(f"{OUT}/{name}")
    for name, text in files.items():
        with open(f"{OUT}/{name}", "w") as f:
            f.write(text)
    print(f"wrote {len(files)} floor 6 shop entries")
    return 0


if __name__ == "__main__":
    sys.exit(main())
