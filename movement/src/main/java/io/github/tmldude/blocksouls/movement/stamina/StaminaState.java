package io.github.tmldude.blocksouls.movement.stamina;

import io.github.tmldude.blocksouls.movement.MovementConfig;

/** Immutable stamina snapshot; every change returns a new state so attachment sync sees it. */
public record StaminaState(float value, int regenDelay) {
	public static StaminaState full(MovementConfig.Stamina config) {
		return new StaminaState((float) config.max, 0);
	}

	public boolean canAfford(double cost) {
		return value >= cost;
	}

	/** Spends stamina (clamped at 0) and restarts the regen delay. */
	public StaminaState spend(double amount, MovementConfig.Stamina config) {
		return new StaminaState((float) Math.max(0, value - amount), config.regenDelayTicks);
	}

	public StaminaState tick(MovementConfig.Stamina config) {
		return tick(config, 1.0);
	}

	/** @param regenMultiplier scales the regen rate (the {@code STAMINA_REGEN} trait) */
	public StaminaState tick(MovementConfig.Stamina config, double regenMultiplier) {
		return tick(config, regenMultiplier, config.max);
	}

	/** @param max this player's max ({@code STAMINA_MAX_PENALTY}); stamina above it is clamped down at once */
	public StaminaState tick(MovementConfig.Stamina config, double regenMultiplier, double max) {
		if (value > max) {
			return new StaminaState((float) max, regenDelay);
		}
		if (regenDelay > 0) {
			return new StaminaState(value, regenDelay - 1);
		}
		if (value >= max) {
			return value == max ? this : new StaminaState((float) max, 0);
		}
		return new StaminaState((float) Math.min(max, value + config.regenPerSecond * regenMultiplier / 20.0), 0);
	}

	public float fraction(MovementConfig.Stamina config) {
		return fraction(config.max);
	}

	public float fraction(double max) {
		return (float) Math.min(1.0, value / max);
	}
}
