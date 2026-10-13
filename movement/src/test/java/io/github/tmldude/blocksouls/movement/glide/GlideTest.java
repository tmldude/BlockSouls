package io.github.tmldude.blocksouls.movement.glide;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import io.github.tmldude.blocksouls.movement.MovementConfig;

class GlideTest {
	final MovementConfig.Glide config = new MovementConfig.Glide();

	@Test
	void wingsCarryOnlyCapableStandingPlayersWithStamina() {
		assertTrue(Glide.canGlide(true, false, false, 10, config));
		assertFalse(Glide.canGlide(false, false, false, 10, config), "no GLIDE verb (the v1 default set)");
		assertFalse(Glide.canGlide(true, true, false, 10, config), "downed");
		assertFalse(Glide.canGlide(true, false, true, 10, config), "in liquid");
		assertFalse(Glide.canGlide(true, false, false, 0, config), "out of stamina");
		config.enabled = false;
		assertFalse(Glide.canGlide(true, false, false, 10, config), "disabled in config");
	}

	@Test
	void drainsEightStaminaPerSecond() {
		assertEquals(8, Glide.staminaPerTick(config) * 20, 1e-9);
	}

	@Test
	void duskThermalsRiseOverOpenGroundAtDusk() {
		assertTrue(Glide.isThermal(12000, true, true, 10, config));
		assertTrue(Glide.isThermal(24000 + 12000, true, true, 10, config), "day time wraps");
		assertFalse(Glide.isThermal(6000, true, true, 10, config), "noon");
		assertFalse(Glide.isThermal(12000, false, true, 10, config), "no sky (Nether)");
		assertFalse(Glide.isThermal(12000, true, false, 10, config), "under a roof");
		assertFalse(Glide.isThermal(12000, true, true, config.thermalHeight + 1, config), "above the thermal");
		assertFalse(Glide.isThermal(12000, true, true, -1, config), "below ground level");
	}

	@Test
	void liftOnlyWhileGlidingInAnUpdraft() {
		assertEquals(config.updraftLift, Glide.lift(true, true, config));
		assertEquals(0, Glide.lift(true, false, config));
		assertEquals(0, Glide.lift(false, true, config));
	}
}
