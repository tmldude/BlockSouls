# Decisions: final-raid (Stage 2b)

Defaults picked by the `final-raid` session. Same rules as `DECISIONS.md` (the root file wins on conflict).
Code: `bosses/src/main/java/.../bosses/raid/`, client in `.../bosses/client/raid/`; numbers in
`config/blocksouls/boss-final_dragon.json` (`FinalRaidConfig`).

## Replacing the vanilla dragon
- **One mixin, into `EnderDragonFight`, not a re-driven vanilla dragon.** Vanilla's fight object already decides
  *when* a dragon should exist (first visit, the 60 s "haven't seen it" check, respawn), and its kill hook builds the
  exit portal, egg and gateway. So we hook the one place it *creates* a dragon (`createNewDragon` → spawn ours
  instead) and keep everything else. Re-driving `EnderDragon`'s phase manager would have needed mixins into the
  dragon, its phases, its parts and its renderer, and still fought vanilla's crystal healing and perch AI.
- The mixin (`raid/mixin/EnderDragonFightMixin`, own config `blocksouls_bosses_raid.mixins.json`) does four small
  things: spawn ours in `createNewDragon`; cancel `tryRespawn`; force the vanilla boss bar invisible (ours draws its
  own; also stops vanilla's End music and fog); every second, discard any vanilla dragon (old worlds) and spawn ours.
  It also exposes `dragonUUID` / `ticksSinceDragonSeen` so the fight points at our dragon and our dragon keeps it
  from looking for another.
- **On death** we hand vanilla's `setDragonKilled` a stand-in `EnderDragon` that never enters the world, carrying our
  UUID: exit portal, first-kill egg and the next gateway come from vanilla's own code, unchanged.
- **Re-summoning is disabled** (one final boss per run). End crystals placed on the exit portal stay ordinary
  crystals (explosive blocks), nothing more. After the kill the pillars stay bare: the raid's own crystals are gone
  and the vanilla crystals were removed when the fight started (they would explode for 6 when shot).
- `replaceVanillaDragon: false` in the config gives the vanilla fight back (debug/escape hatch).

## Arena
- **No fog wall** (framework hook `BossEntity.raisesFogWall()`, default true): the arena is the whole main island,
  and dead players respawn in the Overworld and must come back through the portal onto the arrival platform; a
  one-way wall would lock them out. "In the arena" is a cylinder 110 blocks around the island center (covers the
  arrival platform at x=100), 60 below to 100 above the surface. So **entering The End engages the raid**.
- **Obelisk** = the exit portal's bedrock column (the display). **Perch** = on the podium, like vanilla.
- **Plates** = 3×3 crying obsidian set into the island 20 blocks from the center, at 45° off the axes, first two
  spots opposite each other. Replaced blocks are remembered (and saved) and put back on reset, phase 1, death and
  after a restart.
- A dragon spawned outside the End (spawn egg, `/bs boss spawn`, `/bs raid summon`) builds a small arena around its
  spot: 8 floating crystal spots in a 14-block ring, plates 9 blocks out.

## The fight
- **Phases at 70% / 30%** as briefed. Health 1000 base (framework scaling: +50% per extra player, × difficulty).
- **P1 shield = ×0.15 damage, not immunity**, and while it's up the dragon can't drop below 70.5% (can't skip into
  P2). Immunity would make the "wrong crystal heals 5%" penalty meaningless at full health; this way chip damage
  counts but the puzzle is the way forward. A muted hit gives a clink and violet sparks (shield) so it reads.
- **Crystal count:** 5 solo, +1 per extra player, max 8, chosen from the 8 open pillars before the 2 caged ones (no
  cage-breaking needed with ≤ 8). Order is random per fight.
- **Wrong crystal re-forms** (after 5 s, particles streaming in) instead of staying broken: otherwise breaking all
  crystals in any order would finish the puzzle and the order wouldn't matter. It heals the dragon 5% (a visible
  violet stream to it) and reshuffles the remaining order (always into a different order).
- **Show, don't tell:** the obelisk plays the order on a loop: each crystal in turn flares full-bright in its color
  (white for 1st, deepening to violet), beams at the obelisk, a colored particle stream runs along the beam, and a
  bell chime rings at both ends, pitch rising with the position. 1.3 s per crystal, 3.5 s pause, repeat; restarts
  after every hit. Rings stacked on the obelisk, one per crystal of the order, turn white as they're done.
- **P2/P3 out of reach = ×0.35 damage while airborne** (with the same clink), so the perch is where the damage is.
  Perched: ×1.5; the head part ×2 more (only while perched).
- **Plates:** required = min(players, 2) (solo: 1), placed = players + 1 (2–4), so there's always a spare. Hold 3 s
  (2 s in P3); the hold drains as fast as it fills when people step off. Pull (2 s of chains of light from every plate
  to the dragon) → perch 8 s (5.5 s in P3) → takeoff → plates dark for 12 s (7 s in P3). The perch timer pauses during
  a phase-change roar so the roar doesn't eat the DPS window.
- **Perched attacks read by facing:** the dragon lands facing the way it was dragged in and doesn't turn. Tail sweep
  hits a low 280° arc around everything but its head (jump it, or stand at the head); wing gust hits a 140° cone in
  front and throws you away from the island center (toward the void edge). So "where is safe" is always "the other
  attack's zone", which teaches both.
- **Void orbs** are server-side particle orbs, not entities: one per player plus one, slow (0.17 b/t), pop on
  contact or after 11 s. Can't be shot down (kept simple; they're dodgeable by outrunning).
- **Meteor shower** (P3): one circle under each player plus one near them, plus 3 random on the island; impacts roll
  across the 1 s active window.
- **Soft enrage** at 12 minutes of fight time: ×1.5 damage (applied through `damageMultiplier()`), violet-white
  flames off the body, the enraged glow layer, a roar. The clock restarts on a wipe (a retry gets a fair 12 minutes).
- **Wipe** = the framework's reset (everyone dead/downed/out of the arena for 10 s). We override it to keep the
  phase and heal to that phase's start (100% / 70% / 30%); the phase's mechanic (crystals or plates) is rebuilt on the
  next engage. The resumed fight opens with the phase roar.
- **Endermen** in the arena have their anger cleared every 5 ticks, and Enderman damage to players near an engaged
  final dragon is cancelled outright.

## Look, sound, drops
- **Name: "Hollow Sovereign"** (original). Crystals: "Order Crystal". Trophy: "Hollow Crown".
- Model: vanilla Java model at 2× scale, 256×128 skin from `tools/assets/raid_skins.py` (its own script, so the
  shared `boss_skins.py` isn't a merge hotspot). Wingspan ≈ 19 blocks. Glow layers: violet cracks always; white-hot
  cracks in P3 or when enraged. Wings fold on the perch.
- Crystals reuse vanilla's crystal *model* with our own bone/violet texture, tinted full-bright in their sequence
  color when lit.
- Sounds are re-pitched vanilla events (placeholders), music placeholder = `music.dragon` at 0.85 pitch.
- **Drops:** vanilla dragon XP (12000), the egg (vanilla, first kill), 4 dragon's breath, the Hollow Crown (pure
  trophy, epic, fire-resistant), and from `boss_drops/final_dragon_guns` (guns module only): 2 legendary guns (two
  rolls of the shared legendary table) + 64 primary + 32 special ammo.
- Death: 4 s of light bursting from the cracks, then it's gone (vanilla's 10 s light show is the vanilla dragon's).

## Debug
`/bs raid summon | crystals skip | perch | enrage | wipe | info` (nearest final dragon within 256 blocks), plus the
framework's `/bs boss ...`.
