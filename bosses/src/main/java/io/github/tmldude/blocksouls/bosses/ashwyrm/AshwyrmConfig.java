package io.github.tmldude.blocksouls.bosses.ashwyrm;

import io.github.tmldude.blocksouls.bosses.api.AttackStats;
import io.github.tmldude.blocksouls.bosses.field.FieldBossSettings;

/** {@code config/blocksouls/boss-ashwyrm.json}. Ticks are 1/20 s; damage is at normal difficulty, phase 1. */
public class AshwyrmConfig {
	public double health = 600;
	public double armor = 8;
	public double speed = 0.28;
	public int arenaRadius = 28;
	/** Experience dropped on death. */
	public int xp = 500;
	/** Hits on the head deal this much more. */
	public double headDamageMult = 2.0;
	/** Hits while it lies stunned after a landing (phase 2) deal this much more (stacks with the head). */
	public double vulnerableDamageMult = 2.0;
	/** Fireballs and meteors set real fire blocks where they land (and those can spread). Off: fire only hurts players. */
	public boolean griefing = false;

	// --- Phase 1 (ground) ---

	/** Bite: a quick lunge with the jaws, straight ahead. Short windup, so it hits light. */
	public AttackStats bite = new AttackStats(12, 4, 16, 40, 0, 7.5, 3, 5);
	public double biteReach = 6.5;
	public double biteHalfWidth = 1.5;

	/** Tail sweep: the whole body spins and the tail clears a full circle. Low: jump it or roll through. */
	public AttackStats tailSweep = new AttackStats(20, 6, 22, 100, 0, 7, 2, 8);
	public double tailRadius = 7;
	public double tailHeight = 1.0;

	/** Fire breath: a cone for 3 s that slowly follows its target. Damage per hit, every {@code breathHitIntervalTicks}. */
	public AttackStats breath = new AttackStats(24, 60, 24, 180, 0, 12, 2, 3);
	public double breathLength = 10;
	public double breathAngle = 50;
	public int breathHitIntervalTicks = 10;
	public int breathFireTicks = 60;
	/** Degrees per tick the breath turns toward its target. */
	public double breathTurnRate = 1.2;

	/** Wing buffet: wings come down hard; a ring that throws everyone near back. */
	public AttackStats wingBuffet = new AttackStats(18, 4, 20, 140, 0, 6.5, 1.5, 4);
	public double buffetRadius = 8;
	public double buffetKnockback = 1.6;

	// --- Phase 2 (air) ---

	/** Fireball run: telegraphs a line on the ground, flies along it and drops fireballs on it. Damage per fireball. */
	public AttackStats fireballRun = new AttackStats(30, 34, 40, 60, 0, 64, 3, 8);
	public int fireballs = 3;
	/** Blocks between impact points along the line. */
	public double fireballSpacing = 6;
	public double fireballRadius = 2.5;
	/** Ticks a fireball takes from the mouth to the ground. */
	public int fireballFlightTicks = 12;
	/** Burning ground left at each impact: lasts this long and hurts {@code fireDamage} every {@code fireHitIntervalTicks}. */
	public int fireTicks = 50;
	public double fireDamage = 2;
	public int fireHitIntervalTicks = 10;
	public int fireballFireTicks = 80;

	/**
	 * Landing (phase 2): after {@code landIntervalTicks} in the air it comes down (shockwave on impact), then lies
	 * stunned for the recovery (6 s), taking {@code vulnerableDamageMult}. Damage is the landing shockwave.
	 */
	public AttackStats land = new AttackStats(40, 4, 120, 0, 0, 64, 1000, 6);
	public int landIntervalTicks = 400;
	public double landShockwaveRadius = 5;

	/** Flight between attacks: circles its home at this radius (capped to the arena) and height above the ground. */
	public double circleRadius = 16;
	public double flightHeight = 12;
	/** Blocks per tick in flight. */
	public double flySpeed = 0.55;
	/** Radians per tick around the circle. */
	public double circleSpeed = 0.025;

	// --- Phase 3 (ground, enraged) ---

	/** Firestorm: a meteor on every player (plus strays) after a 1.5 s telegraph circle. */
	public AttackStats firestorm = new AttackStats(30, 8, 24, 200, 0, 40, 2.5, 8);
	public double meteorRadius = 2.5;
	public int meteorStrays = 2;
	public double meteorScatter = 6;
	public int meteorFireTicks = 60;

	// --- Phases ---

	public double phase2Threshold = 0.6;
	public double phase3Threshold = 0.25;
	public double phase3Damage = 1.2;
	public double phase3Speed = 1.3;
	public double phase3Cooldowns = 0.6;
	public double phase3Recovery = 0.7;

	/** On the ground, a target this far away that it can't walk to makes it hop toward them with its wings. */
	public double hopMinDistance = 10;
	public int hopCooldownTicks = 60;

	// --- Field spawn ---

	/** Lands on a mountain peak or high hill near a player; leaves (flies off) after 10 minutes unengaged. */
	public FieldBossSettings field = new FieldBossSettings(0.3, 20 * 60 * 10, 20 * 60 * 10);
	/** Candidate columns sampled around a player per check; the highest open one wins. */
	public int spawnSamples = 32;
	public double spawnMinDistance = 48;
	public double spawnMaxDistance = 128;
	/** Ground at or above this Y counts as high terrain in any biome (mountain/hill biomes always count). */
	public int highGroundY = 110;
	/** Arrival: starts this high above its spot and glides down over {@code arrivalTicks}. */
	public double arrivalHeight = 30;
	public int arrivalTicks = 80;
	/** Leaving: flies up and away for this long, then is gone. */
	public int leaveFlightTicks = 100;
}
