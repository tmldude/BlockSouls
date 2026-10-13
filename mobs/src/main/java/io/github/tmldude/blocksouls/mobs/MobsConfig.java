package io.github.tmldude.blocksouls.mobs;

/**
 * {@code config/blocksouls/mobs.json}. Stats are vanilla-tier, before the game's wave scaling. Speeds are the
 * movement-speed attribute, times are seconds unless named {@code ...Ticks}, distances are blocks.
 */
public class MobsConfig {
	/** Defaults revision {@link #migrate} brings older files up to. */
	public static final int CURRENT_DEFAULTS = 1;

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
	public Breaking breaking = new Breaking();
	public Dread dread = new Dread();

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
		config.defaultsVersion = CURRENT_DEFAULTS;
		return true;
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
}
