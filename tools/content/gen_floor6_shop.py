#!/usr/bin/env python3
"""Floor 6 (the Final Arena) sells tonics and ammo only (docs/decisions/v2-floor6-arena.md).

Board entries only narrow, so every other BlockSouls recipe gets an entry capping it at floor 5:
world/.../blocksouls_board/entry/floor6_arena/<namespace>__<path>.json = {"recipe": id, "maxFloor": 5}.

Kept (no file): recipes whose result is ammo (#blocksouls:ammo, the brew board tag), a tonic, antidote, draught or broth.
A recipe that already has its own entry file elsewhere gets no second file (the last loaded would win); that file
gets "maxFloor": 5 written into it instead, unless it starts on floor 6 or later.
Rerun after adding recipes:  python3 tools/content/gen_floor6_shop.py [--check]
"""
import glob
import json
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
OUT = f"{ROOT}/world/src/main/resources/data/blocksouls_world/blocksouls_board/entry/floor6_arena"
LAST_FLOOR = 5
TONIC = re.compile(r"(tonic|antidote|draught|broth|ammo)")


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


def kept(rid, result, keep_items):
    return result in keep_items or TONIC.search(rid) or TONIC.search(result)


def capped(text):
    """The entry file's text with maxFloor capped at the last floor before the arena (minimal edit, keeps format)."""
    entry = json.loads(text)
    if entry.get("minFloor", 1) > LAST_FLOOR or entry.get("maxFloor", 99) <= LAST_FLOOR:
        return text
    if "maxFloor" in entry:
        return re.sub(r'("maxFloor"\s*:\s*)-?\d+', rf"\g<1>{LAST_FLOOR}", text, count=1)
    body = text.rstrip()
    assert body.endswith("}"), body
    head = body[:-1].rstrip()
    indent = re.search(r'\n([ \t]+)"', text)
    sep = f"\n{indent.group(1)}" if indent else " "
    return f'{head},{sep}"maxFloor": {LAST_FLOOR}{chr(10) if indent else " "}}}' + text[len(body):]


def wanted():
    """(files to write into OUT, {other entry file path: capped text})."""
    keep_items = tag_items("ammo") | tag_items("brew_board")
    results = {}
    for path in glob.glob(f"{ROOT}/*/src/main/resources/data/blocksouls_*/recipe/**/*.json", recursive=True):
        ns, rest = path.split("/data/", 1)[1].split("/recipe/", 1)
        with open(path) as f:
            results[f"{ns}:{rest[:-len('.json')]}"] = result_id(json.load(f))
    claimed = set()
    edits = {}
    for path in sorted(glob.glob(f"{ROOT}/*/src/main/resources/data/*/blocksouls_board/entry/**/*.json", recursive=True)):
        if os.path.abspath(path).startswith(OUT):
            continue
        with open(path) as f:
            text = f.read()
        rid = json.loads(text).get("recipe")
        claimed.add(rid)
        if rid in results and not kept(rid, results[rid], keep_items) and capped(text) != text:
            edits[path] = capped(text)
    files = {}
    for path in sorted(glob.glob(f"{ROOT}/*/src/main/resources/data/blocksouls_*/recipe/**/*.json", recursive=True)):
        ns, rest = path.split("/data/", 1)[1].split("/recipe/", 1)
        rid = f"{ns}:{rest[:-len('.json')]}"
        result = results[rid]
        if rid in claimed or kept(rid, result, keep_items):
            continue
        name = f"{ns}__{rest[:-len('.json')].replace('/', '__')}.json"
        files[name] = json.dumps({"recipe": rid, "maxFloor": LAST_FLOOR}, indent=2) + "\n"
    return files, edits


def main():
    files, edits = wanted()
    have = {os.path.basename(p) for p in glob.glob(f"{OUT}/*.json")}
    if "--check" in sys.argv:
        stale = sorted(have ^ set(files)) + sorted(n for n in files if n in have and open(f"{OUT}/{n}").read() != files[n]) \
            + sorted(os.path.relpath(p, ROOT) for p in edits)
        if stale:
            print("floor 6 shop entries out of date (run tools/content/gen_floor6_shop.py):", *stale, sep="\n  ")
            return 1
        print(f"floor 6 shop OK ({len(files)} recipes capped at floor {LAST_FLOOR}, other entries capped too)")
        return 0
    os.makedirs(OUT, exist_ok=True)
    for name in have - set(files):
        os.remove(f"{OUT}/{name}")
    for name, text in files.items():
        with open(f"{OUT}/{name}", "w") as f:
            f.write(text)
    for path, text in edits.items():
        with open(path, "w") as f:
            f.write(text)
    print(f"wrote {len(files)} floor 6 shop entries, capped {len(edits)} other entries at floor {LAST_FLOOR}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
