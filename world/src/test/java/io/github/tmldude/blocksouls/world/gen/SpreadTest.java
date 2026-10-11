package io.github.tmldude.blocksouls.world.gen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import io.github.tmldude.blocksouls.world.WorldConfig;

class SpreadTest {
	@Test
	void defaultsMatchTheOldRarity() {
		WorldConfig.Gen gen = new WorldConfig.Gen();
		// One attempt per 144 chunks (wreck) / 576 chunks (checkpoint), as the old placed features rolled.
		Spread wreck = Spread.forKey(gen, "wreck");
		assertEquals(144, wreck.spacing() * wreck.spacing());
		Spread checkpoint = Spread.forKey(gen, "checkpoint");
		assertEquals(576, checkpoint.spacing() * checkpoint.spacing());
		assertTrue(wreck.enabled() && checkpoint.enabled());
	}

	@Test
	void separationStaysBelowSpacing() {
		Spread spread = Spread.of(6, 10);
		assertEquals(6, spread.spacing());
		assertEquals(5, spread.separation());
		assertEquals(0, Spread.of(6, -3).separation());
		assertEquals(2, Spread.of(1, 0).spacing());
	}

	@Test
	void zeroSpacingDisables() {
		assertFalse(Spread.of(0, 4).enabled());
	}
}
