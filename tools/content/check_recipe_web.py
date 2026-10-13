#!/usr/bin/env python3
"""Checks the crafting web (the content standard in docs/CONTENT.md, "Crafting"):

  R1  every BlockSouls item that drops (mob, boss, chest or ore loot) is an ingredient in at least 2 recipes;
  R2  every BlockSouls item that is crafted connects: its recipe uses a BlockSouls item, or it is itself an
      ingredient in another recipe (no recipes that only turn vanilla items into a dead end);
  R3  every recipe has a recipe-unlock advancement (written by gen_recipe_advancements.py), so it appears in the
      recipe book as soon as a player holds an ingredient.
  R4  every shopkeeper board data file (data/<ns>/blocksouls_board/{entry,source,enchant,upgrade}, the format in
      docs/decisions/v2-boards.md) is well formed, and every entry names a recipe that exists.

Gear, trophies and other end items that are meant to be dead ends are listed with a reason in
tools/content/recipe_web_exempt.json. Run from anywhere:  python3 tools/content/check_recipe_web.py [--report]
"""
import glob
import json
import os
import re
import sys

ROOT = os.path.normpath(os.path.join(os.path.dirname(__file__), "..", ".."))
ID = re.compile(r"^#?[a-z0-9_.-]+:[a-z0-9_./-]+$")


def load(path):
    with open(path, encoding="utf-8") as f:
        return json.load(f)


def mod_items():
    items = set()
    for lang in glob.glob(f"{ROOT}/*/src/*/resources/assets/blocksouls_*/lang/en_us.json"):
        for key in load(lang):
            m = re.match(r"^(item|block)\.(blocksouls_[a-z]+)\.([a-z0-9_]+)$", key)
            if m:
                items.add(f"{m.group(2)}:{m.group(3)}")
    return items


def item_tags():
    tags = {}
    for path in glob.glob(f"{ROOT}/*/src/main/resources/data/*/tags/item/**/*.json", recursive=True):
        ns = path.split("/data/")[1].split("/")[0]
        name = path.split("/tags/item/")[1][:-5]
        values = []
        for v in load(path).get("values", []):
            values.append(v["id"] if isinstance(v, dict) else v)
        tags.setdefault(f"#{ns}:{name}", []).extend(values)
    return tags


def expand(ref, tags, seen=None):
    if not ref.startswith("#"):
        return {ref}
    seen = seen or set()
    if ref in seen:
        return set()
    seen.add(ref)
    out = set()
    for v in tags.get(ref, []):
        out |= expand(v, tags, seen)
    return out


def ingredient_refs(node):
    """Every id-looking string under a recipe, except under its result."""
    if isinstance(node, dict):
        for k, v in node.items():
            if k in ("result", "type", "category", "group", "fabric:load_conditions", "show_notification"):
                continue
            yield from ingredient_refs(v)
    elif isinstance(node, list):
        for v in node:
            yield from ingredient_refs(v)
    elif isinstance(node, str) and ID.match(node):
        yield node


def recipes(tags):
    out = []
    for path in glob.glob(f"{ROOT}/*/src/main/resources/data/*/recipe/**/*.json", recursive=True):
        r = load(path)
        result = r.get("result", {})
        result_id = result.get("id") if isinstance(result, dict) else result
        ins = set()
        for ref in ingredient_refs(r):
            ins |= expand(ref, tags)
        out.append((os.path.relpath(path, ROOT), result_id, ins))
    return out


def drops():
    """Items named in loot tables, minus blocks dropping themselves."""
    found = {}

    def walk(node, table):
        if isinstance(node, dict):
            if node.get("type") == "minecraft:item" and isinstance(node.get("name"), str):
                found.setdefault(node["name"], set()).add(table)
            for v in node.values():
                walk(v, table)
        elif isinstance(node, list):
            for v in node:
                walk(v, table)

    for path in glob.glob(f"{ROOT}/*/src/main/resources/data/*/loot_table/**/*.json", recursive=True):
        rel = path.split("/loot_table/")[1][:-5]
        ns = path.split("/data/")[1].split("/")[0]
        table = f"{ns}:{rel}"
        before = {k: set(v) for k, v in found.items()}
        walk(load(path), table)
        if rel.startswith("blocks/"):
            self_id = f"{ns}:{rel[len('blocks/'):]}"
            if self_id in found and table not in before.get(self_id, set()):
                found[self_id].discard(table)
                if not found[self_id]:
                    del found[self_id]
    return found


def recipe_ids():
    ids = set()
    for path in glob.glob(f"{ROOT}/*/src/main/resources/data/*/recipe/**/*.json", recursive=True):
        ns, rest = os.path.relpath(path, ROOT).split("/data/", 1)[1].split("/recipe/", 1)
        ids.add(f"{ns}:{rest[:-len('.json')]}")
    return ids


def board_problems():
    """R4: the shopkeeper's board data files."""
    problems = []
    known = recipe_ids()

    def ids_ok(values):
        return isinstance(values, list) and all(isinstance(v, str) and ID.match(v) and not v.startswith("#") for v in values)

    def floors_ok(d):
        lo, hi = d.get("minFloor", 1), d.get("maxFloor", 99)
        tags = d.get("floorTags", [])
        return isinstance(lo, int) and isinstance(hi, int) and 1 <= lo <= hi and isinstance(tags, list)

    for path in sorted(glob.glob(f"{ROOT}/*/src/main/resources/data/*/blocksouls_board/*/**/*.json", recursive=True)):
        rel = os.path.relpath(path, ROOT)
        kind = rel.split("/blocksouls_board/", 1)[1].split("/", 1)[0]
        try:
            d = load(path)
        except ValueError as e:
            problems.append(f"{rel} isn't valid JSON: {e}")
            continue
        if not isinstance(d, dict):
            problems.append(f"{rel} must be a JSON object")
            continue
        if kind == "entry":
            recipe = d.get("recipe")
            if not isinstance(recipe, str) or recipe not in known:
                problems.append(f"{rel} names recipe {recipe!r}, which isn't a recipe in the repo")
            if d.get("board", "forge") not in ("forge", "brew"):
                problems.append(f"{rel} board must be \"forge\" or \"brew\"")
            if not floors_ok(d):
                problems.append(f"{rel} needs 1 <= minFloor <= maxFloor and a floorTags list")
        elif kind == "source":
            if not ids_ok([d.get("item")]) or ("icon" in d and not ids_ok([d["icon"]])):
                problems.append(f"{rel} needs an item id (and an icon id if set)")
        elif kind == "enchant":
            if not ids_ok([d.get("metal")]) or not ids_ok(d.get("enchantments")) or not d.get("enchantments"):
                problems.append(f"{rel} needs a metal id and a non-empty enchantments id list")
            if not floors_ok(d) or not all(isinstance(d.get(k, 0), int) and d.get(k, 0) >= 0 for k in ("metalCount", "souls")):
                problems.append(f"{rel} needs valid minFloor/maxFloor/floorTags and metalCount/souls >= 0")
        elif kind == "upgrade":
            chain = d.get("chain")
            if not ids_ok(chain) or len(chain) < 2:
                problems.append(f"{rel} needs a chain of 2+ item ids")
        else:
            problems.append(f"{rel}: unknown board folder {kind!r} (entry, source, enchant, upgrade)")
    return problems


def main():
    report = "--report" in sys.argv
    items = mod_items()
    tags = item_tags()
    rs = recipes(tags)
    lists = load(f"{ROOT}/tools/content/recipe_web_exempt.json")
    exempt = dict(lists.get("exempt", {}))
    debt = dict(lists.get("debt", {}))
    used_in = {}
    for path, result, ins in rs:
        for i in ins:
            used_in.setdefault(i, []).append(path)
    problems = []
    for item, tables in sorted(drops().items()):
        if item not in items or item in exempt:
            continue
        n = len(set(used_in.get(item, [])))
        if item in debt:
            if n >= 2:
                problems.append(f"{item} now passes: remove it from 'debt' in recipe_web_exempt.json")
            else:
                print(f"debt: {item} drops but is in {n} recipe(s) ({debt[item]})")
            continue
        if n < 2:
            problems.append(f"R1 {item} drops ({', '.join(sorted(tables))}) but is in {n} recipe(s); needs 2+")
    crafted = {}
    for path, result, ins in rs:
        if result:
            crafted.setdefault(result, []).append(ins)
    for item, ins_lists in sorted(crafted.items()):
        if item not in items or item in exempt or item in debt:
            continue
        uses_mod = any(any(i in items for i in ins) for ins in ins_lists)
        if not uses_mod and not used_in.get(item):
            problems.append(f"R2 {item} is crafted only from vanilla items and isn't used in any recipe")
    stale = [k for k in list(exempt) + list(debt) if k not in items]
    for k in stale:
        problems.append(f"exempt list names {k}, which isn't a BlockSouls item any more")
    from gen_recipe_advancements import problems as unlock_problems
    problems += [f"R3 {p}" for p in unlock_problems()]
    problems += [f"R4 {p}" for p in board_problems()]
    if report:
        print(f"{len(items)} items, {len(rs)} recipes")
    for p in problems:
        print(p)
    if problems:
        print(f"\n{len(problems)} problem(s). Fix the recipes, or exempt a true end item with a reason in "
              "tools/content/recipe_web_exempt.json (see docs/CONTENT.md, Crafting).")
        return 1
    print("crafting web OK")
    return 0


if __name__ == "__main__":
    sys.exit(main())
