package io.github.tmldude.blocksouls.movement.trait;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import io.github.tmldude.blocksouls.movement.MovementConfig;

class TraitMathTest {
	final MovementConfig config = new MovementConfig();

	@Test
	void levelZeroChangesNothing() {
		assertEquals(0, TraitMath.airJumps(0, config.traits));
		assertEquals(config.roll.iframeEnd, TraitMath.rollIframeEnd(0, config.roll, config.traits));
		assertEquals(32, TraitMath.webRange(0, config.web, config.traits), 1e-9);
		assertEquals(1, TraitMath.climbSpeedMultiplier(0, config.traits), 1e-9);
		assertEquals(1, TraitMath.staminaRegenMultiplier(0, config.traits), 1e-9);
	}

	@Test
	void levelsStack() {
		assertEquals(2, TraitMath.airJumps(2, config.traits));
		assertEquals(8 + 4, TraitMath.rollIframeEnd(2, config.roll, config.traits));
		assertEquals(32 * 1.5, TraitMath.webRange(2, config.web, config.traits), 1e-9);
		assertEquals(1.75, TraitMath.climbSpeedMultiplier(3, config.traits), 1e-9);
		assertEquals(1.4, TraitMath.staminaRegenMultiplier(2, config.traits), 1e-9);
	}

	@Test
	void flightIsSlowerThanCreative() {
		assertEquals(0.03, TraitMath.flyingSpeed(config.traits), 1e-6);
		assertEquals(0.6, TraitMath.flightStaminaPerTick(config.traits), 1e-9);
	}
}
