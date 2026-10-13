package io.github.tmldude.blocksouls.bosses.furnace;

import io.github.tmldude.blocksouls.bosses.api.AttackStats;
import io.github.tmldude.blocksouls.bosses.archetype.ArchetypeParams;
import io.github.tmldude.blocksouls.bosses.field.FieldBossSettings;

/** {@code config/blocksouls/boss-furnace_knight.json}. Ticks are 1/20 s; damage is at normal difficulty, phase 1. */
public class FurnaceKnightConfig {
	public double health = 400;
	public double armor = 10;
	public double speed = 0.26;
	public int arenaRadius = 18;
	/** Hits on the glowing helm (the weak point) deal this much more. */
	public double helmDamageMult = 1.5;

	/** Overhead slam: a fire shockwave along a line. */
	public AttackStats slam = new AttackStats(20, 4, 24, 80, 0, 7, 3, 14);
	public double slamLength = 8;
	public double slamHalfWidth = 1.0;

	/** Sweeping cut: a low 270° arc; jump it or roll through. */
	public AttackStats sweep = new AttackStats(16, 3, 20, 60, 0, 4.5, 3, 10);
	public double sweepRadius = 4.5;
	public double sweepAngle = 270;
	/** Height of the swept band above the knight's feet; a jumping player clears it. */
	public double sweepHeight = 0.9;

	/** Furnace breath: the visor opens and a cone of fire pours out. Damage is per tick of contact (every 10 ticks). */
	public AttackStats breath = new AttackStats(24, 40, 20, 140, 0, 5.5, 2, 3);
	public double breathLength = 5;
	public double breathAngle = 50;
	public int breathHitIntervalTicks = 10;
	public int breathFireTicks = 80;
	/** How fast the breath follows its target, degrees per tick. */
	public double breathTurnRate = 2.5;

	/** Charge: lowers its shoulder and dashes in a line. */
	public AttackStats charge = new AttackStats(20, 12, 28, 120, 6, 16, 2, 12);
	public double chargeDistance = 12;
	public double chargeKnockback = 1.2;

	/** Fire pillars (phase 2): circles under players erupt. */
	public AttackStats pillars = new AttackStats(30, 10, 16, 160, 0, 24, 2.5, 10);
	public int pillarsMin = 3;
	public int pillarsMax = 5;
	public double pillarRadius = 1.5;
	public double pillarHeight = 4;
	/** Extra pillars land within this distance of a player. */
	public double pillarScatter = 5;
	public int pillarFireTicks = 60;

	/** Extra recovery when the charge slams into terrain: the knight is stuck for 2 s. */
	public int chargeStuckTicks = 40;

	/** v2 archetypes. Shockwave: two fire rings roll out from a stomp; jump them. */
	public ArchetypeParams.Ring ring = new ArchetypeParams.Ring(new AttackStats(20, 60, 20, 140, 0, 8, 2, 9)).rings(2, 14).reach(13, 0.45);
	/** Slag pools (phase 2): molten pools under each player; damage per half-second of contact. */
	public ArchetypeParams.Pools pools = new ArchetypeParams.Pools(new AttackStats(24, 100, 12, 220, 0, 20, 2, 3)).pools(4, 2.0);
	/** Leap: jumps onto a player; the shadow shows where. */
	public ArchetypeParams.Leap leap = new ArchetypeParams.Leap(new AttackStats(30, 3, 26, 180, 6, 16, 1.5, 13)).leap(14, 3.5);
	/** Feint (phase 2): starts the overhead slam, then sweeps instead. */
	public ArchetypeParams.Feint feint = new ArchetypeParams.Feint(new AttackStats(10, 1, 0, 260, 0, 4.5, 1.5, 0));

	/** Phase 2 starts below this share of health: the blade ignites. */
	public double phase2Threshold = 0.5;
	public double phase2Damage = 1.25;
	public double phase2Recovery = 0.7;
	public double phase2Cooldowns = 0.7;
	public double phase2Speed = 1.15;

	/** Field spawn: guards the first bastion a player comes near. */
	public FieldBossSettings field = new FieldBossSettings(0, 20 * 60 * 5, 20 * 60 * 10);
	/** A player this close to a bastion's center wakes the guard. */
	public double bastionTriggerRange = 64;
}
