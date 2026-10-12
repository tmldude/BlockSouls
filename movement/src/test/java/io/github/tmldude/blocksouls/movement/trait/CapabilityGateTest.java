package io.github.tmldude.blocksouls.movement.trait;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import io.github.tmldude.blocksouls.movement.MovementConfig;

/**
 * The origin capability gate must be invisible to all-granted (v1) players: with every verb held, each gated number
 * equals the ungated v1 value; without the verb, the mechanic is off.
 */
class CapabilityGateTest {
	final MovementConfig config = new MovementConfig();

	@Test
	void airJumpsEqualV1WithTheVerbAndVanishWithoutIt() {
		for (int level = 0; level <= 3; level++) {
			assertEquals(TraitMath.airJumps(level, config.traits), TraitMath.airJumps(level, config.traits, true), "v1 at level " + level);
			assertEquals(0, TraitMath.airJumps(level, config.traits, false), "gated at level " + level);
		}
	}

	@Test
	void staminaMaxIsTheConfigMaxWithoutAPenalty() {
		assertEquals(config.stamina.max, TraitMath.staminaMax(0, config.stamina, config.traits));
		assertEquals(config.stamina.max - 25, TraitMath.staminaMax(1, config.stamina, config.traits));
		assertEquals(config.traits.staminaMaxFloor, TraitMath.staminaMax(10, config.stamina, config.traits), "floored");
	}

	@Test
	void staminaFloorNeverExceedsTheConfigMax() {
		config.stamina.max = 20;
		assertEquals(20, TraitMath.staminaMax(0, config.stamina, config.traits));
		assertEquals(20, TraitMath.staminaMax(5, config.stamina, config.traits));
	}
}
