package io.github.tmldude.blocksouls.bosses.stormplate;

import io.github.tmldude.blocksouls.bosses.api.AttackStats;
import io.github.tmldude.blocksouls.bosses.field.FieldBossSettings;

/** {@code config/blocksouls/boss-stormplate_knight.json}. Ticks are 1/20 s; damage is at normal difficulty, phase 1. */
public class StormplateConfig {
	public double health = 500;
	public double armor = 12;
	public double speed = 0.27;
	/** Fog wall radius; a little outside the ruin so the broken pillars stand inside the fight. */
	public int arenaRadius = 16;

	/** Spear thrust: a long, narrow line straight ahead. */
	public AttackStats thrust = new AttackStats(15, 3, 18, 50, 0, 6.5, 3, 12);
	public double thrustLength = 7;
	public double thrustHalfWidth = 0.8;

	/** Spear sweep: a wide low arc around the knight. */
	public AttackStats sweep = new AttackStats(18, 3, 20, 70, 0, 5, 3, 10);
	public double sweepRadius = 5.5;
	public double sweepAngle = 220;
	/** Height of the swept band; a jumping player clears it. */
	public double sweepHeight = 1.0;

	/** Thunder call: circles under every player, then lightning. Lightning damage, no fire, no block damage. */
	public AttackStats thunder = new AttackStats(30, 6, 24, 140, 0, 32, 2.5, 10);
	public double thunderRadius = 2.0;
	/** Phase 2: extra circles per player (placed where they're heading). */
	public int thunderExtraPerPlayerPhase2 = 1;

	/** Spear throw: thrown along a telegraphed line, then recalled; it hits on the way out and on the way back. */
	public AttackStats spearThrow = new AttackStats(20, 22, 18, 120, 5, 20, 2, 10);
	public double throwLength = 16;
	public double throwHalfWidth = 0.9;
	/** Active ticks spent flying out (the same back); the rest it hangs crackling at the far end. */
	public int throwFlightTicks = 9;

	/** Leap slam (phase 2): jumps to a player, shockwave on landing. */
	public AttackStats leap = new AttackStats(22, 14, 26, 160, 4, 18, 2.5, 14);
	public double leapRadius = 4.0;
	public double leapHeight = 6.0;
	public double leapKnockback = 1.0;

	/** Charged puddles (phase 2): left where thunder strikes; pulse damage every {@code puddlePulseTicks}. */
	public double puddleRadius = 2.0;
	public int puddleLifeTicks = 20 * 14;
	public int puddlePulseTicks = 50;
	/** The puddle shows it's about to pulse this long before (brighter ring filling in). */
	public int puddleWarnTicks = 15;
	public double puddleDamage = 4;
	public int puddleMax = 8;
	/** Puddles scattered around the arena when phase 2 starts. */
	public int puddlesOnPhase2 = 4;

	/** Phase 2 starts below this share of health: the storm intensifies. */
	public double phase2Threshold = 0.5;
	public double phase2Damage = 1.2;
	public double phase2Recovery = 0.7;
	public double phase2Cooldowns = 0.7;
	public double phase2Speed = 1.2;

	/** The ruin it brings: ring radius of broken pillars and cracked floor. */
	public int ruinRadius = 14;
	public int ruinPillars = 9;
	/** Share of floor columns turned to cracked stone bricks. */
	public double ruinFloorShare = 0.55;
	/** Columns more than this many blocks above/below the center's floor are left alone. */
	public int ruinMaxStep = 3;
	/** Ambient lightning flickers over the ruin: average ticks between strikes (phase 2 halves it). */
	public int flickerIntervalTicks = 20 * 7;

	/** Field spawn: a chance per in-game day near a player, on flat open ground. */
	public FieldBossSettings field = new FieldBossSettings(0.3, 20 * 60 * 10, 20 * 60 * 10);
	public double spawnMinDistance = 36;
	public double spawnMaxDistance = 60;
	/** Max height difference across the ruin's footprint for a spot to count as flat. */
	public int spawnMaxSlope = 3;

	/** Dragonscale set bonus: knockback resistance added (0.1 shows as "+1"). */
	public double setKnockbackResistance = 0.2;
	/** Dragonscale set bonus: kills call lightning on the next enemy within this range. */
	public double setChainRange = 8;
	public double setChainDamage = 8;
	public int setChainCooldownTicks = 100;
}
