package io.github.tmldude.blocksouls.bosses.blight;

import io.github.tmldude.blocksouls.bosses.api.AttackStats;
import io.github.tmldude.blocksouls.bosses.archetype.ArchetypeParams;

/**
 * {@code config/blocksouls/boss-blightheart.json}. Ticks are 1/20 s; damage is at normal difficulty, phase 1. The
 * Blighted Surface's signature boss: it roams the floor by day and is one of its mini-bosses.
 */
public class BlightheartConfig {
	public double health = 360;
	public double armor = 8;
	public double speed = 0.24;
	public int arenaRadius = 20;
	/** Hits on the swollen heart in its chest (the weak point) deal this much more. */
	public double heartDamageMult = 1.6;

	/** Rot wave: two rings of blight roll out from a stomp; jump them. */
	public ArchetypeParams.Ring ring = new ArchetypeParams.Ring(new AttackStats(20, 60, 20, 120, 0, 9, 2, 8)).rings(2, 14).reach(14, 0.45);
	/** Blight pools: festering pools under each player; damage per half-second of contact. */
	public ArchetypeParams.Pools pools = new ArchetypeParams.Pools(new AttackStats(24, 120, 12, 200, 0, 22, 2, 3)).pools(4, 2.2);
	/** Spores: slow homing motes that burst on contact; outrun or shoot them. */
	public ArchetypeParams.Orbs spores = new ArchetypeParams.Orbs(new AttackStats(20, 120, 16, 180, 6, 24, 1.5, 5)).orbs(3, 0.3);
	/** Leap: lands on a player; the shadow shows where. */
	public ArchetypeParams.Leap leap = new ArchetypeParams.Leap(new AttackStats(30, 3, 26, 180, 6, 16, 1.5, 12)).leap(14, 3.5);
	/** Heartburst (phase 2): a tall burst from the heart with one safe lane. */
	public ArchetypeParams.Burst burst = new ArchetypeParams.Burst(new AttackStats(26, 50, 24, 220, 0, 12, 1.5, 10));

	/** Phase 2 starts below this share of health: the heart splits open. */
	public double phase2Threshold = 0.5;
	public double phase2Damage = 1.25;
	public double phase2Recovery = 0.75;
	public double phase2Cooldowns = 0.7;
	public double phase2Speed = 1.15;
}
