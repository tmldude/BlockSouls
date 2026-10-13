package io.github.tmldude.blocksouls.bosses.api;

import net.minecraft.world.entity.LivingEntity;

/**
 * One boss move: windup (telegraph) → active (hitboxes) → recovery (punish window). Subclass it and override the
 * hooks you need; per-execution state lives in the {@link AttackRun} the hooks receive, so one instance serves every
 * boss of a kind. Stage lengths come from {@link AttackStats} (scaled by the current phase), and the framework moves
 * through the stages on its own, so an attack can never get stuck.
 *
 * <p>Every hook runs on the server. The client only sees the synced stage and the attack's {@link #name()}, which its
 * model uses to pick an animation.
 */
public abstract class Attack {
	private final String name;
	private final AttackStats stats;

	/**
	 * @param name  unique within the boss; also the animation key on the client ({@code "slam"})
	 * @param stats usually a field of the boss's config, so the numbers are tunable
	 */
	protected Attack(String name, AttackStats stats) {
		this.name = name;
		this.stats = stats;
	}

	public final String name() {
		return name;
	}

	public final AttackStats stats() {
		return stats;
	}

	/** Extra conditions on top of range and cooldown (e.g. needs line of sight, or only while on the ground). */
	public boolean canUse(BossEntity boss, LivingEntity target) {
		return true;
	}

	/**
	 * Share of the windup during which the boss keeps turning toward its target. After that the direction is locked,
	 * so a late dodge works.
	 */
	public double trackFraction() {
		return 0.7;
	}

	/**
	 * What this move is good at, for the utility picker ({@code picker} in bosses.json): frontal, anti-air, punishing
	 * a roll, pressuring a tired target.
	 */
	public io.github.tmldude.blocksouls.bosses.logic.AttackPicker.Traits traits() {
		return io.github.tmldude.blocksouls.bosses.logic.AttackPicker.Traits.NONE;
	}

	/** Windup begins. {@link AttackRun#direction()} already faces the target. */
	public void start(AttackRun run) {
	}

	/** Every windup tick: draw the telegraph. {@link AttackRun#progress()} goes 0 → 1. */
	public void windup(AttackRun run) {
	}

	/** Every active tick: deal damage (each victim once per run unless you use {@link AttackRun#hurtEvery}). */
	public void active(AttackRun run) {
	}

	/** Every recovery tick. */
	public void recovery(AttackRun run) {
	}

	/** Always called last, also when the attack is cut short (reset, death). */
	public void end(AttackRun run) {
	}
}
