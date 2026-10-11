package io.github.tmldude.blocksouls.movement.trait;

import io.github.tmldude.blocksouls.movement.MovementConfig;

/** How trait levels scale movement numbers. Pure; levels stack linearly. {@link MovementTraits} reads the levels. */
public final class TraitMath {
	/** Vanilla creative flying speed. */
	public static final double CREATIVE_FLYING_SPEED = 0.05;

	private TraitMath() {
	}

	public static int airJumps(int level, MovementConfig.Traits config) {
		return Math.max(0, level) * config.airJumpsPerLevel;
	}

	/** Last i-frame tick of a roll: the base window's end, pushed later per level. */
	public static int rollIframeEnd(int level, MovementConfig.Roll roll, MovementConfig.Traits config) {
		return roll.iframeEnd + Math.max(0, level) * config.rollIframeTicksPerLevel;
	}

	public static double webRange(int level, MovementConfig.Web web, MovementConfig.Traits config) {
		return web.range * scale(level, config.webReachPerLevel);
	}

	public static double climbSpeedMultiplier(int level, MovementConfig.Traits config) {
		return scale(level, config.climbSpeedPerLevel);
	}

	public static double staminaRegenMultiplier(int level, MovementConfig.Traits config) {
		return scale(level, config.staminaRegenPerLevel);
	}

	public static float flyingSpeed(MovementConfig.Traits config) {
		return (float) (CREATIVE_FLYING_SPEED * config.flightSpeedFraction);
	}

	public static double flightStaminaPerTick(MovementConfig.Traits config) {
		return config.flightStaminaPerSecond / 20.0;
	}

	private static double scale(int level, double perLevel) {
		return 1.0 + Math.max(0, level) * perLevel;
	}
}
