# v2-mobs session decisions

Defaults picked while building Stage 6B's mobs lane (PLAN2.md §3h affixes, §3k; brief `docs/sessions/v2-mobs.md`).
Same style as `DECISIONS.md`: where this conflicts with a plan file, this logs what was actually built.

## Common affix contract (PR 1)

- **`common` stores the affix; the provider gives it meaning.** `Affixes` owns a persistent Fabric attachment
  (`blocksouls_common:affix`, one `Affix` per entity) synced to every tracking client, so the affix survives
  chunk reloads and the client can pick the tinted overlay without a custom packet. `apply` / `roll` go through a
  `Provider` the mobs module installs; the default is a no-op (`apply` → false, `roll` → empty), mirroring
  `Floors`/`Acts`. `of` reads the attachment directly, so it answers correctly even for entities affixed by
  another module's provider path (bosses) and on the client.
- **One affix per entity.** PLAN2 §3h reads "prefix + one modifier"; applying a second replaces the first. `of`
  returns `Optional<Affix>`.
- **`attach(entity, affix)` is the raw store** a provider calls; it has no behaviour, so bosses can stamp an
  affix on a field boss and still get the mobs module's modifier through `apply` when mobs is loaded.
- **`roll(random, floor)` takes the 1-based floor** (0 outside Expedition) so the provider can gate affixes by
  floor/escalation; the brief's classic-mode switch (`affixesInClassic`) lives in the provider.
- **Nameplate prefix is translatable** (`affix.blocksouls_common.<name>`), with the English prefix also on the
  enum for server-side names.
- **`roll` also takes the floor's tags** (`roll(random, floor, floorTags)`; the two-argument form passes none), so
  the provider can read the world's `elite_affixes` escalation without the mobs module looking floors up itself.

## Floor rosters (common half)

- **`WaveMobSpec` gains `minFloor` and `tags`** as new record components, with the old five-argument constructor
  kept (every floor, no tags). `tags` are floor tags the spec needs: it is eligible only on floors carrying all of
  them, which is how blight/sky mobs slot in later as data (the world adds a pool tag, the spec names it).
- **`WaveMobs.eligible(wave, floor, floorTags)`** is the floor overload; `eligible(wave)` (classic) is unchanged and
  ignores both new fields. `more_elites` lets elites skip their `minWave` on that floor (they join from the floor's
  first wave); `elite_affixes` is read by the affix roll. Both tag names are constants on `WaveMobs`.
- **Flankers are marked with an entity tag, `WaveMobs.FLANKER_TAG` (`blocksouls_flanker`).** The Director adds it
  to hunters it spawns under its `FLANKERS` composition; mobs reads it (plus its own config list of always-flanking
  mob types). A scoreboard tag needs no attachment and is saved with the entity.
