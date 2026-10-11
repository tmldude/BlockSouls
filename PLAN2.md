# BLOCKSOULS v2 — "Expedition" (design + build plan)

v1 (PLAN.md) ships first; this plan is the next version. It folds in the owner's 2026-10-11 ideation
pass. Everything in PLAN.md still applies unless this file says otherwise; DECISIONS.md still wins over both.

**Status of the big call:** the owner confirmed the open-dimension act model (2026-10-11): acts are
open dimensions, not closed maps; **no fixed siege count per act** — pacing is entirely the party's;
day/night rhythm exists in every dimension (synthetic where there's no sun). Classic mode (§2e) stays
so playtests can compare run shapes.

---

## 0. The pitch

v1 is "Minecraft + night waves." v2 is its own game: a 1–8 player roguelike **expedition through four
acts** — Overworld → infected Nether → the Blightheart → the Skyrift — ending in the End raid. Each act
is a whole open dimension: mine its ores, build, roam, fight its field bosses. Nights bring sieges,
everywhere, forever — **the waves do not care how long you stay**. The party leaves an act whenever it
chooses by charging the portal; the global threat counter never stops climbing, and every boss ahead
(final raid included) scales with it. Stay and you're stronger against harder bosses; rush and you're
weaker against easier ones. That trade is the game.
Builds emerge from tag synergies across boons, guns and enchantments; an AI Director runs the sieges;
every run ends in a shareable run summary.

Run length: **player-paced**; tune curves to a ~2.5-hour median. All pacing numbers in config.

## 1. The act structure

| Act | Dimension | Entry | Resource tier | Field bosses | Camp |
|---|---|---|---|---|---|
| 1 | Overworld | run start | scrap, gunmetal | Pale Rider, Ashwyrm | spawns near run start |
| 2 | Infected Nether (exists) | nether portal, ignited by the Act 1 portal charge | blight ore / blight cores | Furnace Knight | near arrival portal |
| 3 | **The Blightheart** (new dimension; the source of the infection) | portal built deep in the Nether, ignited by the Act 2 portal charge | blightheart ore (name TBD) | 1 new boss (blight-themed; e.g. a wall-crawling blight knight that uses the players' own climb mechanic) | near arrival portal |
| 4 | **The Skyrift** (new sky dimension; floating islands, storms) | glowstone-framed portal, ignited by the Act 3 portal charge | skysteel (name TBD) | Stormplate Knight (relocated home), 1 new boss | near arrival portal |
| Finale | The End (existing raid) | final portal at the Act 4 camp, ignited by the Act 4 portal charge | — | reworked Ender Dragon (exists) | — |

- **No fixed siege count per act** (owner). An act lasts exactly as long as the party chooses to stay.
- **Dimension names confirmed by the owner (2026-10-11): the Blightheart and the Skyrift.** Original
  names only (no "Aether").
- Strongholds / Ender Eye hunting are **removed from the run** (Classic mode keeps them).
- Endless mode after the finale: endless sieges in the act of the party's choice.

### One rhythm: day and night everywhere

Every act runs the ~17-minute day/night cycle, so the v1 siege rules apply unchanged in every dimension:
sieges start at dusk wherever the party is, chain through the night, pause at dawn; days are prep.
- The overworld binds to the real sun. Dimensions without one get a **synthetic cycle — the "vibe
  switch"** (owner): no fake sun, just the world turning hostile on the same clock. Ambient light and
  fog color lerp down at dusk (Nether: ash-glow dims to ember-dark), the soundscape shifts (distant
  horns, growl layers), hostile aggression and spawn pressure rise, and dawn visibly releases it.
  Client renders from a synced cycle phase; the server drives sieges off the same clock.
- `game` grows a `SiegeClock` abstraction over the v1 scheduler: one implementation reads the sun,
  the other drives the synthetic cycle. Wave chaining, dawn rules, relic drops: one code path.
- The **threat counter** (the v1 wave counter, promoted) ticks up every night of the run, in every act,
  and never pauses. Mob *rosters* come from the current act; wave size/toughness come from the counter.

### Greed vs. safety: the ratchet (core rule)

- **The waves do not care how long you stay.** Sieges keep coming every night at ever-higher threat.
  There is no "clearing" an act.
- **Every boss ahead scales with the threat counter at engage time** — field bosses, portal-charge
  sieges, and the final raid — via a config curve (`threat.json`), separate from and multiplied with
  `PartyScaling`. Staying longer = stronger build vs. harder bosses; rushing = weaker build vs. easier
  bosses. Both are legitimate runs; the curve is the balance lever.
- The HUD shows the threat counter prominently (and the shopkeeper comments on it), so greed is always
  an informed choice, never a hidden tax.

### Portal charge (leaving an act, whenever you dare)

The next act's portal frame generates near the camp (Act 1 uses the party's own nether portal). **Any
player can start the charge at any time**: ~3 minutes of defense while the Director throws a siege at
the portal, at current threat, day or night. Charge completes → the portal ignites permanently. No
crafted key items, no structure hunt, no minimum sieges survived; the climax is a fight you pick the
moment for. Wiping the attempt just means re-triggering it later.

## 2. Open-world stance (the rules that keep acts from feeling like rails)

a. **The portal never pulls.** It waits for a player to start the charge, and once lit it waits again.
   Staying to mine/loot/over-prepare is always legal; sieges keep coming on the clock and the threat
   counter keeps climbing (§1). Advancing is a party decision.
b. **Two-way portals.** Backtracking to earlier acts and the home base is allowed. The siege front
   follows the party: once act N is unlocked, sieges target whatever dimension the party is in.
   (Split parties: the siege targets the dimension with the most non-downed players; stragglers get a
   warning line.)
c. **The Camp.** On first entry to an act, a camp structure generates near the arrival portal (skin on
   26.3's Abandoned Camp bones where possible): shopkeeper, forge, relic altar, light safe aura (hostile
   wave mobs won't path within ~24 blocks unless a siege targets the camp). Camps are landmarks, not walls.
d. **Act transitions are party-scaled moments.** Portal entry teleports everyone within 48 blocks after a
   10 s countdown; players further away get 60 s and are then pulled. No one herds stragglers manually.
e. **Classic mode** (`game-run.json`: `"runShape": "classic" | "expedition"`): the v1 open-world 7-night
   run with strongholds stays playable. Cheap (it is the existing code) and it lets playtests A/B the shapes.

## 3. Systems

### 3a. Synergy engine (build identity) — `common` contract + all item modules

- A shared **tag vocabulary** in `common` (`CommonTags.synergy`): element tags (`fire`, `storm`, `blight`,
  `kinetic`), trigger tags (`on_kill`, `on_precision`, `on_roll`, `on_reload`), payload tags (`ricochet`,
  `explosion`, `pull`, `heal`). Every boon, wacky enchantment and gun perk declares its tags in its JSON.
- **Keystone boons** (rare/legendary) change rules, not numbers. Seed list:
  - *Powder Burn*: reloading releases a nova of your last 6 bullets (`on_reload` + `explosion`)
  - *Ricochet Doctrine*: ricochets inherit your enchantments (`ricochet`)
  - *Red Ledger*: no natural regen; kills overheal to +10 hearts (`on_kill`)
  - *Ember Step*: your roll leaves a fire trail and counts as a hit (`on_roll` + `fire`)
  - *Deadeye Wind*: headshots refund 25 stamina (`on_precision` — binds guns to movement)
- **Weighted offers:** the relic's 3 cards weight toward tags the player already owns (Hades-style),
  so runs converge into builds. Weighting curve in `boons.json`.
- A **build line on the HUD/tab list**: the player's top two tags get an icon + name
  ("Fire · Ricochet"). Legibility is the point.

**Deck-building layer** (owner: "a combo between Slay the Spire and Hades"):
- **Boon levels:** picking a boon you already own upgrades it (Hades-style); the relic can also offer an
  "upgrade a random owned boon" card. Levels shown as pips on the HUD boon row.
- **Skip pays:** declining all cards converts the relic to souls. Never a dead choice.
- **Curses** — negative boons: greed objects price in curses (the cursed chest adds one; the blood altar
  can offer "take a curse instead of paying hearts"). Examples: *Hemorrhage* (healing 25% weaker),
  *Loud* (mobs aggro from further), *Heavy Pockets* (−souls on going down). **Purging** a curse costs
  souls at the shopkeeper or a free cleanse at a blood altar (which then offers no reward). Curses are
  lost on going down like boons — going down is already the punishment.
- **Trinkets** — the Slay-the-Spire relic analogue, a second collection axis that **survives going
  down** (softens the boon-wipe sting without removing it): passive artifacts, **3 slots**, no levels.
  Sources: field bosses offer a **pick-1-of-3 trinket** on death; challenge pylons and rare cursed
  chests drop one. Examples: *Dawn Clock* (prep phases 20% longer), *Last Shell* (once per siege,
  survive a down at 1 heart), *Magnet Core* (drops fly to you), *Siege Lens* (see the next siege's
  modifier two days out). Naming: the dawn drop stays "relic" (it offers boons); trinkets are the
  carried items.

### 3b. AI Director — `game`

Replaces "waves on a timer" with a hunter. Tracks per-party **stress** (recent damage taken, downs,
ammo levels, DPS trend, boon count vs. wave number) and modulates, within config bounds:
- spawn rate and burst size (lulls when battered, spikes when cruising),
- composition (flanker pack behind a camping party; brutes at a fortified choke),
- spawn direction via a coarse **influence map** of player pressure, so hordes flank and surround
  instead of conga-lining into one gun lane,
- special events (a Stalker on the player who's safest).
Director decisions are logged (`/bs director log`) so balance passes can read why a night felt wrong.
All curves in `director.json`; party-size inputs through `PartyScaling`.

### 3c. Economy & risk-reward — `game` + `world`

Souls (boss currency) become the run economy; all of these are world objects, no menus ("show, don't tell"):
- **Blood altar** (ruins, camps): pay 4 max hearts *or* a random owned boon → one legendary-tier roll.
- **Cursed chest** (visibly wrong, black particles): great loot + instantly rolls the next siege's
  modifier up a tier.
- **Challenge pylon** (overworld/act wilds): survive 60 s in a shrinking circle → guaranteed legendary.
  Loud; attracts the horde.
- **Soul wager** at the camp shopkeeper at siege start: stake souls; nobody goes down → 3× back.
- **Overclock horn** at the camp: start the next siege early at +1 wave number for +1 relic.

### 3d. The day/prep phase gets verbs — `game` + `world`

- **Shopkeeper** at each camp: sells ammo, common guns, barricade kits, rerolls a gun's perks for souls.
  Stock quality follows the act. (Entity, not GUI-only: it can be defended, it comments on your build.)
- **Forge ritual** at the camp: consume two guns of the same rarity → one gun of the next rarity with
  perks rolled from both pools. A crafting *decision*, not a recipe.
- **Tomorrow's forecast:** the next siege's modifier is visible a full prep-phase early, through the
  world (sky tint, sounds, shopkeeper line) — turning modifiers from surprises into plans.

### 3e. Objectives inside sieges — `game`

~1 in 3 sieges rolls a side objective, paid in an extra relic:
defend a crashed supply drop 90 s; carry a volatile core to the camp while the horde targets the
carrier; kill the fleeing glowing zombie before it escapes; keep the generator (light source) alive.
Data-driven list so acts can add themed ones.

### 3f. Origins (classes) — `game` + `movement`

Owner (2026-10-11): **movement itself is class identity.** Not everyone is Mario *and* Spider-Man —
you pick where you start. One guardrail: the **Souls core stays universal** (dodge roll + i-frames,
stamina, the downed/revive loop, the improved hold-jump feel), because the combat rhythm and revive
play must read the same for the whole party; everything above that baseline is origin identity.
Character customization = origin × boons × gear: the origin sets the movement verbs, boons deepen them
(§3a tag weighting), gear does the rest.

An origin = **movement signature + kit/passive + drawback**, picked at run start (item in the starter
kit; solo gets a default). Seed ten; the pool is data-driven and open (bench: Pale Courier — starts with
a weaker Wraith Steed; Warden — taunt horn, +2 hearts, −15% speed):

| Origin | Movement signature | Kit / passive | Drawback |
|---|---|---|---|
| **Drifter** | the full Mario kit: triple-jump chain + innate double jump | +10% move speed | cannot climb or swing |
| **Webslinger** | web swing + wall climb + ledge grab | Web Shooter from minute 0 | no triple/double jump; −25 max stamina |
| **Tidecaller** | fish: infinite water breathing, dolphin-speed swimming, clear vision and full-speed gunfire underwater | heals in rain; +damage to blight mobs | burns 2× longer |
| **Ashborn** | fire & lava immune; walks on magma; swims lava like water (Acts 2–3 open up: lava routes, fire floors ignorable) | fire-tag boons weighted | water slows them badly; −10% move speed |
| **Zephyr** | flight: stamina-drain glide wings; rides updrafts hard (Skyrift updrafts, dusk thermals elsewhere) | no fall damage | −2 max hearts (hollow bones) |
| **Juggernaut** | baseline only — can't climb, swing, or glide | scrap-plate set; revives allies 2× faster; knockback immune | −15% move speed |
| **Gunslinger** | baseline | rare Hand Cannon; +20% reload | −25 max stamina |
| **Tinkerer** | baseline | wrench: deployable scrap turret, half-cost barricades/vehicle repair | ammo drops −25% |
| **Bloodbound** | baseline + roll leaves a brief blinding blood mist | blood altars cost half; +1 heart per boss kill | healing items 50% effective |
| **Gambler** | baseline | every relic offers 4 cards, one always a Wildcard | all cards are hidden until taken |

- **Implementation:** `movement` keeps every mechanic but gates each behind an origin **capability set**
  (contract in `common`, synced from `game`). Classic mode grants everyone everything — the v1 behavior,
  for free.
- **Dimension interplay is deliberate:** Ashborn owns Nether/Blightheart terrain, Tidecaller owns oceans
  and blight pools, Zephyr owns the Skyrift. Party composition becomes a routing decision.
- Movement boons can cross-train late (a legendary "Webslinger's Memento" grants climb to anyone) so a
  long greedy run can still collect verbs — at legendary prices.
- Balance rule: **every origin must be able to finish solo**; tested at 1 and 8 players, as ever.

### 3g. Solo & co-op feel — `game`

Solo:
- **Last Stand** (solo only, once per siege): going down starts a 20 s crawl with the pistol;
  3 kills while down → self-revive. Otherwise run over, as now.
- Director solo tuning: fewer, scarier (more elites/stalkers, smaller hordes) via `PartyScaling` inputs.
- Companions are the solo axis: Wraith Steed, Tinkerer turret, a boon that makes the steed fight.

Co-op:
- **Cross-player boons**: "your precision kills reload the nearest ally", "revives grant both players
  10 s of i-frames", "you and a sworn partner share the higher stamina regen" (pick-partner at the camp).
- **Downed players stay in the game**: crawl slowly, fire the pistol at half damage, ping.
- Shared stakes: the wager, cursed chests and the overclock horn are party arguments by design; the
  relic altar can rarely offer a **party relic** needing a majority to open.
- **Run summary v2**: styled per-player cards — waves, boss kills, best boon, build tags, revives given,
  "most time spent downed". Screenshot-worthy; it is the social artifact.

### 3h. Bosses v2 — `bosses`

New reusable `Attack` archetypes (every boss and the raid can compose them):
- ground hazards: expanding rings, lingering pools, line/cross AoEs, a rotating beam;
- projectile choreography: radial bursts with a gap, 1 s delayed-homing orbs (makes roll i-frames matter
  at range), flight-phase spirals;
- summon-and-shield: shield until 3 spawned adds die (adds via `common` spawn-by-id);
- **posture/stagger**: hidden poise filled by precision hits and well-timed rolls → 3 s visceral window
  at 2× damage with its own animation. This is the Souls rhythm; highest priority of the list;
- **part breaks**: sustained damage on a `BossPart` severs it — lose an attack, gain speed
  (Stormplate's pauldron, Ashwyrm's tail);
- feints (telegraph A cancels into B — both must have one safe answer) gated to late phases/endless.

Movement vocabulary: leap with shadow marker; **Ashwyrm burrow phase** (dirt-plume tracking, eruption
telegraph); reactive sidestep vs. aim with a 6–8 tick reaction floor and cooldown; charge that gets
stuck 2 s on hitting terrain (terrain as a weapon); a wall/ceiling-crawling boss in the Blightheart.

Elite/boss **affixes**: Burning / Frosted / Volatile / Magnetic prefixes = palette shift + one modifier.
Applies to wave elites and field-boss rolls. Cheapest content multiplier we have.

Presentation: 1.5 s boss name-splash + sound sting at fog-wall rise; 2-tick hit-stop on heavy hits;
emissive crack textures intensifying per phase; damage-state texture swaps at thresholds.

Fairness invariants (config-locked, not tunable per boss): telegraphs never lie; reaction floors;
adaptation caps.

### 3i. Mob & boss AI (the "JEV" track) — `mobs` + `bosses` + `game`

Tier 1 (ship in v2): utility scoring in `AttackPicker` (distance, angle, target stamina, just-rolled,
airborne, per-attack cooldown/repeat penalty); the Director (§3b); influence-map horde flanking.
Tier 2 (v2 stretch): per-player roll-timing histograms → bounded windup delays ("reads");
a frequency table over player action sequences to pick punishes.
Tier 3 (post-v2 research, explicitly deferred): small policy MLP via ONNX self-play for boss movement;
a learned Director trained on real run summaries. Not scheduled.

## 4. Module ownership (v2 additions)

| Module | v2 additions |
|---|---|
| `common` | synergy tag vocabulary; act/siege state + events (act started, siege started/ended, portal charging/ignited); Director stress inputs contract; origin registry + **movement capability set** contract |
| `game` | SiegeClock, acts, Director, economy objects' logic, shopkeeper trades, origins, boon levels/curses/trinkets, Last Stand, cross-player boons, run summary v2, Classic mode flag |
| `world` | **both new dimensions** (worldgen, portals, camps, act ores/gear materials), altar/chest/pylon structures |
| `mobs` | act 3/4 rosters (blight + sky mobs), influence-map pathing, affix system on elites |
| `bosses` | attack archetypes, posture, part breaks, 2 new bosses, boss relocations per act, affixes on field bosses, name-splash |
| `guns` | perk tags, forge ritual hooks, act-tier recipes |
| `movement` | gate each mechanic behind the origin capability set (Classic mode = all granted); Zephyr glide + updrafts; boon cross-training hooks |
| `vehicles` | act-aware spawning (wrecks in all acts; the speeder's radioactive core moves to the Blightheart) |

Contract changes land as their own small PRs in Stage 5 and are then frozen, per v1 rules.
Everything that scales with players goes through `PartyScaling`, tested at 1 and 8. Every number in config.

**Content standards:** all v2 content follows `docs/CONTENT.md` (owner's call, 2026-10-11; lands with
tmldude/BlockSouls#35 and is CI-enforced). The v2-specific consequences:
- **Recipe web (R1/R2):** every act material, forge ingredient and boss drop must be an ingredient in
  2+ recipes; crafted items must connect. **Souls** drop from bosses, so beyond being the shop currency
  they get 2+ recipes of their own (soul-infused crafting is on-theme — e.g. a recipe path into trinket
  or keystone crafting). Gear and **trinkets** are end items: exempt with a one-line reason each.
- CONTENT.md's boss **readability rule** (>30% health ⇒ ≥15 ticks windup, config-locked) is the same
  invariant as §3h's "telegraphs never lie" — bosses v2 attack archetypes must satisfy it.
- New boons/curses/trinkets each add a row to the boon icon generator; mobs/bosses/dimensions use the
  generated-asset pipelines and test rosters CONTENT.md names.
- CONTENT.md's "progression stays vanilla-shaped (… stronghold → End)" line describes the **Classic**
  shape; in Expedition the act portals replace the stronghold (DECISIONS.md logs the resolution).
- **Releases:** coordinate with the v1 orchestrator before cutting any v2 version from main (they cut
  v0.10.x; we announce before cutting v2 tags).

## 5. Session stages (continuing docs/sessions/README.md's numbering; v1's Stage 4 / v0.10.0 is in flight)

Coordination with Stage 4 (agreed with the v1 orchestrator session, 2026-10-11): Stage 5 may start now
from current `main` (v0.9.0); nothing in Stage 4 touches `game/run`, the wave scheduler, or existing
`common` contracts. Until `claude/boon-icons` merges, v2 sessions don't touch `game/player/boon/**` or
`game`'s client HUD. Stage 6 waits for v0.10.0 (collisions: world structures, bosses arena code,
vehicles, HUD, sounds.json). v2 PRs are opened and merged by the v2 orchestrator; Stage 4's by the v1
orchestrator — neither merges the other's.

- **Stage 5 — contracts & spine (one session, sequential; brief: `docs/sessions/v2-spine.md`):**
  `common` v2 contracts (own small PR first); `SiegeClock` (sun binding + synthetic vibe-switch cycle) +
  act state machine + threat scaling (`threat.json`); player-triggered portal charge; Classic/Expedition
  flag — **Classic stays the default until v2 ships**, so merging the spine is behavior-neutral for the
  live pack; migration of the v1 scheduler under it; gametest: a scripted 4-act run skeleton with empty
  acts, advanced by portal charges. `integration/FullPackClientTest`'s dusk step (advance_time +
  `time set 12700` + 400 ticks to wave 1) must stay green or be updated in the same PR.
- **Stage 6 — features (parallel, one worktree each; after v0.10.0):**
  | Session | Builds |
  |---|---|
  | Dimensions | Blightheart + Skyrift worldgen, portals, camps, act ores |
  | Director & economy | §3b + §3c + §3e |
  | Builds & origins | §3a (incl. levels, curses, trinkets) + §3f (incl. `movement` capability gating + glide) + forge ritual + shopkeeper trades |
  | Bosses v2 | §3h archetypes + posture + part breaks + affixes |
  | New bosses | Blightheart boss + Skyrift boss (design sub-sessions for models, per v1 practice) |
  | Mobs v2 | act rosters, influence map, elite affixes |
  | Players v2 | solo/co-op (§3g), run summary v2, HUD build line |
- **Stage 7 — intersections (sequential):** acts wired to rosters/drops/recipes; Director tuned;
  balance to a ~2.5 h median; perf with 8 players in act 3; full-run gametest across all four acts;
  Expedition becomes the default run shape; `v2.0.0`.

## 6. Risks

- **Two custom dimensions is the big one.** Custom dimension JSON + worldgen on 26.3 is well-trodden,
  but floating-island gen and a sky void need real iteration; the Skyrift also stresses movement
  (falling = death?) — give it slow-fall updrafts near island edges rather than nerfing the void.
- **The Director can make nights feel unfair** if stress inputs are wrong; its decision log and hard
  config bounds exist for exactly that. Ship conservative defaults.
- **Synergy balance explosion**: tags multiply; cap stacking (one keystone per trigger tag) from day one.
- **Movement gating can hurt the feel layer** — v1's movement is the game's best-tested joy, and an
  origin that locks most of it away must be fun for other reasons. Mitigations: the Souls core and
  hold-jump stay universal, every movement origin's signature must feel as good as the v1 kit, legendary
  cross-training boons exist, and solo-completable is a hard rule per origin.
- **Pacing is player-owned, so it can bloat or trivialize.** The threat curve on bosses (§1) is the
  balancing lever: lingering must get genuinely dangerous and rushing genuinely under-geared. Playtest
  the curve; keep the counter visible so the choice is informed. A config soft-cap (threat growth
  accelerates after N nights in one act) is the fallback if parties turtle.
- **Classic mode drift**: it shares the scheduler; CI keeps one classic-mode gametest alive so v1 shape
  never silently breaks.
