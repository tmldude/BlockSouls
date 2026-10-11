# Session: audio (Stage 4)

**Modules:** sound assets of `guns`, `bosses` and `vehicles`. **Branch:** `claude/audio`.
**PR title:** `audio: original sounds and music for guns, bosses and the kart`.

Only the mobs have original sounds today (`tools/assets/mob_sounds.py`, see `docs/decisions/art-mobs.md` "Sounds").
Guns, every boss (incl. the final raid) and the kart still point their sound events at re-pitched vanilla sounds,
and boss music is a vanilla disc / `music.dragon`. Replace all of it with **original, synthesized** audio.

## You own
- `guns/`, `bosses/`, `vehicles/` `src/main/resources/assets/blocksouls_<module>/sounds.json` and `sounds/**`.
- New generator scripts in `tools/assets/` (e.g. `gun_sounds.py`, `boss_sounds.py`, `vehicle_sounds.py`, `music.py`);
  reuse `mob_sounds.py`'s synthesis helpers (factor shared code into a small module if it helps).
- Sound registration classes, **only** to add events or point existing ones at new ids: `GunSounds`/equivalent in
  `guns`, `BossSounds`, `FinalRaidSounds`, the vehicles sound registry. Fix the stale comments in `MobSounds`,
  `BossSounds` and `FinalRaidSounds`. No gameplay changes.
- Not yours: the kart **horn** (the `kart-fuel-horn` session adds that event and its sound).

## What to make
- **Guns:** a distinct fire sound per weapon (starter pistol, hand cannon, auto rifle, shotgun, sniper rifle),
  reload, empty click, ammo pickup, headshot tick, and each perk's proc (Outlaw, Rampage, Explosive Rounds, Vorpal).
  Punchy and short; 2–3 variants each where vanilla uses variants.
- **Bosses:** every event each boss registers (attacks, windups, roars, hurt/death, footsteps, fog wall
  close/open, lock-on), so each boss has its own voice: Furnace Knight (iron, bellows, fire), Pale Rider (hollow
  hoofbeats, wind, spectral), Ashwyrm (huge wings, fire breath, roar), Stormplate Knight (thunder, steel), Hollow
  Sovereign (void, deep, cracking crystal, the Sovereign's End cast rising over its length).
- **Music:** one original looping track per field boss and one for the final raid (60–120 s, streamed `.ogg`,
  `"stream": true`), mixed under the SFX. Keep it simple and moody; procedural is fine.
- **Kart:** engine loop (idle → high revs; `KartEngineSound` pitches it), skid, crash, repair hammering, part fitted.

Mono `.ogg` for positional SFX, stereo allowed for music; normalize loudness so nothing is louder than vanilla
thunder. Keep the pack small (aim < 15 MB of new audio).

## Tests
`check_mob_assets.py` style: a `tools/assets/check_sounds.py` that fails if a `sounds.json` entry points at a missing
file or a registered event has no entry; run it before committing. Gametests are not needed for pure assets.

## Done when
CI green; no BlockSouls sound event points at a vanilla sound (except where you log why); the full-pack client test
still passes; `docs/decisions/audio.md` lists every new file group and how it was made.
