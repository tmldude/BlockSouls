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

	private static final AttackPicker.Weights WEIGHTS = new AttackPicker.Weights(0.6, 0.8, 0.8, 1.2, 1.2, 200);

	@Test
	void scoringPrefersAntiAirAgainstAirborneTargets() {
		AttackPicker.Scored ground = new AttackPicker.Scored(new AttackPicker.Option(1, 0, 0, 10), new AttackPicker.Traits(0, -1, 0, 0), 1000);
		AttackPicker.Scored air = new AttackPicker.Scored(new AttackPicker.Option(1, 0, 0, 10), new AttackPicker.Traits(0, 1, 0, 0), 1000);
		AttackPicker.Context airborne = new AttackPicker.Context(5, 0, 1, false, true);
		assertTrue(AttackPicker.utility(air, airborne, WEIGHTS) > AttackPicker.utility(ground, airborne, WEIGHTS));
		Random random = new Random(3);
		int airPicks = 0;
		for (int i = 0; i < 1000; i++) {
			airPicks += AttackPicker.pickScored(List.of(ground, air), airborne, WEIGHTS, -1, 1, random);
		}
		assertTrue(airPicks > 700, "anti-air picked " + airPicks + "/1000");
	}

	@Test
	void scoringPrefersRearAttacksBehind() {
		AttackPicker.Scored thrust = new AttackPicker.Scored(new AttackPicker.Option(1, 0, 0, 10), new AttackPicker.Traits(1, 0, 0, 0), 1000);
		AttackPicker.Scored tail = new AttackPicker.Scored(new AttackPicker.Option(1, 0, 0, 10), new AttackPicker.Traits(-1, 0, 0, 0), 1000);
		AttackPicker.Context behind = new AttackPicker.Context(5, 170, 1, false, false);
		AttackPicker.Context ahead = new AttackPicker.Context(5, 5, 1, false, false);
		assertTrue(AttackPicker.utility(tail, behind, WEIGHTS) > AttackPicker.utility(thrust, behind, WEIGHTS));
		assertTrue(AttackPicker.utility(thrust, ahead, WEIGHTS) > AttackPicker.utility(tail, ahead, WEIGHTS));
	}

	@Test
	void scoringNeverRemovesAnAttackAndRespectsRangeAndCooldown() {
		AttackPicker.Scored bad = new AttackPicker.Scored(new AttackPicker.Option(1, 0, 0, 10), new AttackPicker.Traits(-1, -1, -1, -1), 0);
		AttackPicker.Context worst = new AttackPicker.Context(5, 0, 0, true, true);
		assertTrue(AttackPicker.utility(bad, worst, WEIGHTS) >= Fairness.ADAPTATION_FLOOR);
		AttackPicker.Scored cooling = new AttackPicker.Scored(new AttackPicker.Option(5, 20, 0, 10), AttackPicker.Traits.NONE, 1000);
		AttackPicker.Scored far = new AttackPicker.Scored(new AttackPicker.Option(5, 0, 20, 30), AttackPicker.Traits.NONE, 1000);
		AttackPicker.Context near = new AttackPicker.Context(5, 0, 1, false, false);
		Random random = new Random(4);
		for (int i = 0; i < 100; i++) {
			assertEquals(2, AttackPicker.pickScored(List.of(cooling, far, bad), near, WEIGHTS, -1, 1, random));
		}
		assertEquals(-1, AttackPicker.pickScored(List.of(cooling, far), near, WEIGHTS, -1, 1, random));
	}

	@Test
	void freshnessFavoursAttacksNotSeenInAWhile() {
		AttackPicker.Option o = new AttackPicker.Option(1, 0, 0, 10);
		AttackPicker.Context c = new AttackPicker.Context(5, 0, 1, false, false);
		assertTrue(AttackPicker.utility(new AttackPicker.Scored(o, AttackPicker.Traits.NONE, 400), c, WEIGHTS)
				> AttackPicker.utility(new AttackPicker.Scored(o, AttackPicker.Traits.NONE, 10), c, WEIGHTS));
	}
}
