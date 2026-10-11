package io.github.tmldude.blocksouls.bosses.field;

/** A field boss's spawn numbers. Embed one in the boss's config class so they're tunable. */
public class FieldBossSettings {
	public boolean enabled = true;
	/** Chance that a {@code DAILY} rule's boss shows up on a given in-game day (ignored by {@code WATCH} rules). */
	public double chancePerDay = 0.35;
	/** After it leaves (or after a fight resets and it leaves), wait this long before it can appear again. */
	public int cooldownTicks = 20 * 60 * 10;
	/** Leave after this long with nobody within {@code leaveCheckRange} and no fight. Default 10 minutes. */
	public int leaveAfterTicks = 20 * 60 * 10;

	public FieldBossSettings() {
	}

	public FieldBossSettings(double chancePerDay, int cooldownTicks, int leaveAfterTicks) {
		this.chancePerDay = chancePerDay;
		this.cooldownTicks = cooldownTicks;
		this.leaveAfterTicks = leaveAfterTicks;
	}
}
