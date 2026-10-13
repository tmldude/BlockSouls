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

	@Test
	void regenMultiplierScalesTheRate() {
		StaminaState state = new StaminaState(10, 0);
		for (int t = 0; t < 20; t++) {
			state = state.tick(config, 1.4);
		}
		assertEquals(10 + config.regenPerSecond * 1.4, state.value(), 1e-3);
	}

	@Test
	void aPerPlayerMaxCapsRegenAndClampsExcess() {
		StaminaState state = new StaminaState(70, 0);
		for (int t = 0; t < 200; t++) {
			state = state.tick(config, 1.0, 75);
		}
		assertEquals(75, state.value(), 1e-4, "regen stops at the player's max");
		assertEquals(75, StaminaState.full(config).tick(config, 1.0, 75).value(), 1e-4, "stamina above the max is clamped");
		assertEquals(1.0F, new StaminaState(80, 0).fraction(75), "the bar never overflows");
		assertEquals(0.5F, new StaminaState(50, 0).fraction(config), "the v1 fraction is unchanged");
	}
}
