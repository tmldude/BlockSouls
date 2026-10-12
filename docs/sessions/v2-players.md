# Session: v2-players (Stage 6A)

**Modules:** `game` (player side) + `movement` + `common` (additions only). **Branch:** `claude/v2-players`.
**PR title:** `game+movement: origins, movement classes, solo/co-op, run summary v2`.

You build the player's identity layer: origins with movement signatures, solo Last Stand, downed
gameplay, the run summary. Read `docs/sessions/README.md`, `PLAN2.md` §0 and **§3f, §3g in full**,
`docs/CONTENT.md`, DECISIONS.md's v2 entries. Contracts you build on: `Capabilities` (#37).

## You own
- `game/player/origin/**` (new), `game/player/**` downed/revive/Last Stand changes, run summary v2.
- `movement/**`: **gate every mechanic behind `Capabilities.has`** (climb, swing, multi-jump; add
  **glide** for Zephyr — stamina-drain wings + updraft riding; placeholder updraft sources: dusk
  thermals over open ground until Skyrift lands). No attachment = all granted stays byte-for-byte v1.
- `game/client/.../hud/` **only** new files: origin-select screen, run-summary screen, downed indicator
  (v2-builds shares the lane with build-line + trinket row files; one registration line each).
- `docs/decisions/v2-players.md`.

## Origins (PLAN2 §3f)
- Data-driven origin definitions (JSON): movement capability set, kit, passive, drawback, signature
  ability id (consumed by the `abilities` module — reference by id; if `abilities` isn't merged yet the
  field is inert), weighted synergy tags.
- Ship the **ten seeds** (Drifter, Webslinger, Tidecaller, Ashborn, Zephyr, Juggernaut, Gunslinger,
  Tinkerer, Bloodbound, Gambler). Tidecaller/Ashborn immunities and penalties live here (fire/lava
  immunity, water handling, burn multipliers) — attributes/effects, not movement.
- Picked at run start via an item in the starter kit (origin-select screen); solo default configurable;
  classic shape: origins optional (default off → everyone all-granted, v1 behavior).
- Cross-training: a `Capabilities.grant` hook for boons (`v2-builds` ships the boon; you expose the hook).
- **Every origin must finish solo** — a gametest per origin that the run can start, the kit applies, and
  the capability set is exactly as declared; tests at 1 and 8 players for any party math.

## Solo & co-op (PLAN2 §3g)
- **Last Stand** (solo only, once per siege): 20 s crawl with the pistol, 3 kills → self-revive.
- Downed players keep playing: crawl, pistol at half damage, ping (keybind), screen-edge downed-teammate
  indicator.
- Party relic (majority to open) hook for v2-builds' offers (expose, don't fill).
- **Run summary v2**: styled per-player cards — waves, threat reached, acts, boss kills, best boon, build
  tags, revives given, "most time downed"; also appended to `blocksouls-runs.log` as JSON.

## Constraints
- `common` additions in their own first PR. Every number in config; `PartyScaling` and tests at 1 and 8;
  `defaultsVersion`/migrate for shipped default changes; `ConfigEvents.RELOADED` for cached values.
- `movement` is the game's best-tested feel layer: gating must not change behavior for all-granted
  players (unit test the gate; the client test's movement steps must stay green). Keep the hold-jump feel
  and roll universal (never gated).
- The owner's **movement tuning session** (v0.11 config-reload) may land new movement defaults while you
  work — rebase, don't fight it.
- Don't touch: `game/run/**`, `game/player/boon/**`, `guns/`, `abilities/`, `bosses/`, `world/`, `mobs/`,
  `vehicles/`, any `sounds.json`.
- Recipe web: the origin-select item and any kit items are end items → exempt with reasons.
- Merge latest `main` before each PR; CI green is the bar.

## Done when
- Ten origins selectable with correct movement gating (gametest each), glide works, Last Stand works solo,
  downed gameplay + indicator, run summary cards render in the client test screenshot; icons via the
  generators; decisions logged.
