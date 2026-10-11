package io.github.tmldude.blocksouls.bosses.raid.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;

class CrystalOrderTest {
	@Test
	void generatePicksDistinctSlotsPreferringOpenPillars() {
		for (long seed = 0; seed < 50; seed++) {
			CrystalOrder order = CrystalOrder.generate(List.of(0, 1, 2, 3, 4, 5, 6, 7), List.of(8, 9), 6, new Random(seed));
			assertEquals(6, order.remaining().size());
			assertEquals(6, new HashSet<>(order.remaining()).size(), "no slot twice");
			assertFalse(order.contains(8) || order.contains(9), "caged pillars only when the open ones run out");
		}
	}

	@Test
	void generateFallsBackToCagedPillarsAndCapsAtWhatExists() {
		CrystalOrder order = CrystalOrder.generate(List.of(0, 1), List.of(8, 9), 3, new Random(1));
		assertEquals(3, order.remaining().size());
		assertTrue(order.contains(0) && order.contains(1));
		CrystalOrder all = CrystalOrder.generate(List.of(0, 1), List.of(8), 10, new Random(1));
		assertEquals(3, all.remaining().size());
	}

	@Test
	void generateShufflesTheOrder() {
		var seen = new HashSet<List<Integer>>();
		for (long seed = 0; seed < 20; seed++) {
			seen.add(CrystalOrder.generate(List.of(0, 1, 2, 3), List.of(), 4, new Random(seed)).remaining());
		}
		assertTrue(seen.size() > 3, "orders should vary between fights");
	}

	@Test
	void rightOrderCompletes() {
		CrystalOrder order = new CrystalOrder(List.of(3, 1, 2));
		Random random = new Random(0);
		assertEquals(3, order.next());
		assertEquals(CrystalOrder.Result.RIGHT, order.hit(3, random));
		assertEquals(CrystalOrder.Result.RIGHT, order.hit(1, random));
		assertEquals(CrystalOrder.Result.DONE, order.hit(2, random));
		assertTrue(order.isDone());
		assertEquals(-1, order.next());
		assertEquals(3, order.destroyed());
	}

	@Test
	void wrongCrystalReshufflesTheRemainingOrderAndKeepsIt() {
		for (long seed = 0; seed < 50; seed++) {
			CrystalOrder order = new CrystalOrder(List.of(0, 1, 2, 3));
			Random random = new Random(seed);
			assertEquals(CrystalOrder.Result.RIGHT, order.hit(0, random));
			List<Integer> before = List.copyOf(order.remaining());
			assertEquals(CrystalOrder.Result.WRONG, order.hit(3, random));
			assertNotEquals(before, order.remaining(), "the order must change after a mistake");
			assertEquals(new HashSet<>(before), new HashSet<>(order.remaining()), "the wrong crystal re-forms: same set");
			assertEquals(1, order.destroyed(), "a mistake doesn't count as progress");
		}
	}

	@Test
	void reshuffleOfTwoAlwaysSwaps() {
		for (long seed = 0; seed < 20; seed++) {
			CrystalOrder order = new CrystalOrder(List.of(5, 7));
			order.reshuffle(new Random(seed));
			assertEquals(List.of(7, 5), order.remaining());
		}
	}

	@Test
	void hitsOutsideTheOrderAreIgnored() {
		CrystalOrder order = new CrystalOrder(List.of(1, 2));
		assertEquals(CrystalOrder.Result.IGNORED, order.hit(9, new Random(0)));
		assertEquals(List.of(1, 2), order.remaining());
	}
}
