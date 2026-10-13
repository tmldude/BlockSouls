# Floors 1–6: design brief (accepted by the owner 2026-10-13, first draft to build)

Status: **accepted as the first draft.** Owner's call: build all of it, then playtest floors 1–5 and the final boss
and tune from feel. Each floor's block is the template its worker session gets. Where this conflicts with
PLAN2.md §1 it supersedes it (DECISIONS.md "Floors 2–6 first draft").

Mechanics every floor shares (already built or contracted): island map in the void with a hard edge, 8-minute
day (config per floor), camp + shopkeeper + horn at the centre, exit portal lit by surviving the siege, slots
(structure / mini-boss / secret / event / cave / wreck / nodes / crates) filled from the template, escalation
tags per floor (`more_elites`, `stingy_nodes`, `mean_mini_boss`, `elite_affixes`), party scaling 1–8,
Director-placed day mobs. Everything breaks (terrain and walls are tactical, not an economy).

## The two day-lanes (new, owner 2026-10-13)

The day is too short to do everything. Each floor offers **two lanes** and a fast party does one, maybe both:

| Lane | What you gather | Where you turn it in | What you get |
|---|---|---|---|
| **Ore → gear** | floor metal nodes (scrap, gunmetal, floor rare) | shopkeeper **forge board** | armor pieces, gun reroll/upgrade, enchantment |
| **Herb → consumables** | floor herb / organism (visible, rare, often guarded) | shopkeeper **brew board** | heals, buffs, antidotes, siege-night tonics |

**Crafting leaves the player's hands.** No crafting grid for BlockSouls items: the shopkeeper's boards are the only
converter (gather → trade → item). Vanilla crafting stays for vanilla blocks. Consequence for CI: the recipe-web
check (`tools/content/check_recipe_web.py`) must count **trade-board entries** as recipes, or it fails every drop.
**Owner decision needed (D1).**

## Floor order (proposed, replaces PLAN2 §1's list)

| # | Working name | One line | Theme source |
|---|---|---|---|
| 1 | **Surface** | the built one: fields, ridge, scrapfield | overworld |
| 2 | **Infernal** | nether-like: basalt, lava channels, soulfire, fortress ruin | owner: "at least like the nether" |
| 3 | **Skyrift** | aether-like: floating shards, bright, wind, long falls | owner: "the opposite, the aether" |
| 4 | **The Vault** | stronghold / dungeon: bounded box, breakable walls, rooms, no sky | owner: "like a stronghold, breakable walls" |
| 5 | **Blighted Surface** | floor 1 hardened and worn: corrupted, mobs everywhere even by day, roaming bosses, random zone problems | owner: "first floor but worn down, much more dangerous" |
| 6 | **Final Arena** | boss arena only: phase 1, phase 2, damage-rush | owner |

Caverns (old floor 2) folds into the **cave slot** on floors 1, 2 and 5 and into the Vault. Blightheart (old
floor 4) becomes floor 5's **theme and roaming boss**, not its own floor. **Owner decision needed (D2).**

## Per-floor template (fill this for each; drafts below)

```
Vibe / palette:         blocks, sky tint, ambient sound, time-of-day look
Rare resource:          one node + one herb; what each turns into at the boards
Hazard:                 the thing the floor does to you (lava, falls, dark, blight)
Mob roster feel:        3–4 words + which v1/v2 mobs lead; day-mob density
Mini-boss pool:         2–3 names from bosses v2 archetypes
Structure kind:         what the one structure is and what its chest holds
Secret mechanism:       how you find/open it (Gungeon-style), what it gives
Events (2–3):           the "?" choices with a risk
Siege twist:           what the night does differently here
```

### Floor 1 — Surface (built; needs only the herb lane)
- Rare: `amber_resin` (node) + **draft herb: sunroot** (open fields, pops in 1 hit, 2–3 per floor).
- Hazard: none; the tutorial floor. Secret: cracked wall (built). Mini-boss: current field pool.
- Siege twist: none. Escalation: none.

### Floor 2 — Infernal (draft)
- Vibe: basalt + blackstone + magma, red sky tint, soulfire at the camp, lava channels cut the map into 3–4 lobes
  joined by narrow bridges (breakable: cut a bridge, cut a siege lane).
- Rare: **cinder ore** node (gun fire perk reroll, heat armor piece) + **ashcap** fungus herb (fire resistance
  tonic, burn damage buff). Resin absent.
- Hazard: lava and fire; falling into a channel is the death plane with the fall penalty.
- Mobs: fast, burning affix common, ranged spitters; piglin-style brutes as elites. Day-mob density medium.
- Mini-boss pool: Ashwyrm (exists), a Brute archetype, a Hound-pack archetype.
- Structure: fortress stub (3 rooms, bastion chest: heat armor or a fire gun), wall-buy.
- Secret: **douse the sealed fire door** with a water bucket from the floor's one water pocket (carry it across
  a lava bridge). Gives an exotic.
- Events: soul-fire altar (burn a boon for a legendary roll), lava-locked cursed chest (reach it before the
  bridge sinks), stranded trader.
- Siege twist: lava rises one block at the midpoint; the camp lobe is the island.

### Floor 3 — Skyrift (draft)
- Vibe: pale stone, white/blue grass, floating shards above and around the main isle, clouds below the edge,
  gold sky tint. Wind pushes slightly at the rim. The swing/climb kit shines here.
- Rare: **skysteel** node (mostly on the floating shards: climb or swing to reach) + **cloudbloom** herb
  (slow-fall / double jump tonic, feather armor piece).
- Hazard: falls. No glass wall: the edge is the drop; death plane with fall penalty.
- Mobs: flyers and leapers, frosted affix common, snipers on shards. Day-mob density medium-high.
- Mini-boss pool: Stormplate (exists), a Roc/flyer archetype, a Glider-pack archetype.
- Structure: sky temple (vertical, 3 tiers, chest at top: feather armor or a long-range gun).
- Secret: **carry the glowing seed from the temple to the camp altar without taking a hit**; it dims on damage.
  Gives a legendary boon.
- Events: wind altar (gamble a boon for a movement rune shard), falling-shard chest (timed grab), ambush on a
  bridge.
- Siege twist: waves arrive from the shards above; some siege rings are elevated.

### Floor 4 — The Vault (draft)
- Vibe: stronghold / dungeon. A bounded stone box (radius as a floor, ceiling at ~24 blocks, no sky) with a
  room-and-corridor layout; walls breakable (stone bricks, 2–3 hits with a burst), bedrock shell at the bound.
  Dark; lanterns are loot. The siege ring is the corridors.
- Rare: **deep iron** node (walls hide them; mining into a wall can open a shortcut or a mob closet) + **cave
  moss** herb (night-vision tonic, poison antidote).
- Hazard: darkness and dead ends; breaking the wrong wall releases a closet of mobs.
- Mobs: slow heavy melee, bulwarks, volatile affix common, silverfish-style swarms from broken walls.
- Mini-boss pool: a Warden-like blind hunter archetype, a Golem archetype.
- Structure: the vault proper (the locked treasure room: 3 keys scattered in other rooms → gear set piece).
- Secret: **a hollow wall** (sounds different when hit, drips) opens to a hidden stair → a second mini-boss
  with a trinket pick.
- Events: library (trade a boon for a reroll of all your boons), prison (free an ally NPC who fights the siege),
  trapped hall (dodge course for souls).
- Siege twist: no horn buff beyond 1 tier; waves come through corridors so barricade kits matter.

### Floor 5 — Blighted Surface (draft)
- Vibe: floor 1's templates re-skinned: dead grass, blight tendrils, purple fog, broken outposts, the sky dark
  even at noon. Everything from floor 1 is here but worse.
- Rare: **blight crystal** node (top-tier gun perk, cursed armor piece) + **ghostcap** herb (the only full
  heal; also the floor-6 revive tonic).
- Hazard: **danger scaling**. Mobs everywhere by day; **roaming bosses** (Blightheart as the signature, plus
  a second field boss) walk the map; **zone problems** fire on a timer (blight storm: a moving circle that
  damages, a spawn surge, a dead-camp window where the shop closes for 60 s).
- Mobs: every roster, all affixes on elites, `more_elites` + `elite_affixes` + `mean_mini_boss`.
- Mini-boss pool: Blightheart, Pale Rider (exists), one Director-picked from any earlier floor.
- Structure: the ruined outpost from floor 1, now a nest (chest holds a legendary and a cursed chest next to it).
- Secret: **the grave**: dig the one soft-dirt patch the fog hides; gives the run's exotic.
- Events: blood altar, soul wager (stake souls, nobody downs → 3×), challenge pylon.
- Siege twist: two sieges (the horn ends the first; the second comes at full dark); surviving both lights the
  portal.

### Floor 6 — Final Arena (draft)
- One arena, no day. The camp is the arena's edge (shop sells only tonics and ammo). Three phases:
  **P1** the boss (bosses v2 posture + part breaks), **P2** the boss transformed + adds from floor 5's roster,
  **P3 damage rush**: a timed window (60 s, config) where the boss is exposed and the party must deal X% HP or
  it heals back to P2. Win → run end, souls kept, rune tree.
- Hazard: arena hazards scale with party (PartyScaling).
- Secret: none on the floor; the **secret floor** (below) is reached from here or from floor 5.

## Secret floors (to think about later; two candidate mechanisms)
- **Gungeon-style**: a specific floor secret (the doused fire door on 2, the hollow wall on 4) leads to a
  **side floor** (a 7th template pool, one floor, no shop, one boss, one guaranteed exotic) that returns to the
  next normal floor.
- **Hades-style**: an NPC met in events across floors asks for a token; carry it to floor 6 unhit → an alternate
  boss (harder, better ending, a rune).

## Owner decisions (2026-10-13)
- **D1** Crafting tables are **disabled** in floors runs. Consumables keep *recipes*, but every BlockSouls recipe is
  crafted **at the shopkeeper**: the **brew board** executes consumable recipes, the **forge board** executes gear
  recipes, the trade board stays the offer pool. Recipes stay JSON so the recipe-web check keeps working; player
  crafting grids never match them.
- **D2** Floor order accepted: Surface → Infernal → Skyrift → The Vault → Blighted Surface → Final Arena.
- **D3** The two day-lanes (ore → forge, herb → brew) on every floor. Accepted.
- **D4** Floor 5 keeps two sieges in one night.
- **D5** No per-floor edits yet; the drafts above are the first build. Owner playtests floors 1–5 and the final
  boss, then tunes.

## Build plan (Stage 6C, v2 orchestrator)
Wave A, in parallel: `v2-boards` (the shopkeeper's forge + brew boards, crafting tables off) and one session per
floor 2–5 (`v2-floor2-infernal`, `v2-floor3-skyrift`, `v2-floor4-vault`, `v2-floor5-blight`). Each floor session
owns its `world` templates, blocks, items, loot and decisions file, with small lane exceptions in `mobs`,
`bosses` and `game` listed in its brief. Wave B after the boards merge: `v2-floor6-arena`. Briefs in
`docs/sessions/`. Then a playtest release cut by the v1 orchestrator.
