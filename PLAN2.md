# BLOCKSOULS v2 — "Expedition" (design + build plan)

v1 (PLAN.md) ships first; this plan is the next version. It folds in the owner's 2026-10-11 ideation
pass. Everything in PLAN.md still applies unless this file says otherwise; DECISIONS.md still wins over both.

**Status of the big call:** the owner confirmed the open-dimension act model (2026-10-11): acts are
open dimensions, not closed maps; **no fixed siege count per act** — pacing is entirely the party's;
day/night rhythm exists in every dimension (synthetic where there's no sun). Classic mode (§2e) stays
so playtests can compare run shapes.

---

## 0. The pitch — the Floor Loop (revised 2026-10-12 after the owner's playtest)

v1 is "Minecraft + night waves." The playtest verdict: the roguelike parts (loot, boons, guns, bosses,
movement) are the fun; Minecraft's survival parts (mining, recipe-hunting, waiting) are the drag. So v2
**keeps Minecraft as the engine and deletes the survival loop**: a 1–8 player roguelike run across
**six floors** — small, dense, hand-shaped maps on a timer — each a day of *lots to do* around a
**shopkeeper at the center**, then a siege, then the next floor. Closer to Enter the Gungeon / Risk of
Rain / Slay the Spire than to a survival world. Fast: a floor is **5–10 minutes of day + the siege**; a
run is ~60–90 minutes. You get strong *fast* and the game gets strong with you.

**Design laws:**
1. **No recipe to learn, no Y-level to grind.** Everything you can get is on the shopkeeper's board with
   its price shown as *icons of where the ingredients come from* (a tree, an ore node, a mob). Crafting is
   trading. Resources are on the surface or in one shallow cave; ore nodes pop in a few hits.
2. **A lottery you keep winning.** A small win every 30–60 s (crates, nodes, drops), a visible jackpot
   every floor (mini-boss, secret, event), pity so every player leaves each floor stronger. Difficulty
   matches through the threat ratchet and the Director.
3. **Lots to do, all optional, all legible.** On every floor: resources, a structure, a mini-boss you can
   choose to fight, one secret, one random event, a cave dungeon, a wreck. None mandatory; the day timer
   is the only pressure, and *you can end the day early for a buff* (risk vs. reward, every floor).
4. **Show, don't tell.** Every objective is visible or hinted in-world; tooltips state mechanics.
5. **Pacing budget:** no stretch over ~90 s without a decision or a threat; shooting something that
   matters within the first 3 minutes of a run.

**Focus order** (owner): builds, guns, abilities and boons first; then pacing; then everything else.

## 1. A floor

A floor is a **small island map** (~150×150 blocks, config) in a dedicated void dimension: ground level
with terrain and trees, a glass wall at the edge, void beyond. Floors are **templates** from a pool,
picked and varied per run (layout, resource placement, which structure, which mini-boss, where the
secret is), and the pool **escalates subtly with floor number** (Gungeon-style: more elites, stingier
nodes, meaner mini-boss) — the kind of ramp players feel but don't notice.

| Thing on the floor | What it gives | Notes |
|---|---|---|
| **Shopkeeper + camp** (center) | The trade board; the horn; relic altar; forge/upgrade bench | Safe-ish aura; the siege converges here |
| **Resource nodes** (surface + shallow cave) | Wood, scrap, gunmetal, floor-specific rare (blight, skysteel, ...) | Pop in 2–4 hits with a burst; no deep mining |
| **Structure** (ruin / wreck yard / outpost; pool per floor) | A chest (gear, enchantment, early boon), a wall-buy, lore-free set dressing | Always one; mini-dungeon layout |
| **Mini-boss** (rotating from the field-boss pool) | Trinket pick-1-of-3, rare resource, souls | Optional; a fog-wall arena on the map |
| **Secret** (one per floor) | Exotic / legendary boon / trinket | Gungeon-style: a cracked wall, a hidden drop under the structure, a hint item; findable, never required |
| **Random event** (one per floor; the StS "?") | A choice with a risk: altar, cursed chest, challenge pylon, wandering trader, a stranded ally, an ambush | Data-driven event pool |
| **Cave dungeon** (one entrance) | A short underground run: nodes, a chest, sometimes the secret | Breakable terrain is *tactical* (cover, shortcuts), not an economy |
| **Wreck** (car) | Repair with **scrap lying around it** → drive the floor, carry the party, ram the siege | Repair is one interaction; parts are visible nearby |
| **Day mobs** | Interesting roaming mobs during the day (elites with affixes, scouts) | Director-placed |

**The day:** a visible timer (default 8 min, config per floor). Do what you like. **The horn** at the shop
ends the day early: the earlier you blow it, the bigger tonight's buff (loot rarity / a free boon /
souls) — the core risk-reward. When the timer runs out or the horn blows, **the siege** comes to the
camp (the v1 wave chain, Director-paced, threat-scaled). Survive → the **exit portal lights** at the camp
→ next floor. Floor 6 is the final boss arena.

**Six floors:** Surface → Caverns (the breakable-ground floor) → Infernal → the Blightheart → the Skyrift
→ the Final Arena (the reworked dragon raid). Each has its own template pool, resource rare, mob roster,
mini-boss rotation and look (floor "vibe switch" on the synthetic clock, §2).

## 2. The run shape and what it keeps from v1

- **Run shape** `game-run.json` → `runShape`: `"classic"` (v1 open world — stays as the live default
  until v2 ships), `"floors"` (this plan). The `"expedition"` open-dimension act model from the first v2
  draft is **superseded** by floors; its spine (SiegeClock, act machine, portal charge, threat) is reused:
  an act = a floor, the portal charge = the exit portal after a siege, the synthetic clock = the floor
  timer and its day/night look.
- **Classes, not origins** (owner): **four classes** in the Destiny mold — working names **Warden**
  (tank), **Striker** (warrior), **Arcanist** (mage), **Ranger** (archer) — each with a kit, a signature
  ability, a movement signature on top of the **universal v1 movement kit** (roll, hold-jump, climb,
  swing — all on, because it's fun and friction-free), and a **permanent rune tree**: meta-progression
  unlocked across runs with souls kept at run end. In-run power is boons, trinkets, gear, enchantments.
  The ten-origin list in §3f becomes flavor for class kits / runes.
- **Everything through the world:** early boons and relics come from structures, mini-bosses, secrets
  and events, not only dawn relics. Armor and enchantments drop and trade freely — getting strong is easy;
  the threat ratchet is what makes it matter.
- **Party of 1–8** stays: floor size, node counts, siege size and mini-boss health through `PartyScaling`.
- **Replayability levers:** template variation, resource mix per run, the rotating mini-boss, which event
  and secret you got, class × runes × boons, and the horn gamble.

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

### 3f. Classes and runes (was: origins) — `game` + `movement`

**Revised 2026-10-12 (§2):** four classes with permanent rune trees replace the ten origins; the universal
movement kit stays on for everyone and classes add a signature. The origin table below is kept as the
source pool for class kits, drawbacks and runes; `Capabilities` gating now serves runes (e.g. glide is a
Ranger rune), not class restriction.

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

### 3j. Guns & abilities v2 — `guns` + new module `abilities`

**Cooler guns** (`guns`):
- **Exotics, one per archetype**, each a rule-breaker with its own behavior class and synergy tags —
  seed list: a hand cannon whose **last round in the mag always ricochets** to a second target
  (`ricochet`); a sniper whose precision kills **mark** nearby enemies, and marked enemies take bonus
  damage from the whole party (`on_precision`, co-op); a shotgun whose blast **launches the shooter
  backward** — damage and movement tech in one trigger pull (`kinetic`, pairs with swing/glide); an auto
  rifle that **ramps fire rate while on-target** and overheats dramatically (`fire`); a rocket launcher
  that fires a **web-tether rocket** pinning two mobs together (`pull`). Exotic drops come from field
  bosses, challenge pylons and the legendary relic tier.
- **Gun feel pass**: punchier muzzle/impact VFX, hit markers with precision variant, kill confirmation
  sound, reload-cancel on roll (rolling finishes the reload at 80% — binds movement to gunplay), rarity
  glow on dropped guns.
- Every gun keeps its JSON definition; exotic behavior classes are the only code-per-gun.

**Abilities — a new axis** (new module `abilities`, Destiny-style): every player gets an active kit on
cooldowns, bound through `common`'s `Keybinds`, all data-tunable:
- **Tactical** (~25 s cooldown, pick one at run start or from drops): frag (arc, `explosion`), web bomb
  (AoE slow/anchor, `pull`), fire wall (`fire`), healing dart (`heal`, co-op).
- **Signature** (~45 s, set by the origin): Drifter ground-pound (jump-cancel slam, knockup); Webhead
  web-pull (yank a mob to you, or you to a wall); Ashborn ember wave; Tidecaller geyser (launches allies
  — a movement assist); Juggernaut taunt slam; Gunslinger fan-the-hammer (6 instant shots); Tinkerer
  overclock (turret frenzy); Bloodbound transfusion (steal health to revive faster).
- **Ultimate**, charged by kills and precision hits (minutes-scale): one big, loud, run-defining button —
  e.g. a storm barrage, a time of invulnerable melee, a party-wide overshield. Charge bar on the HUD.
- Abilities carry **synergy tags** and scale with boons (`on_kill` keystones feed ultimate charge, etc.);
  cooldowns/charges are config; everything through `PartyScaling` where party-dependent.
- Module rules: `abilities` is its own Fabric mod (`blocksouls_abilities`), talks only through `common`
  (new contract: ability registry, cooldown/charge attachment, cast payloads + events); `movement` and
  `guns` never import it.

### 3k. Mob & boss AI (the "JEV" track) — `mobs` + `bosses` + `game`

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
| `guns` | perk tags, forge ritual hooks, act-tier recipes, **exotics + gun feel pass** (§3j) |
| `abilities` (new) | tactical/signature/ultimate ability kit (§3j); contracts (registry, cooldown/charge, cast events) land in `common` |
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
- **Stage 6 — features (parallel, one worktree each; after v0.10.0).** Sessions start in priority
  order (owner, 2026-10-12: fun content and pacing first). **6A launches immediately; 6B as 6A
  sessions free up** (all of 6B still ships in v2):
  | Priority | Session | Builds |
  |---|---|---|
  | 6A | Builds & boons | §3a in full (tags, keystones, levels, curses, trinkets) + forge ritual + shopkeeper trades |
  | 6A | Guns & abilities | §3j: exotics, gun feel pass, the `abilities` module (tactical/signature/ultimate) |
  | 6A | Director & economy | §3b + §3c + §3e — the Director owns the **pacing budget** (§0), the day timer, the **horn** (end the day early for a buff), event pool, mini-boss rotation, day mobs, loot cadence; the **shopkeeper trade board** (prices as source icons) |
  | 6A | Origins & players | §3f (incl. `movement` capability gating + glide) + §3g solo/co-op + run summary v2 + HUD build line |
  | 6B | Bosses v2 | §3h archetypes + posture + part breaks + affixes |
  | 6B | New bosses | Blightheart boss + Skyrift boss (design sub-sessions for models, per v1 practice) |
  | 6B | Mobs v2 | act rosters, influence map, elite affixes |
  | 6A | **Floors** (replaces Dimensions) | the void floor dimension, the template pool + variation, resource nodes, structure/cave/secret/event/wreck placement, glass-and-void edge, floor escalation |
- **Stage 6C — floors 2–6 and the boards (parallel; after 6B; owner-accepted brief `docs/design/floors.md`):**
  `v2-boards` (forge + brew boards, crafting tables off) + `v2-floor2-infernal`, `v2-floor3-skyrift`,
  `v2-floor4-vault`, `v2-floor5-blight`; then `v2-floor6-arena`. Briefs in `docs/sessions/`.
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
