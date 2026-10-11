package io.github.tmldude.blocksouls.bosses.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;

class AttackPickerTest {
	private static final AttackPicker.Option MELEE = new AttackPicker.Option(3, 0, 0, 5);
	private static final AttackPicker.Option RANGED = new AttackPicker.Option(1, 0, 6, 20);

	@Test
	void onlyPicksAttacksInRange() {
		List<AttackPicker.Option> options = List.of(MELEE, RANGED);
		Random random = new Random(1);
		for (int i = 0; i < 100; i++) {
			assertEquals(0, AttackPicker.pick(options, 3, -1, 1, random));
			assertEquals(1, AttackPicker.pick(options, 10, -1, 1, random));
		}
		assertEquals(-1, AttackPicker.pick(options, 30, -1, 1, random), "nothing reaches 30 blocks");
	}

	@Test
	void skipsAttacksOnCooldown() {
		AttackPicker.Option cooling = new AttackPicker.Option(100, 20, 0, 5);
		List<AttackPicker.Option> options = List.of(cooling, MELEE);
		Random random = new Random(2);
		for (int i = 0; i < 100; i++) {
			assertEquals(1, AttackPicker.pick(options, 2, -1, 1, random));
		}
		assertEquals(-1, AttackPicker.pick(List.of(cooling), 2, -1, 1, random));
	}

	@Test
	void followsWeights() {
		List<AttackPicker.Option> options = List.of(new AttackPicker.Option(3, 0, 0, 5), new AttackPicker.Option(1, 0, 0, 5));
		Random random = new Random(3);
		int first = 0;
		int n = 20000;
		for (int i = 0; i < n; i++) {
			if (AttackPicker.pick(options, 1, -1, 1, random) == 0) {
				first++;
			}
		}
		assertEquals(0.75, first / (double) n, 0.02);
	}

	@Test
	void repeatPenaltyLowersTheLastAttack() {
		List<AttackPicker.Option> options = List.of(new AttackPicker.Option(1, 0, 0, 5), new AttackPicker.Option(1, 0, 0, 5));
		Random random = new Random(4);
		int repeats = 0;
		int n = 20000;
		for (int i = 0; i < n; i++) {
			if (AttackPicker.pick(options, 1, 0, 0.25, random) == 0) {
				repeats++;
			}
		}
		assertEquals(0.2, repeats / (double) n, 0.02, "weight 0.25 vs 1");
		assertEquals(0, AttackPicker.pick(List.of(MELEE), 1, 0, 0.25, random), "a lone attack can still repeat");
	}

	@Test
	void zeroWeightNeverPicked() {
		List<AttackPicker.Option> options = List.of(new AttackPicker.Option(0, 0, 0, 5), MELEE);
		Random random = new Random(5);
		for (int i = 0; i < 100; i++) {
			assertEquals(1, AttackPicker.pick(options, 1, -1, 1, random));
		}
	}

	@Test
	void anyInRangeIgnoresCooldowns() {
		assertTrue(AttackPicker.anyInRange(List.of(new AttackPicker.Option(1, 50, 0, 5)), 4));
		assertFalse(AttackPicker.anyInRange(List.of(MELEE), 8));
	}
}
