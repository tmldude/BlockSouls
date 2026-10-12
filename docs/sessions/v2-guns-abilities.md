# Session: v2-guns-abilities (Stage 6A)

**Modules:** `guns` + **new module `abilities`** + `common` (additions only).
**Branch:** `claude/v2-guns-abilities`. **PRs, in order:** `common: ability contracts (registry,
cooldowns, cast events)` → `abilities: tactical, signature, ultimate kit` → `guns: exotics + feel pass`.

You build the owner's #1 ask for this version: cooler guns and a brand-new active-ability axis. Read
`docs/sessions/README.md` (protocol), `PLAN2.md` §0 (focus, pacing budget) and **§3j in full** (plus §3a
for tags, §3f for the origin → signature mapping), `docs/CONTENT.md`, DECISIONS.md's v2 entries.
Contracts you build on: `SynergyTags`, `Capabilities` (#37).

## You own
- `guns/**` (everything), new `abilities/**` (everything), `common` **new files only**.
- Gun/ability art via `tools/assets/art.py` / `guns.py` (CONTENT.md rules); ability + exotic sounds via
  the synthesized-audio scripts next to `mob_sounds.py` (extend v0.10's audio tooling); icons for
  abilities in `textures/gui/sprites`.
- `docs/decisions/v2-guns-abilities.md`.

## Abilities module (new Fabric mod `blocksouls_abilities`, package `...blocksouls.abilities`)
- Copy an existing module's Gradle/`fabric.mod.json`/mixins/entrypoint layout; add it to
  `settings.gradle`; check `docker/Dockerfile`, `modpack/` overrides, `release.yml`, `install.sh` and the
  version check for any **module lists** and add `blocksouls-abilities` wherever modules are enumerated.
  `common` nests jar-in-jar like the others.
- **Contracts (first PR, `common`)**: ability registry (id → definition: slot, cooldown, charge model,
  tags), per-player state attachment (cooldowns, ultimate charge; synced to owner), cast request payload
  + `AbilityEvents` (CAST, ULTIMATE_READY), keybinds via `common` `Keybinds` (three keys, SDL
  scancodes), and an "origin signature" lookup hook (`v2-players` fills origins; until then signature is
  config-selectable).
- **Kit**: tactical (frag, web bomb, fire wall, healing dart), signature (the eight in PLAN2 §3j),
  ultimate (3 seeds: storm barrage, invulnerable melee frenzy, party overshield). Charge from kills +
  precision hits (`guns` fires a common event you listen to — add it if missing). HUD: three icons +
  cooldown sweep + ultimate charge bar, in a single `abilities` HUD renderer (own module, no collision).
- Every ability declares synergy tags and reads boon stacks via `SynergyTags.of(player)` for scaling.
- Readability/fairness: no ability one-shots a player in PvP-less co-op? N/A — but friendly fire is off.

## Guns v2
- **Exotics** (one per archetype, PLAN2 §3j seeds): item components + a behavior class each; drop
  sources: field-boss trinket-style offer hook (coordinate with `v2-builds`'s offer API — if not merged
  yet, drop via boss loot tables with `roll_perks` tier `exotic`), challenge pylons later.
- **Feel pass**: muzzle/impact VFX, hit markers (precision variant), kill-confirm sound,
  **reload-cancel on roll** (roll finishes the reload at ≥80% — read roll state through `common`'s
  `PlayerStatus`/`PlayerTraits`; if no roll query exists, add a `common` event, additive).
  **Not yours:** rarity glow/beam + sting on dropped gun item entities — v0.11's `drops-and-pacing`
  session (v1 side) owns it, together with a cheaper first-gun recipe and gun-part tooltips. **Do not
  restructure existing gun item classes or gun-part lang keys**; add exotics alongside. `modpack/
  add-mods.sh` and `en_us.json` files may conflict textually with v0.11 sessions — union-resolve.
- Register a `SynergyTags.Source` for equipped guns' perks.

## Constraints
- Recipe web (CONTENT.md R1/R2): exotics are end items → exempt with reasons; every new material feeds
  2+ recipes; **Souls**: add one soul-infused recipe on your side (e.g. soul + gun parts → exotic catalyst)
  — `v2-builds` adds the other.
- Every number in config; `PartyScaling` for anything party-dependent (healing dart, overshield) with
  tests at 1 and 8; `defaultsVersion`/migrate pattern for shipped default changes; listen to
  `ConfigEvents.RELOADED`.
- Don't touch: `game/**`, `movement/**`, `bosses/`, `world/`, `mobs/`, `vehicles/`, other modules'
  `sounds.json`.
- Merge latest `main` before each PR; CI green is the bar (incl. the client test — add exotics to its
  item line-up if it has one).

## Done when
- `abilities` jar builds and ships in Docker + modpack; three ability slots usable with keybinds, HUD,
  cooldowns, ultimate charge; 5 exotics with distinct behaviors; feel pass visible in the client test
  screenshots; unit tests for charge math + cooldown math; gametests for one ability per slot and one
  exotic behavior; decisions logged.
