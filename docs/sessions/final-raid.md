# Session: final-raid (Stage 2b, dedicated)

**Module:** `bosses` (package `bosses/raid/`). **Branch:** `claude/final-raid`.
**PR title:** `bosses: final dragon raid`.

Rework the Ender Dragon into the run's finale: a multi-phase, mechanics-driven raid boss for 1–4 players in The End,
built on the framework from the `bosses` session. Its death switches the run to endless mode. Read
`docs/sessions/README.md`, PLAN.md §0b and §6 D3 (+ D3b for mechanic ideas), and **`bosses/README.md`** first.

## You own
`bosses/src/*/java/.../bosses/raid/**`, its assets/data (`final_dragon*`, `raid*` paths), any mixins into vanilla
End/dragon classes, and one registration line each in the shared bosses init/client init. Other sessions work in
`bosses/` in parallel: don't touch their packages or the framework; minimal framework additions only, listed under
"Framework changes" in your PR.

## Contract
- Boss id `CommonIds.FINAL_BOSS`. Fire `BossEvents.ENGAGED` when the fight starts and `BossEvents.KILLED` with every
  participant when it dies — the game module switches to endless mode on that event.
- Vanilla End flow must still work afterward: exit portal, egg, gateway, End cities, re-summoning is **disabled**
  (one final boss per run; decide what crystals do after, log it).

## Approach
Replace the vanilla dragon in the fight (study `EndDragonFight` and `EnderDragon` in the vanilla source): either
mixin `EndDragonFight` to spawn our `FinalDragon` entity instead and mark the fight finished on its death, or
re-drive the vanilla dragon's phase manager. Pick the one with fewer, safer mixins; log why. The arena is the vanilla
main island with its obsidian pillars and crystals.

## The fight (scale numbers by player count; solo must be possible)
- **P1 — The Crystal Order (100–70%):** the dragon is shielded while any crystal lives. Crystals on the pillars
  light up in a **sequence shown by colored beams/sounds** on a central obelisk display (show, don't tell); they must
  be destroyed **in that order**. Wrong crystal → it explodes harmlessly, the dragon heals 5% and the remaining order
  reshuffles. Meanwhile the dragon strafes: *breath line* (telegraphed lane of dragon breath), *dive* (1.2 s
  screech + shadow on the ground, then a line dive).
- **P2 — Grounding (70–30%):** the dragon circles out of reach. **Pressure plates** (glowing obsidian plates spawned
  at 2–4 spots, count = min(players, 2) need to be held) — while all required plates are held for 3 s, chains of
  light pull the dragon down to perch for a **DPS window** (8 s, takes 1.5× damage, head weak point 2×). Perched
  attacks: *tail sweep*, *wing gust* (knockback toward the void edge — telegraphed), *void orbs* (slow homing
  projectiles that pop on contact). Endermen are pacified during the fight (vanilla annoyance off).
- **P3 — Fall (30–0%):** enraged, faster; *meteor shower* telegraph circles, breath sweeps across the island,
  perches more often but for shorter. **Soft enrage** after 12 minutes total: damage +50%.
- **Wipe:** if every participant is down/dead, the dragon heals to the start of the current phase and the crystals
  reset (players can retry after being revived/respawning).
- **Health:** 1000 base (framework scaling: +50% per extra player, × difficulty).

## Look
Custom model (not the vanilla dragon model): a gaunt, void-black dragon with violet cracks, larger wings, a crown of
horns; palette `bone` + violet. Telegraphs in violet/white. Boss music placeholder.

## Drops
Vanilla dragon XP + egg + a big legendary drop: 2 legendary guns (`roll_perks`), ammo, and a unique trophy item.

## Tests
- Unit: crystal order generation/reshuffle, plate requirement by player count, enrage timer, phase thresholds.
- Gametests (End-free where possible: test the phase machine and mechanics against scripted health/inputs): order
  logic, wrong-crystal heal, plates gate the perch, wipe resets the phase, death fires `BossEvents.KILLED` with
  `FINAL_BOSS`.

## Done when
CI green; entering The End starts the raid; a 2-player test kill takes ~8–12 minutes and every mechanic is learnable
without text; killing it fires the event (endless mode once game is merged); `docs/decisions/final-raid.md`.
