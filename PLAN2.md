# BLOCKSOULS v2 — "Expedition" (design + build plan)

v1 (PLAN.md) ships first; this plan is the next version. It folds in the owner's 2026-10-11 ideation
pass. Everything in PLAN.md still applies unless this file says otherwise; DECISIONS.md still wins over both.

**Status of the big call:** the owner approved the systems below. The world model (§1–§2, open-dimension
acts rather than closed maps) is the orchestrator's recommendation, pending the owner's confirmation —
see DECISIONS.md "v2 direction". Classic mode (§2e) exists so playtests can compare both shapes.

---

## 0. The pitch

v1 is "Minecraft + night waves." v2 is its own game: a 1–8 player roguelike **expedition through four
acts** — Overworld → infected Nether → the Blightheart → the Skyrift — ending in the End raid. Each act
is a whole open dimension: mine its ores, build, roam, fight its field bosses. Three **sieges** per act;
clearing them ignites the portal to the next act. You choose when to step through — lingering to loot is
allowed and the difficulty ratchet keeps turning. Builds emerge from tag synergies across boons, guns and
enchantments; an AI Director runs the sieges; every run ends in a shareable run summary.

Run length target: **~2.5 hours** (≈35 min/act + finale). All pacing numbers in config.

## 1. The act structure

| Act | Dimension | Entry | Sieges | Resource tier | Field bosses | Camp |
|---|---|---|---|---|---|---|
| 1 | Overworld | run start | 3 night-sieges (dusk-triggered, dawn pause as in v1) | scrap, gunmetal | Pale Rider, Ashwyrm | spawns near run start |
| 2 | Infected Nether (exists) | nether portal, ignited by the Act 1 portal siege | 3 horn-sieges | blight ore / blight cores | Furnace Knight | near arrival portal |
| 3 | **The Blightheart** (new dimension; the source of the infection) | portal built deep in the Nether, ignited by the Act 2 portal siege | 3 horn-sieges | blightheart ore (name TBD) | 1 new boss (blight-themed; e.g. a wall-crawling blight knight that uses the players' own climb mechanic) | near arrival portal |
| 4 | **The Skyrift** (new sky dimension; floating islands, storms) | glowstone-framed portal, ignited by the Act 3 portal siege | 3 horn-sieges | skysteel (name TBD) | Stormplate Knight (relocated home), 1 new boss | near arrival portal |
| Finale | The End (existing raid) | final portal appears at the Act 4 camp after siege 3 | — | — | reworked Ender Dragon (exists) | — |

- **Dimension names are placeholders** — owner's taste call. Original names only (no "Aether").
- Strongholds / Ender Eye hunting are **removed from the run** (Classic mode keeps them).
- Endless mode after the finale: endless sieges in the act of the party's choice.

### Sieges, not sun

Only the overworld has a day/night cycle. A **siege** is the v2 unit of assault:
- Act 1 sieges are the v1 night-waves (dusk start, several waves chained, dawn pause). Unchanged.
- Acts 2–4 sieges are horn-signaled events: prep timer (config, default ~8 min) → horn + title → 2–4
  chained waves → "dawn" event (siege end; ordinary wave mobs die, elites persist, relic drops).
- The wave counter stays global and only counts up, across acts. Siege waves draw from the act's roster.
- `game` grows a `SiegeClock` abstraction over the v1 scheduler: overworld binds it to the sun,
  other dimensions to timers. One code path for wave chaining, rewards, dawn rules.

### Portal siege (act finale)

Clearing siege 3 starts the **portal charge**: the next act's portal frame (pre-generated near the camp,
or the party's own nether portal in Act 1) begins charging over ~3 minutes while the Director throws a
siege at it. Charge completes → portal ignites permanently. Players never craft a weird item or hunt a
structure; the climax is a defense fight. Wiping the charge attempt just means re-triggering it.

## 2. Open-world stance (the rules that keep acts from feeling like rails)

a. **The portal never pulls.** It ignites and waits. Staying to mine/loot/over-prepare is always legal;
   sieges keep coming on the clock and the wave counter keeps climbing. Advancing is a party decision.
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

### 3f. Origins (run-start classes) — `game` (player side)

Picked at run start (item in the starter kit; solo defaults allowed). Each = kit + passive + weighted
boon tags + one drawback. Seed eight ("go crazy" per owner):

| Origin | Kit / passive | Drawback |
|---|---|---|
| Gunslinger | rare Hand Cannon, +20% reload | −25 max stamina |
| Webhead | Web Shooter from minute 0, swing boons weighted | −2 max hearts |
| Juggernaut | scrap-plate set, revives allies 2× faster | can't triple-jump |
| Tinkerer | wrench: deployable scrap turret, half-cost barricades/vehicle repair | ammo drops −25% |
| Bloodbound | blood altars cost half; +1 heart per boss killed | healing items 50% effective |
| Pale Courier | starts with a (weaker) Wraith Steed whistle | no starter pistol |
| Warden | taunt horn (pulls aggro 10 s), +2 hearts | −15% move speed |
| Gambler | every relic offers 4 cards, one always Wildcard | all cards are hidden until taken |

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
| `common` | synergy tag vocabulary; act/siege state + events (act started, siege started/ended, portal charging/ignited); Director stress inputs contract; origin registry contract |
| `game` | SiegeClock, acts, Director, economy objects' logic, shopkeeper trades, origins, Last Stand, cross-player boons, run summary v2, Classic mode flag |
| `world` | **both new dimensions** (worldgen, portals, camps, act ores/gear materials), altar/chest/pylon structures |
| `mobs` | act 3/4 rosters (blight + sky mobs), influence-map pathing, affix system on elites |
| `bosses` | attack archetypes, posture, part breaks, 2 new bosses, boss relocations per act, affixes on field bosses, name-splash |
| `guns` | perk tags, forge ritual hooks, act-tier recipes |
| `movement` | nothing structural (boon hooks only) |
| `vehicles` | act-aware spawning (wrecks in all acts; the speeder's radioactive core moves to the Blightheart) |

Contract changes land as their own small PRs in Wave 4 and are then frozen, per v1 rules.
Everything that scales with players goes through `PartyScaling`, tested at 1 and 8. Every number in config.

## 5. Session waves (continuing PLAN.md §8; v1 waves 0–3 finish first)

- **Wave 4 — contracts & spine (one session, sequential):** `common` v2 contracts; `SiegeClock` +
  act state machine with the overworld binding; Classic/Expedition flag; migration of the v1 scheduler
  under it; gametest: a scripted 4-act run skeleton with empty acts.
- **Wave 5 — features (parallel, one worktree each):**
  | Session | Builds |
  |---|---|
  | Dimensions | Blightheart + Skyrift worldgen, portals, camps, act ores |
  | Director & economy | §3b + §3c + §3e |
  | Builds & origins | §3a + §3f + forge ritual + shopkeeper trades |
  | Bosses v2 | §3h archetypes + posture + part breaks + affixes |
  | New bosses | Blightheart boss + Skyrift boss (design sub-sessions for models, per v1 practice) |
  | Mobs v2 | act rosters, influence map, elite affixes |
  | Players v2 | solo/co-op (§3g), run summary v2, HUD build line |
- **Wave 6 — intersections (sequential):** acts wired to rosters/drops/recipes; Director tuned;
  balance to ~2.5 h; perf with 8 players in act 3; full-run gametest across all four acts; `v2.0.0`.

## 6. Risks

- **Two custom dimensions is the big one.** Custom dimension JSON + worldgen on 26.3 is well-trodden,
  but floating-island gen and a sky void need real iteration; the Skyrift also stresses movement
  (falling = death?) — give it slow-fall updrafts near island edges rather than nerfing the void.
- **The Director can make nights feel unfair** if stress inputs are wrong; its decision log and hard
  config bounds exist for exactly that. Ship conservative defaults.
- **Synergy balance explosion**: tags multiply; cap stacking (one keystone per trigger tag) from day one.
- **Pacing**: 4 acts can drag; sieges per act (3) and prep timers are config so playtests can cut to
  2-siege acts without code.
- **Classic mode drift**: it shares the scheduler; CI keeps one classic-mode gametest alive so v1 shape
  never silently breaks.
