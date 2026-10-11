package io.github.tmldude.blocksouls.vehicles.kart;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CooldownTest {
	@Test
	void readyFirstThenAfterTheCooldown() {
		Cooldown cooldown = new Cooldown();
		assertTrue(cooldown.tryUse(100, 20));
		assertFalse(cooldown.tryUse(101, 20));
		assertFalse(cooldown.tryUse(119, 20));
		assertTrue(cooldown.tryUse(120, 20));
		assertFalse(cooldown.ready(130, 20));
	}

	@Test
	void readyDoesntUseIt() {
		Cooldown cooldown = new Cooldown();
		assertTrue(cooldown.ready(0, 200));
		assertTrue(cooldown.ready(0, 200));
		assertTrue(cooldown.tryUse(0, 200));
		assertFalse(cooldown.ready(199, 200));
		cooldown.reset();
		assertTrue(cooldown.ready(199, 200));
	}
}
