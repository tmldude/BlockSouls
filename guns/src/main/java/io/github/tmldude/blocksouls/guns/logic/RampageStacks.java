package io.github.tmldude.blocksouls.guns.logic;

/** Rampage perk state: stacks gained on kills, all lost when the timer (refreshed on each kill) runs out. */
public record RampageStacks(int stacks, long expiresAt) {
	public static final RampageStacks NONE = new RampageStacks(0, 0);

	public RampageStacks onKill(long now, int durationTicks, int maxStacks) {
		return new RampageStacks(Math.min(active(now) + 1, maxStacks), now + durationTicks);
	}

	public int active(long now) {
		return now < expiresAt ? stacks : 0;
	}

	public double multiplier(long now, double bonusPerStack) {
		return 1.0 + active(now) * bonusPerStack;
	}
}
