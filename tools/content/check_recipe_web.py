#!/usr/bin/env python3
"""Checks the crafting web (the content standard in docs/CONTENT.md, "Crafting"):

  R1  every BlockSouls item that drops (mob, boss, chest or ore loot) is an ingredient in at least 2 recipes;
  R2  every BlockSouls item that is crafted connects: its recipe uses a BlockSouls item, or it is itself an
      ingredient in another recipe (no recipes that only turn vanilla items into a dead end).

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
