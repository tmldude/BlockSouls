# Decisions: final-raid (Stage 2b)

Defaults picked by the `final-raid` session. Same rules as `DECISIONS.md` (the root file wins on conflict).
Code: `bosses/src/main/java/.../bosses/raid/`, client in `.../bosses/client/raid/`; numbers in
`config/blocksouls/boss-final_dragon.json` (`FinalRaidConfig`).

## Shape of the raid (set with the user)
Three phases, Destiny-raid style (Leviathan / Eater of Worlds / Last Wish as the feel):
1. **Phase 1: the vanilla Ender Dragon fight, untouched** (crystals, perches, vanilla boss bar).
2. **Phase 2: Grounded.** When the vanilla dragon dies it doesn't rise in light: it **falls**. Our dragon (the
   **Hollow Sovereign**) tears out of the fall, lies dazed for 5 s while **the obsidian towers collapse** one after
   another, then rises and fights **on foot**. At 85% it takes to the air (untouchable) and calls a **zombie wave**; at 60%
   it calls a **herald**, one of the field bosses. Clearing the wave brings it down; killing the herald **crashes it down
   stunned** (10 s, ×2 damage).
3. **Phase 3: Final Stand** (below 35%). It hovers and casts **Sovereign's End**, a violet ring closing on the island
   center; when it completes, everyone in the fight dies (a wipe). **Crystals** stand on the tower stumps, **sealed**
   unless the **plates** are held; broken in the order the obelisk shows, they break the cast and crash it down stunned.
   Then a new order and a shorter cast: a **damage check**.

## Replacing the vanilla dragon's death, not the dragon
- **Two mixins** (own config `blocksouls_bosses_raid.mixins.json`):
  - `EnderDragonMixin.tickDeath` (HEAD, cancellable): for the dragon of an End fight, skip the 10 s light show and call
    `FinalRaidEnd.vanillaDragonFell`, which spawns our dragon where it died, in its falling state, and discards the
    vanilla one.
  - `EnderDragonFightMixin`: `setDragonKilled` for the fight's own vanilla dragon also starts the fall (covers
    `/kill`) instead of ending the fight. `createNewDragon` does nothing while our dragon lives (vanilla's 60 s "no
    dragon seen" check would otherwise spawn a second one). The vanilla bar is hidden while ours lives.
    `tryRespawn` is off (one final boss per run).
- **On our dragon's death** vanilla's `setDragonKilled` gets a stand-in `EnderDragon` carrying our UUID (a static
  flag lets that call through): exit portal, first-kill egg and the next gateway come from vanilla's own code.
- **XP:** phase 1 drops none (its death becomes the fall); our dragon drops the vanilla first-kill 12000.
- `replaceVanillaDragon: false` gives the plain vanilla fight back.
- `BossEvents.ENGAGED` fires when our dragon's fight starts (phase 2), `KILLED` on its death, both with `FINAL_BOSS`.

## Arena
- **No fog wall** on the island (framework hook `raisesFogWall()`, and per instance `setFogWallEnabled`, used for the
  herald): dead players respawn in the Overworld and come back through the portal onto the arrival platform; a
  one-way wall would lock them out. "In the arena" = 110 blocks around the island center (covers the platform), 60
  below to 100 above the surface.
- **Towers stay down** after the fall (it's the finale; the world goes endless afterwards). The collapse clears
  obsidian, iron bars and bedrock tops in the pillar footprint down to the surface, top first, two layers a tick, with
  a 1.5 s rumble at each foot first; a tenth of the obsidian flies off as falling blocks (rubble on the island). The
  last vanilla crystals are removed (they'd explode for 6 when shot).
- **Obelisk** = the exit portal's bedrock column. **Perch** (where it crashes when stunned) = the podium.
- **Plates** = 3×3 crying obsidian 20 blocks out at 45° angles; put back on reset, death and after a restart.
- **Flight scale:** all flight distances and the fight radius are multiplied by the arena's scale: 1 in the End, 0.5
  for dragons summoned elsewhere (`/bs raid summon`, spawn egg), 0.08 in game tests (the dragon must stay over its test
  area, the only chunks guaranteed to tick, and neighbouring tests' players must not count). Movement is leashed to
  half the fight radius from the center (55 blocks in the End).

## Phase 2 details
- **Movement:** it walks on the island surface (heightmap) through rubble (no collision), stalking its target to ~5
  blocks. It stands still unless walking or an attack moves it.
- **Ground moveset:** bite (short front cone), tail sweep (280° low arc behind; jump it or stand at the head), wing
  gust (front cone, throws you away from the center toward the void), ground breath (cone that slowly follows its
  target), charge (telegraphed line), stomp (expanding shockwave ring; jump it), void orbs. While airborne over an
  interlude: breath lines, dives, void orbs.
- **Interludes can't be skipped:** damage stops at 85% until the wave is done and at 60% until the herald is.
- **Wave:** 7 solo × party.json's wave curve (29 for 8 players, max 40) mobs from the mobs module's wave pool (wave tier 15; plain zombies without it),
  in a ring 24 blocks out, glowing so they read across the island, re-targeted at players. It comes down when the wave
  is dead, or after 90 s.
- **Herald:** picked from the field-boss pool, preferring bosses not killed this run; ×0.6 health; no fog wall; it
  engages the group straight away. Its kill counts as killing that boss this run (it won't return as a field boss)
  and it drops its own loot. Without any field boss registered the interlude is skipped.
- **Head weak point** ×2 while it's on foot or stunned.

## Phase 3 details
- **Cast:** 60 s (×1.25 solo), ×0.85 each cycle, never under 30 s. A wrong crystal pushes it 10% forward. Sovereign's
  End: 10000 indirect magic to everyone in the fight (totems can survive it), then the cast restarts.
- **Crystals:** 3 solo × party.json's `raidCrystals` curve (4 / 5 / 8 at 2 / 4 / 8 players, max 8), open pillars before caged ones (cages are gone anyway). Order
  random per cycle; the obelisk plays it on a loop (each crystal flares in its color white→violet, beams at the
  obelisk, a bell chime rising in pitch). Rings on the obelisk count progress. Wrong crystal: harmless burst, heals the
  dragon 5% (visible stream), re-forms after 5 s, the order reshuffles.
- **Seal:** crystals are sealed (dark, violet shell, hits clink) unless `PartyScaling.raidPlates` plates are held right now
  (one per two players: 1 solo, 4 for 8; placed: one spare, so 2–5; the End arena has 5 spots). While open, chains of light run from the held plates to the obelisk.
- **Airborne damage** ×0.15 (the stuns are the damage); **stun** 10 s at ×2.
- **Soft enrage** at 12 minutes of our fight: ×1.5 damage. Restarts on a wipe.

## Scaling and wipes
- Mechanics (crystals, plates, wave size) scale through common's `PartyScaling` (party.json, parties of 1–8; bigger
  counts as 8); the cast keeps its solo bonus. Health scaling is the framework's (`PartyScaling.bossHealth`).
- **Wipe** = the framework's reset (everyone dead/downed/out for 10 s): healed to the **last checkpoint**: 100%, 85%
  after the wave, 60% after the herald, 35% in phase 3. Cleared interludes stay cleared; phase 3's crystals, plates and
  cast restart on the next engage. A restart mid-fight keeps the phase and the cleared interludes.

## Look, sound, drops
- Name **"Hollow Sovereign"** (original). Crystals: "Order Crystal". Trophy: "Hollow Crown".
- Model: vanilla Java model at 2× scale (256×128 skin from `tools/assets/raid_skins.py`), ~19-block wingspan. Poses:
  walking (folded wings, four-legged gait), flying (flaps; frantic when falling or crashing), sprawled (dazed and
  stunned: belly down, wings flung out, head low). Glow: violet cracks always; white-hot cracks in phase 3 or enraged.
- Sounds are re-pitched vanilla events (placeholders); music placeholder `music.dragon` at 0.85 pitch.
- **Drops:** 12000 XP, the egg (vanilla, first kill), 4 dragon's breath, the Hollow Crown, and (guns module only)
  2 legendary guns + 64 primary + 32 special ammo.

## Debug
`/bs raid summon | wave | herald | stun | crystals skip | enrage | wipe | info`; phase 3: `/bs boss health 30`.
