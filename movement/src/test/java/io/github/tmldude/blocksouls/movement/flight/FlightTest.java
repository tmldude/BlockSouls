package io.github.tmldude.blocksouls.movement.flight;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import io.github.tmldude.blocksouls.movement.MovementConfig;
import io.github.tmldude.blocksouls.movement.stamina.StaminaState;
import io.github.tmldude.blocksouls.movement.trait.TraitMath;

class FlightTest {
	final MovementConfig.Traits config = new MovementConfig.Traits();

	@Test
	void drainsTwelvePerSecondAndPausesRegen() {
		MovementConfig.Stamina staminaConfig = new MovementConfig.Stamina();
		StaminaState stamina = StaminaState.full(staminaConfig);
		for (int t = 0; t < 20; t++) {
			stamina = stamina.tick(staminaConfig).spend(TraitMath.flightStaminaPerTick(config), staminaConfig);
		}
		assertEquals(88, stamina.value(), 1e-4);
	}

	@Test
	void fullBarRunsOutAndEndsFlight() {
		MovementConfig.Stamina staminaConfig = new MovementConfig.Stamina();
		StaminaState stamina = StaminaState.full(staminaConfig);
		int ticks = 0;
		while (!Flight.shouldEnd(false, stamina.value(), true, false, true)) {
			stamina = stamina.tick(staminaConfig).spend(TraitMath.flightStaminaPerTick(config), staminaConfig);
			ticks++;
		}
		assertEquals(167, ticks, 1);
	}

	@Test
	void endsOnLandingEmptyStaminaLostTraitOrDowned() {
		assertFalse(Flight.shouldEnd(false, 50, true, false, true));
		assertTrue(Flight.shouldEnd(true, 50, true, false, true));
		assertTrue(Flight.shouldEnd(false, 0, true, false, true));
		assertTrue(Flight.shouldEnd(false, 50, false, false, true));
		assertTrue(Flight.shouldEnd(false, 50, true, true, true));
		assertTrue(Flight.shouldEnd(false, 50, true, false, false));
	}

	@Test
	void doubleTapInTheAirWithinTheWindow() {
		Flight.DoubleTap tap = new Flight.DoubleTap();
		assertFalse(tap.tick(true, true, 7));
		for (int t = 0; t < 5; t++) {
			assertFalse(tap.tick(false, true, 7));
		}
		assertTrue(tap.tick(true, true, 7));
		// The double tap is consumed.
		assertFalse(tap.tick(true, true, 7));
	}

	@Test
	void slowTapsDontCount() {
		Flight.DoubleTap tap = new Flight.DoubleTap();
		tap.tick(true, true, 7);
		for (int t = 0; t < 8; t++) {
			tap.tick(false, true, 7);
		}
		assertFalse(tap.tick(true, true, 7));
	}

	@Test
	void landingResetsTheTap() {
		Flight.DoubleTap tap = new Flight.DoubleTap();
		tap.tick(true, true, 7);
		tap.tick(false, false, 7);
		assertFalse(tap.tick(true, true, 7));
	}
}
