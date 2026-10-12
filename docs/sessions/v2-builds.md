# Session: v2-builds (Stage 6A)

**Module:** `game` (boons) + `common` (additions only). **Branch:** `claude/v2-builds`.
**PR title:** `game: synergy builds — keystones, levels, curses, trinkets`.

You build v2's build identity: the synergy engine's content side. Read `docs/sessions/README.md`
(protocol), `PLAN2.md` §0 (focus + pacing budget) and **§3a in full**, `docs/CONTENT.md`, and
DECISIONS.md's v2 entries first. Contracts you build on: `SynergyTags` and `Threat` (merged, #37).

## You own
- `game/player/boon/**` and boon JSONs (`data/blocksouls_game/boons/**`).
- New: `game/player/trinket/**`, curse content, relic-offer weighting.
- `game/client/.../hud/` — **only** new files for the build line (`BuildLineHud` etc.) and trinket row;
  do not edit existing HUD classes beyond one registration line (v2-players shares this lane: they own
  summary + origin screens).
- Boon/curse/trinket icons: add rows to the v0.10 boon icon generator (`tools/assets/`), per CONTENT.md.
- `docs/decisions/v2-builds.md`.

## Build (PLAN2 §3a is the spec; highlights)
- **Tags on everything**: every boon JSON declares synergy tags; register a `SynergyTags.Source` for
  owned boons (stacks = boon level).
- **Keystones** (rare/legendary, rule-changers): ship the five seeds (Powder Burn, Ricochet Doctrine,
  Red Ledger, Ember Step, Deadeye Wind) + 3 more of your own design; one keystone per trigger tag may be
  owned at once.
- **Levels**: duplicate pick upgrades (cap 3 pips); an "upgrade a random owned boon" card can appear.
- **Skip pays souls.**
- **Curses**: 5 seed curses (Hemorrhage, Loud, Heavy Pockets + 2 yours); API for greed objects to apply
  one (`v2-director`'s economy objects call it); purge hook for the shopkeeper. Lost on going down like
  boons.
- **Trinkets**: 3 slots, passive, survive going down (separate attachment, `copyOnDeath`); pick-1-of-3
  offer API (field bosses + pylons call it; wire to `BossEvents.KILLED` for field bosses now); ship 8
  seed trinkets incl. Dawn Clock, Last Shell, Magnet Core, Siege Lens.
- **Weighted offers**: relic cards weight toward owned tags; Wildcard unchanged. Weighting curve in
  `boons.json`.
- **Build line HUD**: top two tags by stacks, icon + name.
- Cross-player boons (PLAN2 §3g list) as boon content: "precision kills reload nearest ally", "revive
  grants both 10 s i-frames", sworn-partner stamina share (partner chosen by sneak-use on a player).

## Constraints
- `common` additions (if any) in their own first PR. Everything data-driven; every number in config;
  party-dependent values through `PartyScaling` (test at 1 and 8).
- Recipe web: trinkets are end items → `tools/content/recipe_web_exempt.json` with reasons. Souls must
  feed 2+ recipes — coordinate: you add one (e.g. soul + materials → trinket reroll item), v2-guns adds
  the other; note it in your PR.
- Config defaults follow the `defaultsVersion`/migrate pattern (#42) if you change any shipped default.
  Listen to `ConfigEvents.RELOADED` (#41) for anything cached.
- Don't touch: `game/run/**` (spine/director lane), `guns/`, `movement/`, `abilities/`, `bosses/`,
  `world/`, `mobs/`, `vehicles/`, any `sounds.json`.
- Merge latest `main` before each PR; CI green is the bar.

## Done when
- 20+ boons tagged, 8 keystones, 5+ curses, 8 trinkets, levels, skip-for-souls, weighted offers, build
  line — all working in gametests (offer weighting unit-tested; trinkets survive a down in a gametest;
  curse purge works; party 1 and 8 covered); icons generated; decisions logged.
