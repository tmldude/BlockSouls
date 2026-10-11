package io.github.tmldude.blocksouls.bosses.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Map;

import org.junit.jupiter.api.Test;

class AggroTableTest {
	@Test
	void closestWinsWithoutDamage() {
		AggroTable<String> table = new AggroTable<>();
		assertEquals("near", table.choose(null, Map.of("near", 3.0, "far", 12.0), 10, 16, 0.25));
	}

	@Test
	void damageDealerPullsAggro() {
		AggroTable<String> table = new AggroTable<>();
		table.addDamage("sniper", 40);
		assertEquals("sniper", table.choose("near", Map.of("near", 2.0, "sniper", 20.0), 10, 16, 0.25));
	}

	@Test
	void keepsCurrentTargetWithinMargin() {
		AggroTable<String> table = new AggroTable<>();
		table.addDamage("a", 10);
		table.addDamage("b", 11);
		assertEquals("a", table.choose("a", Map.of("a", 30.0, "b", 30.0), 10, 16, 0.25), "11 isn't 25% more than 10");
		table.addDamage("b", 5);
		assertEquals("b", table.choose("a", Map.of("a", 30.0, "b", 30.0), 10, 16, 0.25));
	}

	@Test
	void dropsTargetsThatLeft() {
		AggroTable<String> table = new AggroTable<>();
		table.addDamage("gone", 100);
		assertEquals("here", table.choose("gone", Map.of("here", 5.0), 10, 16, 0.25));
		assertNull(table.choose("gone", Map.of(), 10, 16, 0.25));
	}

	@Test
	void threatDecays() {
		AggroTable<String> table = new AggroTable<>();
		table.addDamage("a", 10);
		table.decay(0.5);
		assertEquals(5, table.threat("a"), 1e-9);
	}
}
