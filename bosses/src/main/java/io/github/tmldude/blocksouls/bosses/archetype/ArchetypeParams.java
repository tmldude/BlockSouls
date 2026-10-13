package io.github.tmldude.blocksouls.bosses.archetype;

import io.github.tmldude.blocksouls.bosses.api.AttackStats;

/**
 * Config blocks for the archetypes, one per shape. Bosses embed these in their own config file, so every number is
 * tunable per boss. Plain data (no game classes), so configs and the readability test load without a game.
 */
public final class ArchetypeParams {
	private ArchetypeParams() {
	}

	/** Config block for one boss's rings. */
	public static class Ring {
		public AttackStats stats = new AttackStats(20, 50, 20, 120, 0, 10, 2, 8);
		/** Rings in one attack, {@link #ringGapTicks} apart. */
		public int rings = 1;
		public int ringGapTicks = 12;
		/** Blocks per tick the ring front travels. */
		public double speed = 0.5;
		public double maxRadius = 14;
		/** Half-thickness of the ring front. */
		public double thickness = 0.7;
		/** Height of the wave above the floor: lower than a jump. */
		public double height = 0.8;

		public Ring() {
		}

		public Ring(AttackStats stats) {
			this.stats = stats;
		}

		public Ring rings(int rings, int gapTicks) {
			this.rings = rings;
			this.ringGapTicks = gapTicks;
			return this;
		}

		public Ring reach(double maxRadius, double speed) {
			this.maxRadius = maxRadius;
			this.speed = speed;
			return this;
		}
	}

	/** Config block: a ring plus the width of the safe lane. */
	public static class Burst extends Ring {
		public double gapDegrees = 50;

		public Burst() {
			this.height = 3;
			this.speed = 0.6;
		}

		public Burst(AttackStats stats) {
			this();
			this.stats = stats;
		}
	}

	/** Config block. Damage is per {@link #hitIntervalTicks} of contact; the active stage is how long pools linger. */
	public static class Pools {
		public AttackStats stats = new AttackStats(24, 100, 10, 200, 0, 20, 2, 3);
		/** Total pools (one under each player first, up to this many). */
		public int pools = 4;
		public double radius = 2.0;
		/** Stray pools land within this distance of a player. */
		public double scatter = 6;
		public int hitIntervalTicks = 10;

		public Pools() {
		}

		public Pools(AttackStats stats) {
			this.stats = stats;
		}

		public Pools pools(int pools, double radius) {
			this.pools = pools;
			this.radius = radius;
			return this;
		}
	}

	/** Config block. */
	public static class Cross {
		public AttackStats stats = new AttackStats(22, 4, 18, 120, 0, 20, 2, 10);
		/** 2 = one line through the point, 4 = a cross. */
		public int arms = 4;
		public double length = 10;
		public double halfWidth = 1.0;
		public double height = 2.5;

		public Cross() {
		}

		public Cross(AttackStats stats) {
			this.stats = stats;
		}

		public Cross arms(int arms, double length) {
			this.arms = arms;
			this.length = length;
			return this;
		}
	}

	/** Config block. Damage is per {@link #hitIntervalTicks} of contact; the sweep lasts the active stage. */
	public static class Beam {
		public AttackStats stats = new AttackStats(24, 60, 24, 200, 0, 12, 1.5, 5);
		public double length = 12;
		public double halfWidth = 0.7;
		public double height = 2.0;
		public double degreesPerTick = 4;
		public int hitIntervalTicks = 10;

		public Beam() {
		}

		public Beam(AttackStats stats) {
			this.stats = stats;
		}

		public Beam beam(double length, double degreesPerTick) {
			this.length = length;
			this.degreesPerTick = degreesPerTick;
			return this;
		}
	}

	/** Config block. The windup is the hover before the orbs move; the active stage is their lifetime. */
	public static class Orbs {
		public AttackStats stats = new AttackStats(20, 80, 16, 160, 0, 20, 2, 6);
		public int orbs = 3;
		/** Blocks per tick. */
		public double speed = 0.4;
		/** How far the orbs turn toward their target per tick, degrees. */
		public double turnDegreesPerTick = 6;
		/** Hit radius around an orb. */
		public double hitRadius = 0.8;
		/** Ring the orbs bloom on, around the boss's upper body. */
		public double spawnRadius = 1.8;

		public Orbs() {
		}

		public Orbs(AttackStats stats) {
			this.stats = stats;
		}

		public Orbs orbs(int orbs, double speed) {
			this.orbs = orbs;
			this.speed = speed;
			return this;
		}
	}

	/** Config block. Damage is per bolt. */
	public static class Spiral {
		public AttackStats stats = new AttackStats(24, 80, 20, 220, 0, 24, 2, 5);
		public int arms = 3;
		public double degreesPerTick = 3;
		/** A bolt leaves each arm this often. */
		public int emitEveryTicks = 3;
		/** Blocks per tick a bolt travels outward. */
		public double boltSpeed = 0.5;
		public double length = 16;
		public double boltRadius = 0.7;
		/** How far below the boss the floor is searched for. */
		public int floorSearch = 48;

		public Spiral() {
		}

		public Spiral(AttackStats stats) {
			this.stats = stats;
		}

		public Spiral arms(int arms, double degreesPerTick) {
			this.arms = arms;
			this.degreesPerTick = degreesPerTick;
			return this;
		}
	}

	/** Config block. Damage is unused. */
	public static class Summon {
		public AttackStats stats = new AttackStats(30, 1, 20, 900, 0, 30, 1.5, 0);
		public int adds = 3;
		/** Wave-mob tier the adds are drawn from. */
		public int tier = 1;
		/** Adds appear on a ring this far from the boss. */
		public double radius = 5;
		/** Failsafe: the shield drops after this long even with adds alive. */
		public int maxShieldTicks = 20 * 30;

		public Summon() {
		}

		public Summon(AttackStats stats) {
			this.stats = stats;
		}

		public Summon adds(int adds, int tier) {
			this.adds = adds;
			this.tier = tier;
			return this;
		}
	}

	/** Config block: the feint's own stats (its windup is how much of the bait plays; it never deals damage). */
	public static class Feint {
		public AttackStats stats = new AttackStats(10, 1, 0, 240, 0, 8, 1, 0);

		public Feint() {
		}

		public Feint(AttackStats stats) {
			this.stats = stats;
		}
	}

	/** Config block. */
	public static class Charge {
		public AttackStats stats = new AttackStats(20, 14, 20, 140, 6, 18, 1.5, 12);
		public double distance = 14;
		public double knockback = 1.2;
		/** Extra recovery after hitting terrain. */
		public int stuckTicks = 40;

		public Charge() {
		}

		public Charge(AttackStats stats) {
			this.stats = stats;
		}

		public Charge distance(double distance) {
			this.distance = distance;
			return this;
		}
	}

	/** Config block. The windup is crouch + flight; the shadow shows from the start. */
	public static class Leap {
		public AttackStats stats = new AttackStats(30, 3, 26, 160, 5, 16, 1.5, 12);
		/** Ticks of the windup spent crouching before take-off (the landing spot follows the target until then). */
		public int crouchTicks = 12;
		public double apexHeight = 6;
		public double maxDistance = 16;
		public double radius = 3.5;

		public Leap() {
		}

		public Leap(AttackStats stats) {
			this.stats = stats;
		}

		public Leap leap(double maxDistance, double radius) {
			this.maxDistance = maxDistance;
			this.radius = radius;
			return this;
		}
	}

	/** Config block. The windup is tracking + the eruption warning. */
	public static class Burrow {
		public AttackStats stats = new AttackStats(60, 4, 30, 260, 6, 24, 1.5, 14);
		/** Final ticks of the windup the plume stops and the eruption circle fills (never under the big-hit floor). */
		public int warnTicks = 18;
		/** Blocks per tick the plume moves under the floor. */
		public double tunnelSpeed = 0.35;
		public double radius = 3.5;
		public double launch = 0.9;

		public Burrow() {
		}

		public Burrow(AttackStats stats) {
			this.stats = stats;
		}
	}
}
