package io.github.tmldude.blocksouls.movement.stamina;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import io.github.tmldude.blocksouls.movement.MovementConfig;

class StaminaStateTest {
	final MovementConfig.Stamina config = new MovementConfig.Stamina();

	@Test
	void spendingClampsAtZeroAndStartsTheDelay() {
		StaminaState state = StaminaState.full(config).spend(250, config);
		assertEquals(0, state.value());
		assertEquals(config.regenDelayTicks, state.regenDelay());
	}

	@Test
	void canOnlyAffordWhatIsLeft() {
		StaminaState state = new StaminaState(24.9f, 0);
		assertFalse(state.canAfford(25));
		assertTrue(new StaminaState(25, 0).canAfford(25));
	}

	@Test
	void waitsForTheDelayThenRegeneratesAtTheConfiguredRate() {
		StaminaState state = StaminaState.full(config).spend(25, config);
		for (int t = 0; t < config.regenDelayTicks; t++) {
			state = state.tick(config);
			assertEquals(75, state.value(), 1e-4, "no regen during the delay");
		}
		for (int t = 0; t < 20; t++) {
			state = state.tick(config);
		}
		assertEquals(75 + config.regenPerSecond, state.value(), 1e-3, "one second of regen");
	}

	@Test
	void regenStopsAtMaxAndFullStateIsStable() {
		StaminaState state = new StaminaState((float) config.max - 0.1f, 0).tick(config);
		assertEquals(config.max, state.value(), 1e-6);
		assertSame(state, state.tick(config), "no new state (and no sync) once full");
	}
}
