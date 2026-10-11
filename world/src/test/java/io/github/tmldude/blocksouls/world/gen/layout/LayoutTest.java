package io.github.tmldude.blocksouls.world.gen.layout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

class LayoutTest {
	/** Features may only touch the chunk they start in and its neighbours: keep within 7 blocks of the origin. */
	private static final int MAX_RADIUS = 7;

	@Test
	void wreckHasOneChestAKartSpotAndScrap() {
		for (long seed = 0; seed < 200; seed++) {
			Layout wreck = WreckLayout.generate(seed);
			assertEquals(1, wreck.chests().size(), "seed " + seed);
			assertNotNull(wreck.kartSpot());
			assertTrue(wreck.scrapPiles().size() >= 2, "seed " + seed);
			assertTrue(wreck.radius() <= MAX_RADIUS, "seed " + seed + " radius " + wreck.radius());
		}
	}

	@Test
	void wreckChestIsCoveredByARoof() {
		for (long seed = 0; seed < 50; seed++) {
			Layout wreck = WreckLayout.generate(seed);
			Cell chest = wreck.chests().get(0);
			assertTrue(wreck.cells().stream().anyMatch(c -> c.x() == chest.x() && c.z() == chest.z() && c.y() == chest.y() + 1
					&& c.part() == Part.HULL), "seed " + seed);
		}
	}

	@Test
	void kartSpotIsClearOfTheShell() {
		for (long seed = 0; seed < 200; seed++) {
			Layout wreck = WreckLayout.generate(seed);
			int[] kart = wreck.kartSpot();
			assertFalse(wreck.cells().stream().anyMatch(c -> c.x() == kart[0] && c.z() == kart[1] && c.y() >= 0), "seed " + seed);
		}
	}

	@Test
	void checkpointHasOneOrTwoChests() {
		Set<Integer> counts = new HashSet<>();
		for (long seed = 0; seed < 200; seed++) {
			Layout checkpoint = CheckpointLayout.generate(seed);
			int chests = checkpoint.chests().size();
			assertTrue(chests == 1 || chests == 2, "seed " + seed + ": " + chests);
			counts.add(chests);
			assertTrue(checkpoint.radius() <= MAX_RADIUS, "seed " + seed + " radius " + checkpoint.radius());
			assertTrue(checkpoint.cells().stream().anyMatch(c -> c.part() == Part.TOWER_POST));
			assertTrue(checkpoint.cells().stream().anyMatch(c -> c.part() == Part.SANDBAG || c.part() == Part.SANDBAG_MUD));
		}
		assertEquals(Set.of(1, 2), counts, "both one and two chests occur");
	}

	@Test
	void sameSeedSameLayout() {
		assertEquals(WreckLayout.generate(42).cells(), WreckLayout.generate(42).cells());
		assertEquals(CheckpointLayout.generate(42).cells(), CheckpointLayout.generate(42).cells());
	}

	@Test
	void noTwoCellsShareAPosition() {
		for (long seed = 0; seed < 50; seed++) {
			for (Layout layout : new Layout[] {WreckLayout.generate(seed), CheckpointLayout.generate(seed)}) {
				Set<String> seen = new HashSet<>();
				for (Cell c : layout.cells()) {
					assertTrue(seen.add(c.x() + "," + c.y() + "," + c.z()), "duplicate cell " + c);
				}
			}
		}
	}

	@Test
	void rotationTurnsClockwiseAndBack() {
		Cell cell = new Cell(2, 1, 0, Part.HULL);
		assertEquals(new Cell(0, 1, 2, Part.HULL), cell.rotate(1));
		assertEquals(new Cell(-2, 1, 0, Part.HULL), cell.rotate(2));
		assertEquals(cell, cell.rotate(4));
	}
}
