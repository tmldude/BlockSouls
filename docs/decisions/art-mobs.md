# Decisions: art-mobs (Stage 2b)

Defaults picked by the `art-mobs` session. Same rules as `DECISIONS.md` (the root file wins on conflict).

## Pipeline
- **One geometry spec drives everything.** `tools/assets/mob_models.py` holds every mob's parts and cubes and
  generates `MobGeometry.java`; textures, previews and the asset check all read the same spec. UVs are packed
  automatically (skyline packing), so the Java `LayerDefinition`s and the PNGs can't disagree. The models' own
  `createBodyLayer()`s delegate to `MobGeometry`; animations stay hand-written per model.
- **Texture sizes:** Shambler, Crawler, Screamer, Ash Hound 64×64; Brute and Infected Piglin 128×64; Bloater and
  Mutant Brute 128×128. 1 texel per model pixel, like vanilla, so they sit well next to vanilla mobs.
- **Screenshots are offline renders, not `runClient`.** This sandbox can't reach Mojang/Fabric to launch a client,
  and a client game test would need new shared Gradle and CI setup outside this lane. `mob_preview.py` is a software
  rasterizer that ports `ModelPart.Cube` (vertex order, UV rectangles, mirroring), `translateAndRotate`
  (rotationZYX) and the living-entity render transform, then draws the `_glow` layer full-bright. Sheets live in
  `tools/assets/previews/`. Their walk/telegraph poses approximate the Java `setupAnim`s.
- **`check_mob_assets.py`** fails if `MobGeometry.java` is stale, a texture is missing or has the wrong size, a
  glowing renderer lacks its `_glow.png`, or a sound event or file is missing. Run it before committing art.
  It isn't in CI because the workflow is outside this lane; the orchestrator can add it.

## Models (no hitbox changes)
- **Shambler:** hunched 0.42 rad, head lolling, jaw hanging open; a three-segment right arm long enough that the
  knuckles drag on the ground; a withered left arm reaching forward. Torn shirt and one trouser leg are cutout
  overlay layers (+0.3 / +0.25), so the rotting skin shows through the tears. Limps: full stride on the clothed leg,
  short stiff stride on the bare one.
- **Crawler:** six jointed limbs (root yaw, upper and lower segments) with the elbows above the back, like a
  spider's; split spine, ribs through the flanks, a cluster of small eyes. Moves on alternating tripods.
- **Screamer:** wasp waist (pelvis, bare spine, open ribcage), long jointed arms with two long fingers, and a
  distended hinged jaw with a hanging chin that opens 1.25 rad when it screams, the whole body shaking.
- **Bloater:** an 18³ belly with a wider 20×12×20 band to round it out, six pustules that pulse out of phase
  (separate parts, scaled), a tiny head sunk into the fat. **New glow layer** on the pustules so it's spotted in
  the dark. The telegraph inflates the belly 1.32×, swells the pustules and speeds up their throb.
- **Brute:** humped shoulders, huge forearms and fists, short legs, tiny underbitten head sunk between the
  shoulders, a rusted shackle on the right wrist. The slam windup raises both fists straight overhead (-3.2 rad),
  rears the body back and plants the legs wide, with a tremble at the top.
- **Mutant Brute:** the Brute rig with no mirrored UVs (asymmetric mutation), four bony spines, a throbbing tumor on
  the right shoulder, crystal-like nubs on the left forearm; rad-green veins, eyes, tumor, nubs and spine tips are
  emissive. The throw windup twists the body into the throw.
- **Infected Piglin:** piglin head, snout and tusks; a growth swallows the left eye, the left ear is fused into a
  lump, tumors on the scalp, back, left knee and a swollen left arm. The hat/jacket layers are blight overgrowth,
  thickest on the left side. Growths throb. Still a `HumanoidModel`, so the gold sword renders as before.
- **Ash Hound:** deep chest and narrow waist, ash-flake plates along the spine, digitigrade hind legs (thigh +
  shin), a hinged jaw and a two-segment tail. Emissive ember eyes, mouth interior, cracks on the flanks and flake
  edges. Snarls when hunting.
- **Proposed, not done (hitboxes are gameplay):** the Crawler's limbs reach ~0.25 blocks past its 0.9-wide
  hitbox on each side, which looks right for a spider-like mob; the Brute's knuckle-dragging arms poke a little
  past its 1.3-wide box. Neither needs a hitbox change.

## Animation
- **New render-state fields:** `airborne` (not on ground, not in water, not riding) drives the Crawler's and Ash
  Hound's pounce pose, since the pounce isn't a synced action; `phase` (per-entity offset from the entity id)
  desynchronizes idle loops so a horde doesn't twitch in unison.
- Every mob has a walk/run cycle and an idle loop (head jerks, jaw sway, limb taps, breathing, pulsing growths).
  Windups lerp toward fixed, exaggerated poses so they read from ~20 blocks (see the "at ~20 blocks" thumbnail in
  each preview sheet).

## Textures
- Hand-directed pixel art from PIL/numpy: per-mob color ramps, clumped value noise lit from above with 2×2
  ordered dither, then placed features (eyes, sockets, teeth, ribs, wounds, veins, torn cloth, belts, shackles).
- Spawn eggs repainted from each mob's own ramps (body, spots, accent pixels in the eye color).

## Sounds
- **All sounds are original, synthesized by `mob_sounds.py`**; no samples and no third-party assets, so
  nothing to add to an `ASSETS.md`. ffmpeg with libvorbis encodes mono `.ogg` (mono so the game positions them).
- 58 files for 34 events, 1–3 variants each. Each event keeps its Stage 1 id and subtitle key, so no Java changed.
- **Louder and further for telegraphs:** peaks per kind (ambient 0.6 … scream/slam/burst/roar 0.95), and
  `attenuation_distance` 32 for the scream, roar and dread howl and 24 for the slam, windup and burst (vanilla
  default 16).
- `MobSounds`' doc comment still says the ids point at vanilla events; `src/main` is outside this lane, so it's left
  for the orchestrator.
