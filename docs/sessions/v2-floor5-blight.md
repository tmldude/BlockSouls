# Session: v2-floor5-blight (Stage 6C, wave A)

**Modules:** `world` (templates, terrain, your floor's node + herb blocks and items, loot tables, structure, secret,
decisions) with the lane exceptions below. **Branch:** `claude/v2-floor5-blight`. **PRs:** one per slice, each CI-green before the
next: (1) template pool + terrain + hazard, (2) rare node + herb + loot + board entries, (3) structure + secret +
events + siege twist + roster tags.

Read `docs/sessions/README.md` (protocol), **`docs/design/floors.md` ("Floor 5 — Blighted Surface" is your spec; the shared mechanics and
the two day-lanes apply)**, DECISIONS.md "Floors 2–6 first draft", `docs/decisions/v2-floors.md` (template schema,
`FloorPlanner`, slots, escalation tags, how Surface was built: copy its shape), `docs/decisions/v2-mobs.md`
(`WaveMobSpec` `minFloor`/`tags`, `WaveMobs.eligible(wave, floor, tags)`, affixes), `docs/decisions/v2-bosses.md`
(archetypes, posture, part breaks: your mini-boss pool is built from these), `docs/decisions/v2-director.md` (events,
mini-boss rotation, day mobs, the horn) and `docs/CONTENT.md`.

## You own
- **Template pool** `blocksouls_world:blight`: 2–3 templates in `data/blocksouls_world/floor_template/`, `minFloor`/
  `maxFloor` = 5, tagged `blight`. Wire act 5 of `RunConfig.floorActs` to your pool (one line in `game`; lane
  exception). Extend the template schema and `FloorPlanner` only where your floor needs it (log it); Surface templates
  must keep generating identically (their tests stay green).
- **Terrain and hazard** as the brief says. Everything breakable except the bound.
- **Rare node + herb**: one node block (hit-counted like `scrap_node`, loot table `nodes/<node>`) and one herb block
  (pops in 1 hit, 2–3 per floor, visible), their items, placeholder art from `tools/assets`, `CommonIds` entries,
  and their recipes: the node's metal into an armor piece or gun part on the **forge board**, the herb into 2+
  consumables on the **brew board** (format: `docs/decisions/v2-boards.md` once that PR merges; until then write them
  as plain recipe JSON with `fabric:load_conditions` and convert in your last PR). Every drop in 2+ recipes
  (`check_recipe_web.py`), plus a `DirectorConfig.Shop.sources`-style "where to get it" entry.
- **Structure** (your kind, chest contents via a loot table), **secret** (the mechanism in the brief; a new `FloorSlot`
  tag), **events** (2–3 entries in the Director's data-driven event pool; `game` data only, no Java unless a hook is
  missing, then one surgical hook listed as a lane exception), **siege twist**.
- **Roster**: tag the existing mobs that lead on your floor (`WaveMobSpec` `minFloor`/`tags` in `mobs`; data if the
  roster is data, else a few lines of registration, lane exception) and which affixes are common here. At most
  **one** new mob, with generated art, only if the roster needs it. **Mini-boss pool**: configure 2 from the bosses
  v2 archetypes + existing bosses (data if possible; at most one new boss model, placeholder art).
- Party scaling through `PartyScaling` for anything that counts (nodes, herbs, hazard size), tested at 1 and 8.
- **Debug:** `/bs floor goto <slot>` must work on your floor; add `/bs floor hazard <trigger>` for your hazard.
- **Tests:** `FloorPlanner` unit tests for your templates (slots in bounds, hazard placement); a gametest that opens
  your floor, pops the node and the herb, and triggers the secret.
- `docs/decisions/v2-floor5-blight.md`. Lang entries for everything with a name.
- **Reskin, then escalate**: derive your templates from the three Surface ones (dead grass, blight tendrils, purple
  fog, dark sky at noon) so the floor reads as floor 1 worn down. Tags: `more_elites`, `elite_affixes`,
  `mean_mini_boss`, `stingy_nodes`.
- **Danger scaling**: day-mob density much higher (Director config per floor tag); **two roaming field bosses**
  (Blightheart as the signature, built from a bosses v2 archetype with placeholder art, plus one existing boss)
  walk the map by day; **zone problems** on a timer from a data-driven list: a moving blight storm circle that
  damages, a spawn surge, the shop closing for 60 s. `game` lane exception for the timer and the list if the
  Director's event pool can't express them; keep it one small hook.
- **Two sieges in one night** (owner D4): the horn ends the first, the second comes at full dark; the portal lights
  only after both. This is a `game/run` act config field (lane exception, small), default on for act 5 only.
- **Secret**: the grave: one soft-dirt patch hidden in the fog; digging it gives the run's exotic.
- Herb: **ghostcap**, the only full heal and the floor-6 revive tonic.

## Not yours
Other floors; the boards' Java (`v2-boards`); the Director's pacing; bosses v2 internals. Four floor sessions run at
once: expect trivial conflicts in `en_us.json`, `CommonIds`, `floorActs` and `check_recipe_web` exemptions.
**Rebase on main before every PR** and resolve them; keep your additions in their own clearly marked block.

## Done when
A floors run reaches floor 5 and gets your floor: its look, hazard, node, herb, structure, secret, an event, its
roster and mini-boss, and the siege twist, with CI green on every PR. Say so in the last PR and stop.
