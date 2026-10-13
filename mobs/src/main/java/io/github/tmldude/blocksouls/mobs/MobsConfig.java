package io.github.tmldude.blocksouls.mobs;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@code config/blocksouls/mobs.json}. Stats are vanilla-tier, before the game's wave scaling. Speeds are the
 * movement-speed attribute, times are seconds unless named {@code ...Ticks}, distances are blocks.
 */
public class MobsConfig {
	/** Defaults revision {@link #migrate} brings older files up to. */
	public static final int CURRENT_DEFAULTS = 3; // 2: floor 3, Skyrift; 3: floor 2, Infernal

	/** Which defaults revision this file was written against (0 = before the field existed). */
	public int defaultsVersion;

	public Shambler shambler = new Shambler();
	public Crawler crawler = new Crawler();
	public Screamer screamer = new Screamer();
	public Bloater bloater = new Bloater();
	public Brute brute = new Brute();
	public MutantBrute mutantBrute = new MutantBrute();
	public InfectedPiglin infectedPiglin = new InfectedPiglin();
	public AshHound ashHound = new AshHound();
	public Spitter spitter = new Spitter();
	public Bulwark bulwark = new Bulwark();
	public Breaking breaking = new Breaking();
	public Dread dread = new Dread();

	/** Elite affixes appear only in Expedition (on a floor or in a multi-act run) unless this is true. */
	public boolean affixesInClassic = false;
	public AffixConfig affixes = new AffixConfig();
	/** Floor roster (Expedition): mob id → first floor and required floor tags. Mobs not listed are on every floor. */
	public Map<String, RosterEntry> roster = defaultRoster();
	public Flanking flanking = new Flanking();
	public AttackPicking attackPicking = new AttackPicking();

	/**
	 * Brings a mobs.json written against older defaults up to date: values still at an old default take the new one,
	 * anything changed by hand is kept. Returns whether anything changed (so the caller saves the file).
	 */
	public static boolean migrate(MobsConfig config) {
		if (config.defaultsVersion >= CURRENT_DEFAULTS) {
			return false;
		}
		// 1: drops-and-pacing (v0.11): the first elite (Mutant Brute) a wave earlier.
		if (config.defaultsVersion < 1 && config.mutantBrute != null && config.mutantBrute.minWave == 7) {
			config.mutantBrute.minWave = 6;
		}
		// --- floor 3, Skyrift (Stage 6C) ---
		// 2: floor sessions' affix mixes join an existing file (a tag already there is kept as tuned).
		if (config.defaultsVersion < 2 && config.affixes != null && config.affixes.weightsByFloorTag != null) {
			defaultFloorWeights().forEach(config.affixes.weightsByFloorTag::putIfAbsent);
		}
		// --- end Skyrift block
		// 3 (v2-floor2-infernal): the Infernal's affix mix joins a file Skyrift's step 2 already brought to 2.
		if (config.defaultsVersion < 3 && config.affixes != null && config.affixes.weightsByFloorTag != null) {
			config.affixes.weightsByFloorTag.putIfAbsent("infernal", defaultFloorWeights().get("infernal"));
		}
		// end v2-floor2-infernal
		config.defaultsVersion = CURRENT_DEFAULTS;
		return true;
	}

	/** Surface floors 1–6: the basics from floor 1, heavier and ranged mobs join as the act goes on. */
	static Map<String, RosterEntry> defaultRoster() {
		Map<String, RosterEntry> roster = new LinkedHashMap<>();
		roster.put("blocksouls_mobs:shambler", new RosterEntry(1));
		roster.put("blocksouls_mobs:crawler", new RosterEntry(1));
		roster.put("blocksouls_mobs:screamer", new RosterEntry(1));
		roster.put("blocksouls_mobs:spitter", new RosterEntry(2));
		roster.put("blocksouls_mobs:bloater", new RosterEntry(2));
		roster.put("blocksouls_mobs:brute", new RosterEntry(2));
		roster.put("blocksouls_mobs:bulwark", new RosterEntry(3));
		roster.put("blocksouls_mobs:mutant_brute", new RosterEntry(3));
		return roster;
	}

	/** Shared by every mob. {@code minWave}/{@code weight} only matter for wave mobs. */
	public static class Stats {
		public double health;
		public double damage;
		public double speed;
		public double armor;
		public int minWave;
		public int weight;

		Stats(double health, double damage, double speed, double armor, int minWave, int weight) {
			this.health = health;
			this.damage = damage;
			this.speed = speed;
			this.armor = armor;
			this.minWave = minWave;
			this.weight = weight;
		}
	}

	public static class Shambler extends Stats {
		Shambler() {
			super(20, 3, 0.21, 2, 1, 10);
		}
	}

	public static class Crawler extends Stats {
		/** Leaps at targets closer than this. */
		public double leapRange = 4;
		public double leapUp = 0.4;
		public double leapForward = 0.9;
		public int leapCooldownTicks = 40;

		Crawler() {
			super(12, 2, 0.33, 0, 2, 5);
		}
	}

	public static class Screamer extends Stats {
		public double screamRadius = 24;
		public int speedSeconds = 8;
		public int cooldownSeconds = 15;
		/** Keeps at least this far from its target. */
		public double keepAway = 8;

		Screamer() {
			super(14, 2, 0.25, 0, 3, 2);
		}
	}

	public static class Bloater extends Stats {
		/** Starts swelling when its target is this close. */
		public double triggerRange = 2.5;
		/** Telegraph length (1.5 s). */
		public int swellTicks = 30;
		public double burstRadius = 3;
		public double burstDamage = 6;
		public float cloudRadius = 2.5f;
		public int cloudSeconds = 6;
		public int poisonSeconds = 5;

		Bloater() {
			super(30, 0, 0.18, 0, 4, 2);
		}
	}

	public static class Brute extends Stats {
		public int slamWindupTicks = 20;
		public double slamRadius = 4;
		public double slamKnockback = 1.2;
		public int slamCooldownTicks = 100;
		/** Slams when the target is within this range. */
		public double slamRange = 3.5;
		/** Block-breaking speed multiplier (2× = half the time). */
		public double breakSpeed = 2;

		Brute() {
			super(60, 8, 0.2, 6, 5, 2);
		}
	}

	public static class MutantBrute extends Stats {
		public int leapCooldownTicks = 160;
		/** Leaps when the target is between these distances. */
		public double leapMinRange = 6;
		public double leapMaxRange = 16;
		/** How long the landing ring shows before the leap. */
		public int leapWindupTicks = 25;
		public double slamRadius = 4;
		public double slamKnockback = 1.5;
		public int debrisCooldownTicks = 70;
		public double debrisMinRange = 5;
		public double debrisDamage = 6;
		public double breakSpeed = 2;

		MutantBrute() {
			super(150, 12, 0.24, 8, 6, 1);
		}
	}

	public static class InfectedPiglin extends Stats {
		/** Share of each Nether biome's piglin / zombified piglin spawn weight handed to infected piglins. */
		public double replaceShare = 0.3;
		/** Spawn weight in Nether biomes that have no piglins at all. */
		public int baseWeight = 10;

		InfectedPiglin() {
			super(24, 4, 0.3, 2, 0, 0);
		}
	}

	public static class AshHound extends Stats {
		AshHound() {
			super(16, 4, 0.38, 0, 0, 0);
		}
	}

	public static class Breaking {
		/** The mob tries to break through after being stuck (path blocked, not moving) this long. */
		public int stuckTicks = 40;
		/** Time to break one block at wave 1 and at {@link #fastestWave}+, linear in between. */
		public double slowestSeconds = 8;
		public double fastestSeconds = 3;
		public int fastestWave = 10;
		/** Only blocks this close to the mob (horizontally) count as "in its way". */
		public double reach = 1.5;
	}

	/** Hidden Nether pressure, per player. Values are dread points; it rises 1/s in the Nether. */
	public static class Dread {
		public boolean enabled = true;
		public double risePerSecond = 1;
		public double decayPerSecond = 2;
		/** Distant howls from here on. */
		public double howlAt = 120;
		/** The first Ash Hound pack. */
		public double firstPackAt = 240;
		/** Seconds in the Nether between later packs. */
		public double packEverySeconds = 120;
		public int packMin = 3;
		public int packMax = 5;
		/** Each later pack in the same visit has this many more hounds, capped at {@link #packCap}. */
		public int packGrowth = 1;
		public int packCap = 9;
		public double spawnMinDistance = 20;
		public double spawnMaxDistance = 30;
		/** Average seconds between howls once past {@link #howlAt}. */
		public double howlEverySeconds = 25;
	}

	public static class Spitter extends Stats {
		/** Spits at targets within this range it can see. */
		public double spitRange = 16;
		/** Keeps at least this far from its target. */
		public double keepAway = 9;
		/** The throat swells (and the mob stops) this long before the glob flies. */
		public int spitWindupTicks = 15;
		public int spitCooldownTicks = 50;
		public double spitDamage = 4;
		public float spitSpeed = 1.1f;
		/** Poison on a hit, seconds (0 = none). */
		public int poisonSeconds = 3;

		Spitter() {
			super(16, 2, 0.24, 0, 3, 3);
		}
	}

	/** The shielded mob: a slab of scrap in front blocks most damage, so the party has to get round it. */
	public static class Bulwark extends Stats {
		/** Full width of the shielded front arc, degrees. */
		public double frontArcDegrees = 120;
		/** Share of damage that gets through the shield from the front. */
		public double frontDamageFactor = 0.15;
		/** How fast it can turn its shield toward the target, degrees per tick (slow, so flanking works). */
		public float turnDegreesPerTick = 6;
		/** Shield bash: windup, reach, knockback, cooldown. */
		public int bashWindupTicks = 18;
		public double bashRange = 3;
		public double bashKnockback = 1.4;
		public int bashCooldownTicks = 60;

		Bulwark() {
			super(40, 6, 0.19, 4, 4, 2);
		}
	}

	public static class RosterEntry {
		public int minFloor = 1;
		/** Floor tags the floor must carry (all of them), e.g. a template pool tag. */
		public List<String> tags = new ArrayList<>();

		RosterEntry() {
		}

		RosterEntry(int minFloor) {
			this.minFloor = minFloor;
		}
	}

	static Map<String, Map<String, Integer>> defaultFloorWeights() {
		Map<String, Map<String, Integer>> byTag = new LinkedHashMap<>();
		// --- floor 4, The Vault (Stage 6C): volatile is the common affix in the tight corridors ---
		byTag.put("vault", new LinkedHashMap<>(Map.of("volatile", 5)));
		// --- floor 3, Skyrift (Stage 6C): frosted is the common affix up in the cold air ---
		byTag.put("skyrift", new LinkedHashMap<>(Map.of("frosted", 5)));
		// --- floor 2, Infernal (v2-floor2-infernal): burning is the common affix over the lava ---
		byTag.put("infernal", new LinkedHashMap<>(Map.of("burning", 5)));
		return byTag;
	}

	/** Elite affixes (PLAN2 §3h). Rolled for elites; Classic only with {@link #affixesInClassic}. */
	public static class AffixConfig {
		/** No affixes before this floor (the first floor stays plain). */
		public int minFloor = 2;
		/** Chance an elite gets an affix on a floor at or past {@link #minFloor}. */
		public double chance = 0.5;
		/** Chance on floors with the {@code elite_affixes} escalation tag. */
		public double escalatedChance = 1.0;
		/** Chance in Classic, when {@code affixesInClassic} is on. */
		public double classicChance = 0.35;
		/** Relative weight of each affix (by name); 0 or missing = never rolled. */
		public Map<String, Integer> weights = new LinkedHashMap<>(Map.of("burning", 1, "frosted", 1, "volatile", 1, "magnetic", 1));
		/**
		 * A floor's own affix mix, by floor tag: on a floor carrying the tag these weights replace {@link #weights} for
		 * the affixes they name (first matching tag wins). Floor sessions add one line each.
		 */
		public Map<String, Map<String, Integer>> weightsByFloorTag = defaultFloorWeights();
		/** Affixed mobs show their name ("Burning Brute") above them. */
		public boolean showName = true;
		/** Extra max health for an affixed mob (multiplier, 1 = none). */
		public double healthMultiplier = 1.25;

		public Burning burning = new Burning();
		public Frosted frosted = new Frosted();
		public Volatile volatileAffix = new Volatile();
		public Magnetic magnetic = new Magnetic();

		public static class Burning {
			/** Leaves a burning spot where it walks (flames only, no fire blocks), every this many ticks. */
			public int trailEveryTicks = 6;
			/** Ticks a trail spot keeps burning; anyone standing in it catches fire. */
			public int trailLifeTicks = 60;
			/** Seconds a hit sets its target on fire. */
			public int igniteSeconds = 4;
		}

		public static class Frosted {
			/** Slowness on a hit: seconds and amplifier (0 = Slowness I). */
			public int slowSeconds = 3;
			public int slowAmplifier = 1;
			/** Share of projectile damage it takes. */
			public double projectileDamageFactor = 0.6;
			/** Damage types that count as projectiles besides {@code #minecraft:is_projectile} (hitscan bullets). */
			public List<String> projectileDamageTypes = new ArrayList<>(List.of("blocksouls_guns:bullet"));
		}

		public static class Volatile {
			/** The fuse after death: a swelling ring warns, then it blows. */
			public int fuseTicks = 20;
			public double radius = 3.5;
			/** Damage at the centre; falls off linearly to half at the edge. */
			public double damage = 10;
			public double knockback = 0.8;
		}

		public static class Magnetic {
			public double radius = 6;
			/** Pull per tick on dropped items toward it (blocks/tick at the edge). */
			public double itemPull = 0.06;
			/** Sideways push per tick on players' projectiles flying past (arrows, thrown things). */
			public double projectileDeflect = 0.05;
		}
	}

	/** Flanking (see {@code FlankGoal}): Director flankers and these types circle to the side the party isn't facing. */
	public static class Flanking {
		public boolean enabled = true;
		/** Mob ids that always flank, Director or not. */
		public List<String> alwaysFlank = new ArrayList<>(List.of("blocksouls_mobs:crawler"));
		/** Circles at this distance from the target until it's behind them. */
		public double orbitRadius = 5;
		/** "Behind" = at least this many degrees from where the target faces. */
		public double behindDegrees = 110;
		/** Gives up flanking and goes straight in after this long. */
		public int giveUpTicks = 160;
		/** Only flanks targets further than this (closer, it just attacks). */
		public double minDistance = 3;
		public double speed = 1.15;
	}

	/**
	 * Utility scoring for the Brute, Mutant Brute and Screamer's attacks (see {@code AttackPicker}). Each ready attack
	 * scores from these weights; the best score above {@link #threshold} is used.
	 */
	public static class AttackPicking {
		public double threshold = 0.35;
		/** How much being in the attack's ideal range counts. */
		public double distanceWeight = 1.0;
		/** How much the target being in front counts (vs. needing to turn). */
		public double angleWeight = 0.4;
		/** Bonus for punishing a target that just dodge-rolled (within {@link #rollWindowTicks}). */
		public double justRolledBonus = 0.5;
		public int rollWindowTicks = 20;
		/** Bonus for ranged attacks / penalty for ground attacks when the target is in the air. */
		public double airborneWeight = 0.4;
		/** Score taken off an attack used last time (keeps the moveset varied). */
		public double repeatPenalty = 0.3;
		/** Screamer: score per wave mob within its scream radius, capped at 1. */
		public double screamPerAlly = 0.25;
	}
}
