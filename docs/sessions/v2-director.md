# Session: v2-director (Stage 6A — starts after PR #40, the spine, **and** v0.11's `drops-and-pacing` are merged; expect `RunConfig.CURRENT_DEFAULTS` = 2 and the new wave-clear reward code in `game/run`)

**Modules:** `game` (run side) + `world` (economy structures) + `common` (additions only).
**Branch:** `claude/v2-director`. **PR title:** `game: AI Director + economy objects (pacing budget)`.

You own v2's pacing. The owner's verdict on the playtest was "remarkably terribly slow"; the Director is
the system that owns the fix. Read `docs/sessions/README.md`, `PLAN2.md` **§0 (pacing budget), §3b, §3c,
§3e**, `docs/CONTENT.md`, DECISIONS.md's v2 entries, and `docs/decisions/v2-spine.md` (how SiegeClock and
the act machine work). You build on #37 (contracts) and #40 (spine) — both merged before you start.

## You own
- `game/run/director/**` (new) and hooks into `game/run/**`'s scheduler (the spine's wave chaining —
  edit surgically; the spine session is done, you inherit the lane).
- `world/`: the economy structures (blood altar, cursed chest, challenge pylon) as registered
  structures/blocks per CONTENT.md; camp **shopkeeper** entity + trades + overclock horn + soul wager
  (block/entity side). Logic for all of them lives in `game` behind `common` events.
- `docs/decisions/v2-director.md`.

## Director (PLAN2 §3b)
- **Stress model** per party: recent damage taken, downs, ammo state, DPS trend, boon count vs threat.
- **Levers, all config-bounded**: spawn rate and burst size, composition (flankers behind a camping party,
  brutes at a fortified choke), spawn direction via a coarse **influence map** of player pressure (so
  hordes flank instead of conga-lining), lulls and spikes.
- **The pacing budget is yours**: enforce "no stretch > ~90 s without a decision or threat" — a lull
  ceiling, intermission tightening, and quiet-day pressure (a scout pack during long prep). Make the
  budget a config constant and log violations in the decision log (`/bs director log`).
- Solo tuning through `PartyScaling` inputs: fewer, scarier (more elites) at 1 player.
- Portal-charge sieges and siege objectives (§3e: 4 seed objectives, extra relic on success) run through
  the Director.

## Economy (PLAN2 §3c)
- Blood altar (pay hearts or a boon → legendary roll, or a curse instead of hearts); cursed chest (loot +
  next siege modifier up a tier + a curse); challenge pylon (60 s shrinking circle → legendary / trinket
  offer); shopkeeper (ammo, common guns, barricade kits, perk reroll for souls, **curse purge**, stock by
  act, comments on threat); soul wager at siege start; overclock horn.
- Curses/trinket offers via `v2-builds`'s APIs (merge order: if theirs isn't in, stub behind an
  interface in `common` and note it).
- Camp placement: near the arrival portal / run start, as a registered structure; the first camp
  generates at run start in Act 1.

## Constraints
- `common` additions in their own first PR. Every number in config; `PartyScaling` + tests at 1 and 8;
  `defaultsVersion`/migrate for shipped default changes; `ConfigEvents.RELOADED` for cached curves.
- **Recipe web**: altar/chest/pylon/horn blocks are craftable? No — they generate; but any drop they add
  must satisfy R1. Souls' two recipes are handled by builds/guns; don't add a third dead end.
- `world` lane: v0.11's `resources` session changes world **gen defaults** (ore/scrap). Your structures
  are new files; don't touch gen configs. Coordinate through the orchestrator if a file collides.
- Don't touch: `guns/`, `abilities/`, `movement/`, `bosses/`, `mobs/`, `vehicles/`, `game/player/**`,
  game client HUD (expose Director/economy state through `ClientRun` only).
- Classic run shape must stay behavior-neutral: the Director runs only in expedition unless
  `director.enabledInClassic` (default false — a v0.11+ playtest may flip it).
- Merge latest `main` before each PR; CI green is the bar.

## Done when
- A scripted expedition gametest shows the Director modulating spawns under scripted stress, the lull
  ceiling holding, influence-map spawn placement flanking a mock party, one objective completing; economy
  objects each have a gametest; unit tests for stress scoring and the influence map at 1 and 8 players;
  `/bs director log` readable; decisions logged.
