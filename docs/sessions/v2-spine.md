# Session: v2-spine (Stage 5)

**Modules:** `common` (additions only) + `game` (run side). **Branch:** `claude/v2-spine`.
**Two PRs, in order:** first `common: v2 contracts (acts, sieges, threat, synergy tags, capabilities)`,
then `game: SiegeClock, acts, threat scaling (Expedition spine)`.

You build the spine of BlockSouls v2 "Expedition": the act state machine, the one-rhythm day/night
clock that works in every dimension, the threat ratchet, and the portal charge — all behind a run-shape
flag so the live pack's behavior doesn't change. Read `docs/sessions/README.md` (protocol), `CLAUDE.md`,
**`PLAN2.md` §0–§2 and §4–§5 in full** (the design you are building), and DECISIONS.md's v2 entries first.

## Hard constraints (Stage 4 / v0.10.0 runs in parallel)

- Branch from **latest `main`**; merge `main` into your branch before opening each PR.
- Do **not** touch: `game/player/**`, `game/src/client/.../client/hud/**`, anything in `bosses/`,
  `world/`, `vehicles/`, `mobs/`, or any `sounds.json` — five Stage 4 sessions own those right now.
- `common` is frozen: **additions only**, in their own PR (the first one), never changed signatures.
- **Classic mode is the default** (`game-run.json`: `"runShape": "classic"`). Classic must be the exact
  v1 behavior — same scheduler semantics, same timings. Merging your PRs must be behavior-neutral for a
  server that doesn't opt in. CI's `integration/FullPackClientTest` dusk step (gamerule advance_time
  true + `time set 12700` + wait 400 ticks for wave 1) must stay green; update it in the same PR only
  if timing semantics genuinely change (they shouldn't, in classic).
- Your PRs are reviewed and merged by the **v2 orchestrator** (session that wrote this brief), not the
  v1 orchestrator. Say in the PR when CI is green.

## You own

- `common/src/main/java/.../common/` — **new files only**: the v2 contracts below.
- `game/src/main/java/.../game/run/**` — refactor freely (this is yours alone; game-loop's Stage 1
  session is done and Stage 4 doesn't touch it).
- `game/src/client/java/.../game/client/run/**` — additions for the synthetic cycle (client phase sync;
  minimal fog/light lerp hooks — the Graphics stage polishes the look later).
- `game/src/main/resources/data/blocksouls_game/**` except `boons/`.
- `game/src/test/**`, `game/src/gametest/**` files named `Run*`/`Wave*`/`Act*`/`Siege*`/`Threat*`.
- `docs/decisions/v2-spine.md` — log every default you pick.

## Contracts to add to `common` (first PR; finalize names yourself, keep them minimal)

- **`Acts`**: the run's current act (ordinal + id + dimension id), act list (data-driven: id, dimension,
  display name — unknown/unregistered dimensions are legal, travel to them just isn't possible yet;
  Stage 6 registers the real ones). Events: `ACT_STARTED(act)`, `PORTAL_CHARGE_STARTED(act)`,
  `PORTAL_CHARGE_COMPLETED(act)` (= next act's portal ignited), `ACT_ENTERED(act)`.
- **`Threat`**: the threat counter (the v1 night/wave counter, promoted — same persisted value, renamed
  accessor; keep a deprecated alias if other modules read it), plus `Threat.scale(kind, threat)` lookups
  backed by `config/blocksouls/threat.json` curves (`bossHealth`, `bossDamage`, `waveSize` — multiplied
  with `PartyScaling`, never replacing it).
- **`SynergyTags`**: tag id registry (element/trigger/payload tags per PLAN2 §3a) + "tags of a thing"
  lookup contract. Stage 6 fills it; you only define it.
- **`Capabilities`**: per-player movement capability set (`CLIMB`, `SWING`, `MULTI_JUMP`, `GLIDE`, ...),
  synced, **default: all granted** (v1 behavior). Stage 6's origins restrict it; `movement` will gate on
  it. You define the contract + the default-all provider.

## The spine (second PR)

- **`SiegeClock`**: one interface over "what time of day is it, when is dusk/dawn", with two
  implementations — **sun-bound** (the overworld; exactly the v1 accumulator behavior) and **synthetic**
  (a persisted ~17-minute cycle for dimensions without a sun: Nether, End, future acts 3–4). Synced
  cycle phase to clients in those dimensions; client applies a modest fog-color/ambient-light lerp at
  dusk and release at dawn (the "vibe switch" — placeholder-grade is fine, make the hooks clean).
- **Expedition run shape** (`runShape: "expedition"`): the wave scheduler runs off the SiegeClock of
  **the dimension the party is in** (most non-downed players; ties → earlier act). Dusk starts a siege
  (= the v1 night-wave chain), dawn pauses it, relic drops at dawn — v1 rules, any dimension. The threat
  counter ticks every night of the run wherever the party is and never pauses.
- **Act state machine** (persisted in the run's SavedData): acts advance only via the **portal charge**:
  a player-triggered, ~3-minute defended charge (Director doesn't exist yet — spawn a wave at current
  threat via the existing wave spawn path, day or night) that on completion fires
  `PORTAL_CHARGE_COMPLETED` and marks the next act open. Physical portal blocks/frames are Stage 6; the
  charge is started by command/API for now (`/bs act charge`).
- **Classic shape** keeps the v1 code path (overworld sun, 7-night run, strongholds untouched).
- **Debug commands**: `/bs act status|advance|charge`, `/bs threat get|set <n>`, `/bs clock phase <dim>`.
- **HUD contract only:** expose threat + act + cycle phase through `ClientRun` for the HUD session later;
  don't draw HUD yourself (game-players' lane).

## Done when

- Offline-check (`typecheck.sh game common`, `runtests.sh game common`) passes; push early; **CI green
  is the bar**, including `FullPackClientTest`.
- Unit tests: threat curves (incl. party 1 and 8 via `PartyScaling` composition), both SiegeClock
  implementations (dusk/dawn edges, persistence across restart), act machine transitions.
- Gametests: classic shape still passes the existing Run/Wave gametests **unchanged**; expedition shape
  walks a scripted 4-act skeleton (acts aliased to overworld/nether for now) via `/bs act charge`,
  sieges firing from the synthetic clock in the Nether.
- `docs/decisions/v2-spine.md` lists your defaults; PR notes contract additions explicitly.
