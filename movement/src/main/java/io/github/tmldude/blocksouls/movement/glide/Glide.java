package io.github.tmldude.blocksouls.movement.glide;

import io.github.tmldude.blocksouls.movement.MovementConfig;

/** Pure rules for the glide wings ({@code Capabilities.GLIDE}): when they carry, what they cost, where the lift is. */
public final class Glide {
	private Glide() {
	}

	/**
	 * Whether the wings carry the player right now (start a glide, or keep one going).
	 *
	 * @param capable  the player holds the {@code GLIDE} verb
	 * @param downed   downed players never glide
	 * @param inLiquid no gliding in water or lava
	 */
	public static boolean canGlide(boolean capable, boolean downed, boolean inLiquid, float stamina, MovementConfig.Glide config) {
		return config.enabled && capable && !downed && !inLiquid && stamina > 0;
	}

	public static double staminaPerTick(MovementConfig.Glide config) {
		return config.staminaPerSecond / 20.0;
	}

	/**
	 * Whether a dusk thermal rises here: a sky-lit dimension at dusk, under open sky, within the thermal's height
	 * above the ground.
	 *
	 * @param dayTime      the dimension's day time (mod 24000 is applied here)
	 * @param skyLit       the dimension has a sky (no thermals in the Nether or the End)
	 * @param canSeeSky    nothing between the player and the sky
	 * @param heightAbove  how far the player's feet are above the ground below them (blocks)
	 */
	public static boolean isThermal(long dayTime, boolean skyLit, boolean canSeeSky, int heightAbove, MovementConfig.Glide config) {
		if (!skyLit || !canSeeSky || heightAbove < 0 || heightAbove > config.thermalHeight) {
			return false;
		}
		long time = Math.floorMod(dayTime, 24000L);
		return time >= config.thermalStartTime && time <= config.thermalEndTime;
	}

	/** Upward velocity added this tick while gliding: the updraft's lift, or 0 outside one. */
	public static double lift(boolean gliding, boolean inUpdraft, MovementConfig.Glide config) {
		return gliding && inUpdraft ? config.updraftLift : 0.0;
	}
}
