package io.github.tmldude.blocksouls.game.run.director;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class InfluenceMapTest {
	private static final double EAST = 0;
	private static final double WEST = Math.PI;

	/** A party of {@code players} all aiming east: the flank and the quiet sectors are west. */
	private static InfluenceMap partyFacingEast(int players) {
		InfluenceMap map = new InfluenceMap(8);
		for (int i = 0; i < players; i++) {
			map.add(EAST + 0.05 * i, 1.0, 0.4);
		}
		return map;
	}

	@Test
	void flankIsBehindTheGunsSolo() {
		InfluenceMap map = partyFacingEast(1);
		assertEquals(map.sectorOf(WEST), map.flank());
		assertEquals(map.sectorOf(WEST) != map.sectorOf(EAST), true);
	}

	@Test
	void flankIsBehindTheGunsAtEight() {
		InfluenceMap map = partyFacingEast(8);
		assertEquals(map.sectorOf(WEST), map.flank());
		// Picks favour the quiet half: most rolls land away from the eastern lane.
		int east = 0;
		for (int i = 0; i < 1000; i++) {
			int sector = map.pick(i / 1000.0, 3.0);
			if (sector == map.sectorOf(EAST)) {
				east++;
			}
		}
		assertTrue(east < 1000 / 8, "the gun lane gets fewer than a uniform share: " + east);
	}

	@Test
	void eightPlayersSplitAcrossLanesLeaveTheGapsQuiet() {
		InfluenceMap map = new InfluenceMap(8);
		// Eight players, two looking each way along the axes: the diagonals are the gaps.
		for (int i = 0; i < 8; i++) {
			map.add(i / 2 * Math.PI / 2 + 0.01, 1.0, 0.0);
		}
		int quiet = map.quietest();
		assertEquals(1, quiet % 2, "a diagonal sector is quietest");
	}

	@Test
	void noPressureIsUniform() {
		InfluenceMap map = new InfluenceMap(8);
		assertFalse(map.hasPressure());
		assertEquals(0, map.pick(0.0, 3));
		assertEquals(7, map.pick(0.99, 3));
		assertEquals(4, map.pick(0.5, 3));
	}

	@Test
	void decayForgets() {
		InfluenceMap map = partyFacingEast(1);
		map.decay(0.5);
		assertEquals(0.5, map.pressure(map.sectorOf(EAST)), 1e-9);
		map.decay(0);
		assertFalse(map.hasPressure());
	}

	@Test
	void sectorMathWraps() {
		InfluenceMap map = new InfluenceMap(8);
		assertEquals(0, map.sectorOf(0));
		assertEquals(7, map.sectorOf(-0.1));
		assertEquals(4, map.sectorOf(Math.PI + 0.01));
		assertEquals(map.sectorOf(map.centreOf(5)), 5);
	}

	@Test
	void yawConvention() {
		// Minecraft yaw 0 faces +z (south), -90 faces +x (east).
		assertEquals(Math.PI / 2, InfluenceMap.yawToAngle(0), 1e-9);
		assertEquals(0, InfluenceMap.yawToAngle(-90), 1e-9);
	}
}
