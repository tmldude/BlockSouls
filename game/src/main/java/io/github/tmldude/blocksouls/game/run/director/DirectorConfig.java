package io.github.tmldude.blocksouls.game.run.director;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The AI Director and the run economy, loaded from {@code config/blocksouls/director.json} (PLAN2 §3b, §3c, §3e).
 * Party-size inputs go through {@code PartyScaling} ({@code directorBurst}, {@code directorStressPool},
 * {@code directorEliteShare}); everything else is here. Decisions behind each default:
 * {@code docs/decisions/v2-director.md}.
 */
public class DirectorConfig {
	public static final String FILE = "director";
	/** Defaults revision {@link #migrate} brings older files up to. */
	public static final int CURRENT_DEFAULTS = 4; // 4: floor 3, Skyrift (Stage 6C)

	/** Which defaults revision this file was written against (0 = before the field existed). */
	public int defaultsVersion;

	/** Master switch. Off = the v1 wave scheduler exactly, in every shape. */
	public boolean enabled = true;
	/** The Director also runs in the classic (v1) run shape. Off by default: classic stays behavior-neutral. */
	public boolean enabledInClassic = false;

	public Stress stress = new Stress();
	public Levers levers = new Levers();
	public Influence influence = new Influence();
	public Pacing pacing = new Pacing();
	public Day day = new Day();
	public Horn horn = new Horn();
	public Events events = new Events();
	public DayMobs dayMobs = new DayMobs();
	public Loot loot = new Loot();
	public Objectives objectives = new Objectives();
	public Altar altar = new Altar();
	public CursedChest cursedChest = new CursedChest();
	public Pylon pylon = new Pylon();
	public Wager wager = new Wager();
	public Shop shop = new Shop();
	public Camp camp = new Camp();
	/** Entries kept for {@code /bs director log}. */
	public int logSize = 200;

	/** Brings a file written against older defaults up to {@link #CURRENT_DEFAULTS}. True if anything changed. */
	public static boolean migrate(DirectorConfig config) {
		if (config.defaultsVersion >= CURRENT_DEFAULTS) {
			return false;
		}
		// 1: the first shipped defaults; nothing to move yet.
		// 2: floor sessions' own events (floor-tagged) join an existing pool.
		if (config.defaultsVersion < 2 && config.events != null && config.events.pool != null) {
			for (EventSpec spec : new Events().pool) {
				if (!spec.floorTags.isEmpty() && config.events.pool.stream().noneMatch(old -> spec.id.equals(old.id))) {
					config.events.pool.add(spec);
				}
			}
		}
		// 3 (v2-floor5-blight): floor events that share a generic event's id (the blight altar and pylon) join too.
		if (config.defaultsVersion < 3 && config.events != null && config.events.pool != null) {
			for (EventSpec spec : new Events().pool) {
				if (!spec.floorTags.isEmpty() && config.events.pool.stream()
						.noneMatch(old -> spec.id.equals(old.id) && spec.floorTags.equals(old.floorTags))) {
					config.events.pool.add(spec);
				}
			}
		}
		// --- floor 3, Skyrift (Stage 6C) ---
		// 4: floors whose events and rosters landed after 3 (Skyrift) join a file already at 3; ids and tags kept by hand win.
		if (config.defaultsVersion < 4 && config.events != null) {
			if (config.events.pool != null) {
				for (EventSpec spec : new Events().pool) {
					if (!spec.floorTags.isEmpty() && config.events.pool.stream().noneMatch(old -> spec.id.equals(old.id))) {
						config.events.pool.add(spec);
					}
				}
			}
			if (config.events.miniBossesByFloorTag != null) {
				defaultFloorMiniBosses().forEach(config.events.miniBossesByFloorTag::putIfAbsent);
			}
		}
		// --- end Skyrift block
		config.defaultsVersion = CURRENT_DEFAULTS;
		return true;
	}

	/** Per-party stress, 0 (cruising) to 1 (battered). */
	public static class Stress {
		/** Seconds of history the damage, downs and DPS trend are read over. */
		public int windowSeconds = 30;
		/** Health a solo player loses in the window that reads as full damage stress (× {@code directorStressPool}). */
		public double damagePool = 30;
		/** Downs in the window per player that read as full downs stress. */
		public double downsPerPlayerForFull = 0.5;
		/** Ammo items per player (primary + special) at which ammo stress is 0. */
		public int ammoComfortPerPlayer = 96;
		/** Boons a party member "should" own per threat level; fewer reads as under-powered stress. */
		public double boonsPerThreat = 0.4;
		/** Weights of the five signals (normalised by their sum). */
		public double weightDamage = 0.4;
		public double weightDowns = 0.25;
		public double weightAmmo = 0.12;
		public double weightDps = 0.13;
		public double weightPower = 0.1;
		/** How fast the smoothed stress follows the raw reading each second (0–1). */
		public double smoothing = 0.25;
	}

	/** What the Director does with stress, all within these bounds. */
	public static class Levers {
		/** Stress above this starts a lull (spawns pause). */
		public double lullAbove = 0.75;
		/** Stress below this triggers a spike (an extra burst). */
		public double spikeBelow = 0.25;
		/** How long a lull lasts; the pacing budget's lull ceiling caps it too. */
		public int lullSeconds = 12;
		/** Cooldown after a lull or spike before the next one. */
		public int moodCooldownSeconds = 20;
		/** Wave mobs released per second at stress 0.5 (solo); scaled by {@code directorBurst}. */
		public double spawnsPerSecond = 4;
		/** Spawn-rate multiplier at stress 0 and at stress 1 (linear in between). */
		public double rateAtCalm = 1.6;
		public double rateAtBattered = 0.5;
		/** Mobs per burst (solo, × {@code directorBurst}); a spike releases {@code spikeBurstMultiplier} bursts at once. */
		public int burstSize = 5;
		public double spikeBurstMultiplier = 2.0;
		/** A party whose centre moved less than this many blocks over {@code campingSeconds} is camping: flankers come. */
		public double campingRadius = 8;
		public int campingSeconds = 20;
		/** Share of spawns sent to the flank sector while the party camps. */
		public double flankShare = 0.6;
		/** Share of spawns that are brutes while the party holds a choke (camping + low damage taken). */
		public double bruteShare = 0.35;
		/** Wave mob ids preferred as flankers / brutes when their composition is chosen. */
		public List<String> flankerMobs = new ArrayList<>(List.of("blocksouls_mobs:crawler", "blocksouls_mobs:ash_hound"));
		public List<String> bruteMobs = new ArrayList<>(List.of("blocksouls_mobs:brute", "blocksouls_mobs:mutant_brute"));
	}

	/** The coarse influence map of player pressure around the party (sectors around its centre). */
	public static class Influence {
		/** Compass sectors around the party's centre. */
		public int sectors = 8;
		/** Pressure a player adds to the sector they face (their gun lane), and to its neighbours ×{@code spread}. */
		public double lookPressure = 1.0;
		public double spread = 0.4;
		/** Pressure a wave-mob kill adds to its sector (where the guns are already pointed). */
		public double killPressure = 0.5;
		/** Pressure kept per second (the map forgets). */
		public double decayPerSecond = 0.9;
		/** How strongly spawns prefer the quietest sector: 0 = uniform, higher = sharper. */
		public double sharpness = 3.0;
	}

	/** The pacing budget (PLAN2 §0): nothing goes ~90 s without a decision or a threat. */
	public static class Pacing {
		/** The ceiling: seconds without a decision or a threat before the Director acts. */
		public int ceilingSeconds = 90;
		/** Longest a lull may last, whatever stress says. */
		public int lullCeilingSeconds = 20;
		/** Intermission tightening: a wave break is cut to this many seconds when the budget is nearly spent. */
		public int tightBreakSeconds = 3;
		/** Mobs in a quiet-day scout pack (× {@code directorBurst}). */
		public int scoutPack = 3;
		/** Wave mobs within this many blocks of a player count as a threat. */
		public double threatRadius = 24;
	}

	/** The day timer (one per floor/act), on the run's clock. */
	public static class Day {
		/** Day length per floor (minutes; × the Dawn Clock trinket's prep-time multiplier). */
		public double minutes = 8;
		/** Optional per-act overrides (index 0 = act 1); missing or ≤ 0 uses {@link #minutes}. */
		public List<Double> minutesByAct = new ArrayList<>();
		/** When the day runs out the siege (portal charge) starts on its own. */
		public boolean siegeAtDayEnd = true;
	}

	/** The camp horn: end the day early for a buff that grows with the time skipped. */
	public static class Horn {
		/** Souls per player per minute skipped. */
		public double soulsPerMinute = 3;
		/** Share of the day skipped at which tonight's drops roll a tier up. */
		public double rarityAt = 0.25;
		/** Share of the day skipped at which every player gets a free relic. */
		public double boonAt = 0.5;
		/** Tier of that relic: common / rare / legendary. */
		public String boonTier = "rare";
		/** Overclock (sneak-use): the siege starts this many threat levels higher… */
		public int overclockThreat = 1;
		/** …and pays this many extra relics per player if it holds. */
		public int overclockRelics = 1;
		public String overclockRelicTier = "rare";
		// --- floor 4, The Vault (Stage 6C): the siege twist ---
		/** On a floor carrying one of these tags the buff stops at one tier: souls and the drop-tier shift, no free relic. */
		public List<String> oneTierFloorTags = new ArrayList<>(List.of("vault"));
		// --- end floor 4 ---
	}

	/** The random event per floor (the StS "?"): data-driven pool. */
	public static class Events {
		public List<EventSpec> pool = new ArrayList<>(List.of(
				new EventSpec("blood_altar", 3, 1),
				new EventSpec("cursed_chest", 3, 1),
				new EventSpec("challenge_pylon", 2, 1),
				new EventSpec("wandering_trader", 2, 1),
				new EventSpec("stranded_ally", 2, 1),
				new EventSpec("ambush", 2, 2),
				// --- floor 4, The Vault (Stage 6C): placed by the world module through DirectorEvents.PLACE_EVENT ---
				new EventSpec("blocksouls_world:vault_prison", 4, 1, List.of("vault"), true),
				new EventSpec("blocksouls_world:trapped_hall", 4, 1, List.of("vault"), false),
				// v2-floor5-blight: the Blighted Surface's own events (the soul wager runs at every siege already)
				new EventSpec("blood_altar", 3, 1, List.of("blight"), false),
				new EventSpec("challenge_pylon", 3, 1, List.of("blight"), false),
				// --- floor 3, Skyrift (Stage 6C): placed by the world module through DirectorEvents.PLACE_EVENT ---
				new EventSpec("blocksouls_world:wind_altar", 4, 1, List.of("skyrift"), false),
				new EventSpec("blocksouls_world:falling_shard_chest", 4, 1, List.of("skyrift"), false),
				new EventSpec("blocksouls_world:bridge_ambush", 3, 1, List.of("skyrift"), true)));
		/**
		 * v2-floor5-blight: a floor carrying one of these tags rolls only its own (floor-tagged) events, not the
		 * generic pool, as long as one of them is eligible.
		 */
		public List<String> ownEventsOnlyFloorTags = new ArrayList<>(List.of("blight"));
		/** Without a floor's EVENT slot, the event is placed this far from the camp. */
		public int distanceFromCamp = 28;
		/** An ambush springs when a player comes this close to its marker. */
		public double ambushRadius = 8;
		/** Ambush pack (solo, × {@code directorBurst}); one of them is an affixed elite. */
		public int ambushPack = 4;
		/** Mini-bosses (boss ids), rotated per floor from the run seed. Unknown ids (bosses absent) are skipped. */
		public List<String> miniBosses = new ArrayList<>(List.of("blocksouls_bosses:furnace_knight", "blocksouls_bosses:pale_rider",
				"blocksouls_bosses:stormplate_knight"));
		/**
		 * A floor's own mini-boss roster, by floor tag: a floor carrying one of these tags rotates through that list
		 * instead of {@link #miniBosses} (first matching tag wins; unknown ids are skipped the same way).
		 * {@value DirectorRules#EARLIER} stands for the boss {@link #miniBosses} gave a seed-picked earlier act.
		 */
		public Map<String, List<String>> miniBossesByFloorTag = defaultFloorMiniBosses();
		/** Mini-boss from this act on (the first act is the tutorial floor). */
		public int miniBossFromAct = 2;
		/** Without a floor's MINI_BOSS slot, the mini-boss is placed this far from the camp. */
		public int miniBossDistance = 64;
	}

	static Map<String, List<String>> defaultFloorMiniBosses() {
		Map<String, List<String>> rosters = new LinkedHashMap<>();
		// --- floor 4, The Vault (Stage 6C): the blind hunter and the golem (world mini-bosses) ---
		rosters.put("vault", new ArrayList<>(List.of("blocksouls_world:vault_listener", "blocksouls_world:vault_colossus")));
		// v2-floor5-blight: Blightheart, the Pale Rider and one boss from an earlier floor ({@link DirectorRules#EARLIER})
		rosters.put("blight", new ArrayList<>(List.of("blocksouls_bosses:blightheart", "blocksouls_bosses:pale_rider", DirectorRules.EARLIER)));
		// --- floor 3, Skyrift (Stage 6C) ---
		rosters.put("skyrift", new ArrayList<>(List.of("blocksouls_bosses:stormplate_knight", "blocksouls_world:storm_roc",
				"blocksouls_world:glider_matriarch")));
		return rosters;
	}

	public static class EventSpec {
		public String id;
		public int weight;
		public int minAct;
		/** When not empty, only on a floor carrying at least one of these tags (a floor's own event). */
		public List<String> floorTags = new ArrayList<>();
		/** After the event is placed, an ambush also arms at its spot (springs like {@code ambush}). */
		public boolean ambush;

		public EventSpec() {
		}

		public EventSpec(String id, int weight, int minAct) {
			this.id = id;
			this.weight = weight;
			this.minAct = minAct;
		}

		public EventSpec(String id, int weight, int minAct, List<String> floorTags, boolean ambush) {
			this(id, weight, minAct);
			this.floorTags = new ArrayList<>(floorTags);
			this.ambush = ambush;
		}
	}

	/** Roaming day mobs (affixed elites, scouts), Director-placed during the day. */
	public static class DayMobs {
		public int intervalSeconds = 70;
		/** Pack size (solo, × {@code directorBurst}). */
		public int pack = 2;
		/** Chance a pack member is an affixed elite (plus the party's {@code directorEliteShare}). */
		public double eliteChance = 0.2;
		/** Day mobs alive at once, at most. */
		public int maxAlive = 10;
		/** Affixed elites: bonus max health (×), and the affix effects' amplifier. */
		public double eliteHealth = 1.0;
		public int affixAmplifier = 0;
	}

	/** Loot cadence: a small win every 30–60 s. */
	public static class Loot {
		public boolean enabled = true;
		public int minSeconds = 30;
		public int maxSeconds = 60;
		/** Kills, node pops, crates and economy objects count as wins; a kill counts once per this many seconds. */
		public int killWinSeconds = 20;
	}

	/** Siege side objectives (PLAN2 §3e): ~1 in 3 sieges, an extra relic on success. */
	public static class Objectives {
		public double chance = 0.34;
		public List<String> pool = new ArrayList<>(List.of("supply_drop", "volatile_core", "fleeing_zombie", "generator"));
		public String relicTier = "rare";
		/** Supply drop: defend it this long; wave mobs within 2 blocks chip its health each second. */
		public int supplyDropSeconds = 90;
		public int supplyDropHealth = 30;
		/** Generator: keep it alive until the siege ends. */
		public int generatorHealth = 40;
		/** Volatile core / supply drop / fleeing zombie: how far from the camp they appear. */
		public int distance = 30;
		/** Fleeing zombie: kill it within this many seconds or it escapes. */
		public int fleeingSeconds = 60;
		public double fleeingSpeed = 0.35;
		/** Volatile core: delivered within this many blocks of the camp. */
		public double deliverRadius = 4;
	}

	public static class Altar {
		/** Max hearts paid for the legendary roll (permanent for the run). */
		public int hearts = 4;
		/** Sneak-use pays a random owned boon instead; with no boons, a curse is taken instead of the hearts. */
		public boolean curseWithoutBoons = true;
	}

	public static class CursedChest {
		/** The next siege's modifier rolls up this many tiers (none → a modifier → Blood Moon). */
		public int modifierTiers = 1;
		public boolean curse = true;
	}

	public static class Pylon {
		public int seconds = 60;
		public double startRadius = 12;
		public double endRadius = 3;
		/** A horde pack (solo, × {@code directorBurst}) every this many seconds while it runs. */
		public int packEverySeconds = 8;
		public int pack = 3;
		/** Reward: "legendary" = a legendary relic per player in the circle; "trinket" = a trinket offer. */
		public String reward = "legendary";
	}

	public static class Wager {
		/** Souls staked per player. */
		public int stake = 10;
		public double payout = 3.0;
		/** Seconds after the siege starts during which the wager is open. */
		public int windowSeconds = 30;
	}

	/** The shopkeeper's trade board. Prices are paid in items (souls and floor resources), shown as their icons. */
	public static class Shop {
		/** Offers on the board per floor (rotated from the pool by the run seed and act). */
		public int slots = 6;
		public List<Offer> pool = new ArrayList<>(List.of(
				Offer.of(1, 99, 3).product("blocksouls_guns:primary_ammo", 32).price("blocksouls_world:scrap", 3),
				Offer.of(1, 99, 3).product("blocksouls_guns:primary_ammo", 48).price("blocksouls_game:soul", 3),
				Offer.of(1, 99, 2).product("blocksouls_guns:special_ammo", 8).price("blocksouls_world:gunmetal_ingot", 1)
						.price("blocksouls_game:soul", 2),
				Offer.of(1, 99, 2).product("minecraft:cobblestone_wall", 16).price("blocksouls_world:scrap", 4),
				Offer.of(1, 99, 1).product("minecraft:oak_fence", 16).price("blocksouls_world:scrap", 2)
						.price("minecraft:oak_log", 2),
				Offer.loot("blocksouls_game:gun_pity", 1, 2, 2).price("blocksouls_game:soul", 8),
				Offer.loot("blocksouls_game:gun_pity", 3, 99, 2).rare().price("blocksouls_world:gunmetal_ingot", 2).price("blocksouls_world:scrap", 4)
						.price("blocksouls_game:soul", 6),
				Offer.of(2, 99, 1).product("blocksouls_game:trinket_case", 1).rare().price("blocksouls_world:amber_resin", 2)
						.price("blocksouls_world:gunmetal_ingot", 2).price("blocksouls_game:soul", 6),
				Offer.of(1, 99, 2).product("minecraft:golden_apple", 1).price("blocksouls_world:amber_resin", 1)
						.price("blocksouls_world:scrap", 3)));
		/**
		 * The icon a price shows: where that resource comes from (a node, trees, mobs, the floor's rare), so the
		 * board reads as "go get this there". Items without an entry show themselves.
		 */
		public Map<String, Source> sources = new LinkedHashMap<>(Map.of(
				"blocksouls_world:scrap", new Source("blocksouls_world:scrap_node", "Scrap nodes"),
				"blocksouls_world:gunmetal_ingot", new Source("blocksouls_world:gunmetal_node", "Gunmetal nodes"),
				"blocksouls_world:amber_resin", new Source("blocksouls_world:resin_node", "The floor's rare (resin nodes)"),
				"blocksouls_game:soul", new Source("minecraft:zombie_head", "Mobs"),
				"minecraft:oak_log", new Source("minecraft:oak_sapling", "Trees")));
		/** Fixed services on the board. */
		public int perkRerollSouls = 6;
		public int cursePurgeSouls = 10;
		/** Floors runs: crafting tables, the 2×2 grid, crafters and smithing tables don't craft BlockSouls recipes (the forge and brew boards do). */
		public boolean craftingOffInFloors = true;
		/** Forge upgrade (two of a piece → the next tier): souls, plus this many of the floor's enchant metal. */
		public int upgradeSouls = 12;
		public int upgradeMetalCount = 2;
		/** Seconds between the shopkeeper's remarks about the threat. */
		public int remarkCooldownSeconds = 20;
	}

	/** One trade-board offer: a product (an item stack or a loot table roll) for 1–3 price items. */
	public static class Offer {
		/** Item product; ignored when {@link #loot} is set. */
		public String item = "minecraft:air";
		public int count = 1;
		/** Loot-table product (e.g. a rolled gun); the icon is a chest. */
		public String loot = "";
		public int minAct = 1;
		public int maxAct = 99;
		public int weight = 1;
		public List<Price> prices = new ArrayList<>();
		/** A rare offer must cost two or more resource kinds besides souls (checked at load; see {@link #valid}). */
		public boolean rare;

		public Offer() {
		}

		static Offer of(int minAct, int maxAct, int weight) {
			Offer offer = new Offer();
			offer.minAct = minAct;
			offer.maxAct = maxAct;
			offer.weight = weight;
			return offer;
		}

		static Offer loot(String table, int minAct, int maxAct, int weight) {
			Offer offer = of(minAct, maxAct, weight);
			offer.loot = table;
			return offer;
		}

		Offer product(String id, int productCount) {
			item = id;
			count = productCount;
			return this;
		}

		Offer price(String id, int amount) {
			prices.add(new Price(id, amount));
			return this;
		}

		Offer rare() {
			rare = true;
			return this;
		}

		/** Distinct price items other than souls. */
		public long resourceKinds() {
			return prices.stream().filter(p -> p.item != null && !p.item.equals("blocksouls_game:soul")).map(p -> p.item).distinct().count();
		}

		/** Has a product and 1+ prices; a rare offer needs two resource kinds besides souls. */
		public boolean valid() {
			boolean product = (loot != null && !loot.isBlank()) || (item != null && !item.equals("minecraft:air") && count > 0);
			return product && !prices.isEmpty() && prices.stream().allMatch(p -> p.item != null && p.count > 0) && (!rare || resourceKinds() >= 2);
		}
	}

	public static class Source {
		public String icon;
		public String label;

		public Source() {
		}

		public Source(String icon, String label) {
			this.icon = icon;
			this.label = label;
		}
	}

	public static class Price {
		public String item;
		public int count;

		public Price() {
		}

		public Price(String item, int count) {
			this.item = item;
			this.count = count;
		}
	}

	/** Where the camp objects stand (without a floor): offsets from the camp centre. */
	public static class Camp {
		/** Place a camp at run start in act 1, and on entering each later act without a floor. */
		public boolean placeWithoutFloor = true;
		public int hornOffsetX = 3;
		public int hornOffsetZ = 0;
		public int shopkeeperOffsetX = -3;
		public int shopkeeperOffsetZ = 0;
		/** A siege gathers here: the radius the volatile core is delivered into and the Director's "camp". */
		public double radius = 12;
	}
}
