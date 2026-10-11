# Decisions: audio (Stage 4)

Defaults picked by the `audio` session. Same rules as `DECISIONS.md` (the root file wins on conflict).

## Pipeline
- **Everything is synthesized, no samples and no third-party assets**, so nothing goes in `ASSETS.md`. numpy builds
  the waveforms; ffmpeg with libvorbis encodes them (`-bitexact`, so re-running a script gives byte-identical files).
- **Shared helpers moved to `tools/assets/synth.py`** (out of `mob_sounds.py`, which now imports them; its output is
  unchanged byte for byte). New there: `level()` (peak + loudness cap), `loop_seam()`, `metal`, `bell`, `fire`,
  `thunder`, `impact`, `wingbeat`, `choir`, `whoosh`, stereo `write_ogg`, and `emit()` (writes the files and
  `sounds.json`).
- **Loudness:** every sound is normalized to a per-kind peak (handling sounds 0.45–0.65, shots and impacts 0.8–0.92)
  and turned down if its loudest 100 ms RMS is over 0.4 (`synth.MAX_RMS`), so nothing approaches vanilla thunder's
  sustained full scale. Mono `.ogg` (quality 4) for every positional sound; stereo quality 3 for music.
- **Size:** 191 new files, 7.9 MB (guns 0.4, bosses SFX 2.9, music 4.3, kart 0.3). Under the 15 MB budget.
- **`check_sounds.py`** covers mobs, guns, bosses and vehicles: registered events vs. `sounds.json` both ways,
  every named file exists, no entry points at a vanilla sound or event, every subtitle has a lang line. Added to
  CI's "Check art assets" step.
- `sounds.json` for guns and vehicles stays in `src/client/resources` where Stage 1 put it (assets are client-only;
  moving them buys nothing). Bosses' stays in `src/main/resources`.

## File groups and how they were made
- **Guns** (`guns/.../sounds/gun/`, `gun_sounds.py`, 30 files): each shot layers a supersonic crack (ms of high
  noise), the muzzle blast (band-passed noise, fast decay, tanh-driven for punch), a pitched low thump, the
  action's metal clack and a short filtered tail. Per weapon: pistol thin and snappy (3 variants); hand cannon
  deep boom + ringing cylinder + room (3); auto rifle tight and short so bursts don't smear (3); shotgun wide low
  blast + pellet hiss + pump rack (2); sniper vicious snap + heavy boom + valley echo + bolt (2). Plus empty click
  (2), reload start/end (mag out, mag seat, slide; 2 each), ricochet ping + whine (3), Explosive Rounds pop (2),
  ammo pickup cartridge clinks (2), headshot tick (1), Outlaw cylinder spin-up and snap (1), Rampage rising
  detuned-saw growl (1; pitch rises with stacks), Vorpal blade hiss into a gong (1).
- **Bosses** (`bosses/.../sounds/boss/`, `boss_sounds.py`, 134 files): one palette per boss.
  Furnace Knight: iron clanks (inharmonic partials), bellows (slow breathing noise), fire (roar + crackle).
  Pale Rider: hollow hoofbeats (a tap on a resonant box, in a three-beat gallop), wind, a detuned breathy choir in
  a long dark reverb; also the wraith steed whistle and fade. Ashwyrm: huge wingbeats (low air swell + whomp), a
  sub-heavy driven formant roar, fire breath, meteors. Stormplate Knight: thunder (crack + rolling rumble), long-
  ringing steel, electric crackle and buzz. Hollow Sovereign (the `entity.final_dragon.*` ids): void (deep beating
  sine clusters), cracking crystal (glassy bells, shatters), and Sovereign's End. The raid's crystal chime is a
  clean bell (the code pitches it per note), plates hum and confirm, chains rattle. Fog wall close/open are a
  swirling whoosh + boom / a rising dissipating shimmer; lock-on is a soft UI tick.
- **Music** (`bosses/.../sounds/music/`, `music.py`, 5 files, 90–96 s, stereo, `"stream": true`, volume 0.6):
  a tiny sequencer (pad, bass, drums, a seeded scale-walking melody) per track; each is a whole number of bars and
  the reverb tail is folded onto the start, so the loop has no seam. Furnace Knight: D minor 84 bpm, anvils, taiko
  drums, horn line. Pale Rider: A minor 6/8, ghost choir over a hollow gallop, wind, thin whistle. Ashwyrm: C minor
  96 bpm, huge drums, low brass stabs, fire bed, rising horn. Stormplate: E minor 112 bpm, eighth-note bass, steel
  backbeat, thunder rolls, arpeggiated lead. Final raid: B minor 60 bpm, void drone, organ, crystal-bell arpeggios,
  slow enormous drum, choir in the last third.
- **Kart** (`vehicles/.../sounds/kart/`, `vehicle_sounds.py`, 22 files): engine loop = a two-stroke pulse train
  through exhaust resonances + intake hiss, whole cycles at 52 Hz for pitch 1.0 (so `KartEngineSound`'s 0.5–1.6
  covers idle 26 Hz to ~83 Hz), tail cross-faded into the head (no fade at the ends). Skid squeal (2), crash/ram
  panel crunch + clank + rattle (3), breakdown bang + dying cough + clatter (2), repair = three hammer blows on
  sheet metal (2), part fitted = ratchet + clunk (2), repaired = engine catching (1), hop boing + thunk (2),
  boost exhaust pop + turbo whoosh (2), refuel = glugs rising in pitch as the tank fills + cap clink (2), sputter
  = uneven misfire coughs (3; `kart.refuel` and `kart.sputter` came from kart-fuel-horn with vanilla placeholders and
  were replaced after merging main). The horn is the kart-fuel-horn session's; `vehicle_sounds.py` keeps any
  entry it doesn't own.

## New events (registration classes) and their one-line hooks
The brief asked for sounds that had no event. Each got an event and the smallest hook that plays it; no gameplay
changed.
- `gun.ammo_pickup`: `ItemEntityMixin` (guns' first common-side mixin) plays it when a player picks up primary or
  special ammo, next to vanilla's pickup pop.
- `gun.headshot`: played client-side (UI, the shooter only) when a precision hit marker arrives.
- `gun.perk.outlaw` (precision kill with Outlaw), `gun.perk.rampage` (kill with Rampage; pitch 0.9 + 0.15 × stacks),
  `gun.perk.vorpal` (Vorpal bonus applied, once per shot so shotgun pellets don't stack it): sent only to the
  shooter (`ClientboundSoundPacket`). Explosive Rounds already had `gun.explosive_round`.
- `block.fog_wall.open`: `FogWall.lower()` plays it at the wall's centroid (volume 3, ~48 blocks) when it had blocks.
- `ui.lock_on`: `LockOn` plays it (UI) when a lock catches a new target.
- `entity.final_dragon.cast`: Sovereign's End's 2-second pulse now uses it instead of the orb hum (same pitch
  ramp 0.5 → 1.5, so the overlapping swells rise over the cast).
- `kart.skid`: `KartEntity` plays it when a drift starts.

## Left as vanilla (gameplay code, outside this lane)
These call vanilla `SoundEvents` directly rather than a BlockSouls event, so they aren't in any `sounds.json`; each
needs a new event and a one-line change in gameplay code. Follow-up for the orchestrator:
- `BossEntity.leave()` (`ILLUSIONER_MIRROR_MOVE`, a field boss walking off). `BossEntity` is also where
  `arena-seal` works, so it was left alone. (Its default `staggerSound()` is vanilla too, but every boss overrides
  it with its own roar.)
- `FieldBosses` announcement and `RaidAdds` waves (`RAID_HORN`), `RaidAdds` (`WITHER_SPAWN`), `FinalRaidEnd`
  (`ENDER_DRAGON_DEATH`), `TowerCollapse` (`ANCIENT_DEBRIS_BREAK`, `GENERIC_EXPLODE`), `DragonscaleArmor`
  (`LIGHTNING_BOLT_IMPACT`), `WraithSteed`'s ambient/hurt/death/angry (skeleton horse).
- `MobSounds`, `BossSounds` and `FinalRaidSounds` doc comments no longer claim the ids point at vanilla sounds.
