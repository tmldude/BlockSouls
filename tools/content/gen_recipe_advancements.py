#!/usr/bin/env python3
"""Generates a vanilla recipe-unlock advancement for every BlockSouls recipe, so recipes show up in the crafting
book (and furnace book) as soon as a player holds one of the recipe's key ingredients.

For recipe  <module>/src/main/resources/data/<ns>/recipe/<name>.json  it writes
            <module>/src/main/resources/data/<ns>/advancement/recipes/<category>/<name>.json
with the same shape vanilla's data generator uses: a `recipe_unlocked` criterion, one `inventory_changed` criterion
per key ingredient (any one is enough), reward = the recipe, and the recipe's own `fabric:load_conditions` (plus any
module an extra key needs), because one unknown id fails the whole 26.3 advancement registry.

Key ingredients: the recipe's BlockSouls ingredients; a recipe made only of vanilla items uses all of them.
EXTRA_KEYS below adds more for discoverability. Run after adding or changing a recipe:

    python3 tools/content/gen_recipe_advancements.py           # write / update the files
    python3 tools/content/gen_recipe_advancements.py --check   # fail if any are missing or stale (CI, via check_recipe_web.py)
"""
import glob
import json
import os
import sys

ROOT = os.path.normpath(os.path.join(os.path.dirname(__file__), "..", ".."))
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from check_recipe_web import ingredient_refs  # noqa: E402

# Extra unlock keys per recipe id, beyond its own ingredients. Scrap is the first BlockSouls item a player finds, so
# holding it also shows where gunmetal comes from (smelting raw gunmetal / ore) in the furnace book.
_GUNMETAL_SMELTING = [f"blocksouls_world:gunmetal_ingot_from_{kind}_{src}"
                      for kind in ("smelting", "blasting")
                      for src in ("raw_gunmetal", "gunmetal_ore", "deepslate_gunmetal_ore")]
EXTRA_KEYS = {r: ["blocksouls_world:scrap"] for r in _GUNMETAL_SMELTING}

# Book categories that exist in 26.3 recipe JSON; anything else (smithing, stonecutting) files under misc.
CATEGORIES = {"building", "redstone", "equipment", "misc", "food", "blocks"}


def load(path):
    with open(path, encoding="utf-8") as f:
        return json.load(f)


def recipe_files():
    for path in sorted(glob.glob(f"{ROOT}/*/src/main/resources/data/*/recipe/**/*.json", recursive=True)):
        ns = path.split("/data/")[1].split("/")[0]
        name = path.split("/recipe/")[1][:-5]
        yield path, f"{ns}:{name}"


def advancement_path(recipe_path, recipe):
    category = recipe.get("category")
    folder = category if category in CATEGORIES else "misc"
    data_dir, name = recipe_path.split("/recipe/")
    return f"{data_dir}/advancement/recipes/{folder}/{name}"


def key_ingredients(recipe, recipe_id):
    refs = []
    for ref in ingredient_refs(recipe):
        if ref not in refs:
            refs.append(ref)
    ours = [r for r in refs if r.lstrip("#").startswith("blocksouls")]
    keys = ours or refs
    for extra in EXTRA_KEYS.get(recipe_id, []):
        if extra not in keys:
            keys.append(extra)
    return keys


def criterion_name(ref):
    return "has_" + ref.lstrip("#").split(":", 1)[1].replace("/", "_")


def module_of(ref):
    ns = ref.lstrip("#").split(":", 1)[0]
    return ns if ns.startswith("blocksouls_") else None


def build(recipe, recipe_id):
    keys = key_ingredients(recipe, recipe_id)
    criteria = {"has_the_recipe": {"trigger": "minecraft:recipe_unlocked", "conditions": {"recipes": recipe_id}}}
    for ref in keys:
        criteria[criterion_name(ref)] = {
            "trigger": "minecraft:inventory_changed",
            "conditions": {"items": [{"items": ref}]},
        }
    adv = {}
    own = recipe_id.split(":", 1)[0]
    mods = []
    for cond in recipe.get("fabric:load_conditions", []):
        if cond.get("condition") == "fabric:all_mods_loaded":
            mods += [m for m in cond.get("values", []) if m not in mods]
    mods += [m for m in (module_of(r) for r in keys) if m and m != own and m not in mods]
    other = [c for c in recipe.get("fabric:load_conditions", []) if c.get("condition") != "fabric:all_mods_loaded"]
    if mods or other:
        adv["fabric:load_conditions"] = ([{"condition": "fabric:all_mods_loaded", "values": mods}] if mods else []) + other
    adv["criteria"] = criteria
    adv["requirements"] = [list(criteria)]
    adv["rewards"] = {"recipes": [recipe_id]}
    return adv


def dump(obj):
    return json.dumps(obj, indent="\t") + "\n"


def expected():
    """{advancement path: file text} for every recipe."""
    out = {}
    for path, recipe_id in recipe_files():
        recipe = load(path)
        out[advancement_path(path, recipe)] = dump(build(recipe, recipe_id))
    return out


def existing():
    return set(glob.glob(f"{ROOT}/*/src/main/resources/data/*/advancement/recipes/**/*.json", recursive=True))


def rewarded_recipes(path):
    try:
        return set(load(path).get("rewards", {}).get("recipes", []))
    except (OSError, ValueError):
        return set()


def problems():
    """Recipes without an unlock advancement, and unlock advancements whose recipe is gone (which would fail the
    advancement registry: recipe_unlocked can't name a missing recipe)."""
    out = []
    want = expected()
    have = existing()
    recipes = {rid for _, rid in recipe_files()}
    rewarded = {}
    for path in have:
        for rid in rewarded_recipes(path):
            rewarded.setdefault(rid, []).append(path)
    for path, rid in recipe_files():
        if rid not in rewarded:
            out.append(f"recipe {rid} has no unlock advancement (run tools/content/gen_recipe_advancements.py)")
    for path in sorted(have):
        for rid in rewarded_recipes(path):
            if rid not in recipes:
                out.append(f"{os.path.relpath(path, ROOT)} unlocks {rid}, which isn't a recipe any more")
    for path, text in want.items():
        if path in have:
            with open(path, encoding="utf-8") as f:
                if f.read() != text:
                    out.append(f"{os.path.relpath(path, ROOT)} is stale (run tools/content/gen_recipe_advancements.py)")
    return out


def main():
    if "--check" in sys.argv:
        found = problems()
        for p in found:
            print(p)
        print("recipe unlock advancements OK" if not found else f"\n{len(found)} recipe unlock problem(s)")
        return 1 if found else 0
    want = expected()
    for path in existing() - set(want):
        os.remove(path)
        print("removed", os.path.relpath(path, ROOT))
    for path, text in want.items():
        os.makedirs(os.path.dirname(path), exist_ok=True)
        old = None
        if os.path.exists(path):
            with open(path, encoding="utf-8") as f:
                old = f.read()
        if old != text:
            with open(path, "w", encoding="utf-8") as f:
                f.write(text)
            print("wrote", os.path.relpath(path, ROOT))
    return 0


if __name__ == "__main__":
    sys.exit(main())
