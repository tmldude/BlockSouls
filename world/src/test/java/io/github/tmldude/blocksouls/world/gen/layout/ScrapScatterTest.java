package io.github.tmldude.blocksouls.world.gen.layout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import org.junit.jupiter.api.Test;

class ScrapScatterTest {
	private static List<Layout> ruins(long seed) {
		return List.of(WreckLayout.generate(seed), CheckpointLayout.generate(seed));
	}

	@Test
	void addsTheRequestedPilesInsideTheChunk() {
		for (long seed = 0; seed < 200; seed++) {
			for (Layout ruin : ruins(seed)) {
				Layout scattered = ScrapScatter.add(ruin, seed, 4);
				assertEquals(ruin.scrapPiles().size() + 4, scattered.scrapPiles().size(), "seed " + seed);
				assertTrue(scattered.radius() <= ScrapScatter.MAX_RADIUS, "seed " + seed + " radius " + scattered.radius());
				assertEquals(ruin.cells(), scattered.cells());
				assertEquals(ruin.kartSpot(), scattered.kartSpot());
			}
		}
	}

	@Test
	void extraPilesAvoidTheRuinTheKartAndEachOther() {
		for (long seed = 0; seed < 200; seed++) {
			for (Layout ruin : ruins(seed)) {
				Set<String> blocked = new HashSet<>();
				ruin.cells().forEach(c -> blocked.add(c.x() + "," + c.z()));
				if (ruin.kartSpot() != null) {
					blocked.add(ruin.kartSpot()[0] + "," + ruin.kartSpot()[1]);
				}
				Set<String> seen = new HashSet<>();
				ruin.scrapPiles().forEach(p -> seen.add(p[0] + "," + p[1]));
				List<int[]> all = ScrapScatter.add(ruin, seed, 5).scrapPiles();
				for (int[] p : all.subList(ruin.scrapPiles().size(), all.size())) {
					String key = p[0] + "," + p[1];
					assertFalse(blocked.contains(key), "seed " + seed + ": pile on the ruin at " + key);
					assertTrue(seen.add(key), "seed " + seed + ": duplicate pile at " + key);
				}
			}
		}
	}

	@Test
	void extraPilesSitOutsideTheRuinsBlocks() {
		for (long seed = 0; seed < 100; seed++) {
			for (Layout ruin : ruins(seed)) {
				int inner = ruin.cells().stream().mapToInt(c -> Math.max(Math.abs(c.x()), Math.abs(c.z()))).max().orElse(0);
				List<int[]> all = ScrapScatter.add(ruin, seed, 3).scrapPiles();
				for (int[] p : all.subList(ruin.scrapPiles().size(), all.size())) {
					int d = Math.max(Math.abs(p[0]), Math.abs(p[1]));
					assertTrue(d > inner && d <= ScrapScatter.MAX_RADIUS, "seed " + seed + ": distance " + d + ", ruin reaches " + inner);
				}
			}
		}
	}

	@Test
	void sameSeedSamePilesAndZeroIsANoOp() {
		Layout wreck = WreckLayout.generate(9);
		List<int[]> a = ScrapScatter.add(wreck, 9, 3).scrapPiles();
		List<int[]> b = ScrapScatter.add(wreck, 9, 3).scrapPiles();
		assertEquals(a.size(), b.size());
		for (int i = 0; i < a.size(); i++) {
			assertEquals(a.get(i)[0], b.get(i)[0]);
			assertEquals(a.get(i)[1], b.get(i)[1]);
		}
		assertSame(wreck, ScrapScatter.add(wreck, 9, 0));
		assertSame(wreck, ScrapScatter.add(wreck, 9, -1));
	}

	@Test
	void ringSpotStaysOnTheRing() {
		Random random = new Random(1);
		Set<String> seen = new HashSet<>();
		for (int i = 0; i < 2000; i++) {
			int[] p = ScrapScatter.ringSpot(random, 3, 7);
			int d = Math.max(Math.abs(p[0]), Math.abs(p[1]));
			assertTrue(d >= 3 && d <= 7, "distance " + d);
			seen.add(p[0] + "," + p[1]);
		}
		assertTrue(seen.size() > 100, "covers the ring, saw " + seen.size() + " columns");
	}
}
