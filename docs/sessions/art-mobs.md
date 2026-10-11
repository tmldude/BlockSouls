# Session: art-mobs (Stage 2b)

**Modules:** `mobs` (client models/textures/sounds only). **Branch:** `claude/art-mobs`.
**PR title:** `mobs: art pass (models, textures, sounds)`.

Turn the Stage 1 placeholder mobs into creatures with real character, keeping everything original. Read
`docs/sessions/README.md`, PLAN.md §0b ("show, don't tell", apocalypse-infection theme) and the mobs code first.

## You own
`mobs/src/client/**` (models, renderers, animations), `mobs/src/main/resources/assets/blocksouls_mobs/**` (textures,
sounds, `sounds.json`), and `tools/assets/` additions. **Don't change gameplay** (hitboxes, stats, AI); if a model
needs a different hitbox to look right, propose it in the PR instead.

## Do
- **Models:** refine every mob's `LayerDefinition` for a strong silhouette and personality: Shambler (hunched,
  one arm dragging), Crawler (spider-like limbs, low), Screamer (gaunt, distended jaw that opens when screaming),
  Bloater (swollen, pulsing sacs; inflates during its telegraph), Brute (massive shoulders, tiny head), Mutant Brute
  (Brute + rad-green glowing veins, an emissive layer), Infected Piglin (fleshy growths over piglin features),
  Ash Hound (lean, ash-flaked, ember eyes, emissive).
- **Animations:** walk/run cycles, idle twitches, attack windups that read from 20 blocks away (telegraphs matter).
- **Textures:** hand-directed pixel art via PIL scripts (no image models trained on others' art, no copied
  textures). 64×64 or 128×128 where detail helps. Emissive layers for glowing parts.
- **Sounds:** original sounds: synthesize with Python (numpy: noise, filters, pitch envelopes) for groans, screams,
  bursts, howls; encode to `.ogg` (find an available encoder — `ffmpeg`/`oggenc` via apt or a pure-Python
  route; if none works, keep vanilla re-pitched events and log it). Record anything third-party in `ASSETS.md`
  (CC0 only).
- Paths stay stable; the game must render with no missing textures.

## Done when
CI green; screenshots (client gametest or `runClient`) of every mob in the PR; `docs/decisions/art-mobs.md`.
