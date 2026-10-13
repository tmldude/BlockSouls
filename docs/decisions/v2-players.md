# Decisions: v2-players (Stage 6A)

Defaults picked while building origins, movement classes, solo Last Stand, downed gameplay and the run summary v2
(`docs/sessions/v2-players.md`). Same style as `DECISIONS.md`; where they conflict, `DECISIONS.md` wins.

## Contract additions (`common`, first PR)

- **`Capabilities` default set = the v1 verbs.** With no set attached, `has()` is true for climb, swing and
  multi-jump (`Capabilities.V1_VERBS`) and false for `GLIDE`. Glide is v2-only (Zephyr): an all-granted player
  pressing jump in the air must keep v1 behavior, so verbs v1 never had are opt-in through an explicit set
  (an origin, or a cross-training `grant`). `grant` stays a no-op on the default set, as before.
- **`PlayerStatus.canShoot(player)`** replaces `!isDowned(player)` as the gun gate. Downed players may fire
  while the game has downed shooting on (`setDownedShooting`, from `game-players.json`); the v1 rule is the
  `false` default. **Handoff:** `guns` has three `isDowned` checks (`GunServer.canShoot`, `GunItem.use`,
  `GunClient`) that must become `PlayerStatus.canShoot` for downed gameplay to work in the full pack; the game
  side (lent pistol, half damage) is built and tested without it.
- **`PlayerMoveEvents.ROLLED` / `GLIDE_STARTED`** (server): movement fires them; origins react (Bloodbound's
  blood mist on roll).
- **`PlayerTraits.STAMINA_MAX_PENALTY`**: −25 max stamina per level (movement's `traits.staminaMaxPenaltyPerLevel`,
  floor `traits.staminaMaxFloor`). Origins with "−25 max stamina" set level 1. A trait rather than a new
  attribute because stamina is movement's own number, not a vanilla attribute.

## Classes replace origins (owner design change, 2026-10-12)

- **Four classes, data-driven** (`data/blocksouls_game/classes/*.json`): Warden, Striker, Arcanist, Ranger. The ten
  PLAN2 §3f origins became the source pool: Juggernaut → Warden, Bloodbound → Striker, Gambler/Tidecaller/Zephyr →
  Arcanist, Gunslinger → Ranger. The pool stays open: a datapack can add a class JSON.
- **Universal movement kit for everyone.** Roll, hold-jump, climb, swing and multi-jump are never removed by a class;
  `signature_capabilities` can only *add* verbs (none of the four ship one: glide is a Ranger rune). `Capabilities`
  gating now serves runes and cross-training, not restriction. Picking or clearing a class never removes a verb
  something else granted.
- **Class numbers** (all in the JSON): Warden +2 hearts, knockback immune, revives 2× faster, −15% speed, slam landing
  (fall ≥ 4 blocks: 4 damage + 1.2 knockback to enemies within 3 blocks, the fall damage forgiven). Striker +2 attack
  damage, +4 roll i-frame ticks (`roll_iframes` level 2), free rolls after a kill, blinding blood mist on roll, +1 heart
  per boss kill (capped at `classes.bossKillHeartsCap` = 10), heals at 75%. Arcanist +1 Luck, regeneration in rain,
  relics offer 4 cards with a Wildcard always last, hover 30 ticks per leap, −2 hearts. Ranger +15% speed, half fall
  damage, +25% reload, rare Hand Cannon kit, ammo drops ×1.25 (inert until `guns`/`mobs` read
  `PlayerClasses.rules().ammoDropMultiplier()`), −25 max stamina.
- **Kits:** Warden the Scrap-Plate set, Striker an iron sword, Arcanist 4 ender pearls (blink utility; nothing class-
  specific exists yet), Ranger a rare Hand Cannon rolled from `loot_table/classes/ranger.json`. Handed over once per
  class per run, so re-picking never duplicates a kit.
- **Signature abilities** are named by id in the class JSON and match `abilities.json → kit.signatureByClass`
  (Warden `taunt_slam`, Striker `ground_pound`, Arcanist `ember_wave`, Ranger `fan_the_hammer`). The abilities module
  reads the class through `common`'s `Origins` contract: `PlayerClasses` installs `Origins.setProvider` (the class id;
  `null` without a class). The contract keeps its v1 name: renaming a merged contract is churn for no behavior.
- **Hover is Slow Falling, refreshed.** The Arcanist hover holds Slow Falling for `classes.hoverRefreshTicks` (3) past
  each tick jump is held while falling, up to `hover_ticks` per airborne stint. Movement is client-authoritative;
  an effect the client predicts stays smooth where per-tick server motion packets would rubber-band.
- **Slam landing is server-side.** It fires on the tick the server's fall distance drops back to 0 after ≥
  `slam_min_fall` blocks, and the fall damage is cancelled through `ServerLivingEntityEvents.ALLOW_DAMAGE` while the
  fall distance is still set. Only `Enemy` mobs are hit, never players.
- **Class rule readers.** The heal multiplier is applied by the game mixin on `LivingEntity.heal` (multiplies with the
  build rules' Hemorrhage curse). Relic shape: `RelicOffers` takes `max(cardsPerRelic, relicCards)` cards and a
  Wildcard chance of 1.0 when `relicAlwaysWildcard`. `relicHidden` and `altarCostMultiplier` stay parsed for a
  future class but nothing ships them.
- **Command:** `/bs class list|set|clear|select|sigil|show`.

## Rune trees (permanent, per class)

- **Six runes per class, 24 total** (`data/blocksouls_game/runes/*.json`): a small tree per class with `requires`
  edges, costs 20 / 40 / 60 rune souls (`runes.costMultiplier` scales them all). Runes apply only while playing their
  class and stack on top of the class and boons. Glide is the Ranger's *Wings* rune.
- **Profiles on disk per player** (`<server dir>/blocksouls/runes/<uuid>.json`, outside the world so a world reset keeps them): permanent across runs;
  a missing or broken file is an empty profile, never a crash.
- **Payout at run end** (`runes` in `game-players.json`): 10 souls per siege survived × (1 + 0.05 × threat), plus 25%
  of unspent in-run souls (soul items are taken), never below 5 once a siege was survived. Per player, so not party-
  scaled; playing well is the income, hoarding never beats spending.

## Run summary v2

- **Cards at run end** for every online player: class crest, boss kills, revives given, time downed, best boon
  (keystone > tier > level), the peak build line (top `summary.buildTags` = 3 synergy tags, sampled every
  `summary.buildSampleIntervalTicks` = 100 and on every boon, so a player who lost boons on going down still shows the
  build they had), and the rune souls paid. One "Most time downed" badge for the player with the most downed time
  (ties to the first; none if nobody went down). The run line shows wave, threat, nights, highest act open, bosses.
- **Lives in `game/player/summary/`**, not `game/run/` (another lane): it listens to `RUN_OVER` after the rune payout.
  The v1 title + chat summary stays. The cards are a second JSON line in `blocksouls-runs.log` with `"kind": "players"`
  next to the run's own line.
- **Layout:** up to `summary.columns` = 4 cards per row, so 8 players are two rows; the last row is centered; cards
  shrink to fit but not under 96 px. Tested at 1 and 8.
- **Tallies are in memory:** a server restart mid-run resets revives/downed time/best boon for that run. Acceptable
  for a summary; persisting them is a follow-up if it matters.
- `/bs summary show` opens the cards so far (used by the client test).
