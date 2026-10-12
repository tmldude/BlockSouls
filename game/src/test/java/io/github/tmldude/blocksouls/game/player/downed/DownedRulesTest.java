package io.github.tmldude.blocksouls.game.player.downed;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class DownedRulesTest {
	@Test
	void downedAttackersDealScaledDamage() {
		assertEquals(10f, DownedRules.damage(10f, false, 0.5));
		assertEquals(5f, DownedRules.damage(10f, true, 0.5));
		assertEquals(0f, DownedRules.damage(10f, true, -1), "never negative");
	}
}
