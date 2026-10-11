package io.github.tmldude.blocksouls.game.run;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

class RosterShuffleTest {
	@Test
	void offsetsStayInRangeAndVaryBetweenRuns() {
		Set<Integer> seen = new HashSet<>();
		for (long seed = 0; seed < 200; seed++) {
			int offset = RosterShuffle.offset("blocksouls_mobs:brute", seed, 1);
			assertTrue(offset >= -1 && offset <= 1);
			seen.add(offset);
		}
		assertEquals(Set.of(-1, 0, 1), seen);
	}

	@Test
	void offsetIsStablePerRun() {
		assertEquals(RosterShuffle.offset("a:b", 99, 1), RosterShuffle.offset("a:b", 99, 1));
		assertEquals(0, RosterShuffle.offset("a:b", 99, 0));
	}

	@Test
	void neverBelowWaveOne() {
		assertEquals(1, RosterShuffle.shiftedMinWave(1, -1));
		assertEquals(4, RosterShuffle.shiftedMinWave(5, -1));
		assertEquals(6, RosterShuffle.shiftedMinWave(5, 1));
	}
}
