package io.github.tmldude.blocksouls.bosses.api;

/**
 * The tunable numbers of one attack. Plain fields so a boss's config class can embed one per attack and Gson
 * reads/writes it ({@code "slam": {"windupTicks": 20, ...}}).
 */
public class AttackStats {
	/** Telegraph length. Hits over 30% of a player's health need at least 15 (0.75 s). */
	public int windupTicks = 20;
	/** How long hitboxes are live. */
	public int activeTicks = 4;
	/** Punish window after the active part. */
	public int recoveryTicks = 20;
	/** Ticks before this attack can be picked again (counted from the end of recovery). */
	public int cooldownTicks = 60;
	/** Target distance band where the boss may pick this attack. */
	public double minRange = 0;
	public double maxRange = 5;
	/** Relative pick weight. */
	public double weight = 1;
	/** Damage of one hit at normal difficulty, before phase multipliers. */
	public double damage = 6;

	public AttackStats() {
	}

	public AttackStats(int windupTicks, int activeTicks, int recoveryTicks, int cooldownTicks, double minRange, double maxRange,
			double weight, double damage) {
		this.windupTicks = windupTicks;
		this.activeTicks = activeTicks;
		this.recoveryTicks = recoveryTicks;
		this.cooldownTicks = cooldownTicks;
		this.minRange = minRange;
		this.maxRange = maxRange;
		this.weight = weight;
		this.damage = damage;
	}
}
