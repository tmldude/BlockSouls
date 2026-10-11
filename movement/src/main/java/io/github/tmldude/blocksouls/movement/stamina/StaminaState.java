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
		if (regenDelay > 0) {
			return new StaminaState(value, regenDelay - 1);
		}
		if (value >= config.max) {
			return value == config.max ? this : new StaminaState((float) config.max, 0);
		}
		return new StaminaState((float) Math.min(config.max, value + config.regenPerSecond / 20.0), 0);
	}

	public float fraction(MovementConfig.Stamina config) {
		return (float) (value / config.max);
	}
}
