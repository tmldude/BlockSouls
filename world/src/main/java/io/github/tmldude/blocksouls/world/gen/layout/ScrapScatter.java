package io.github.tmldude.blocksouls.world.gen.layout;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Extra scrap piles strewn around a ruin, so the first loops find scrap where they find structures. Piles go in a ring
 * just outside the ruin's blocks and no further than {@link #MAX_RADIUS} out (a ruin stays inside its start chunk),
 * never on a column the ruin builds on, on the kart spot or on another pile.
 */
public final class ScrapScatter {
	/** A ruin starts at its chunk's centre, so 7 blocks out is the chunk edge. */
	public static final int MAX_RADIUS = 7;
	/** Placement tries per pile before giving up (only matters when the ring is nearly full). */
	private static final int TRIES = 16;

	private ScrapScatter() {
	}

	/** {@code layout} plus up to {@code extra} piles, the same for the same seed. Returns {@code layout} for 0 or less. */
	public static Layout add(Layout layout, long seed, int extra) {
		if (extra <= 0) {
			return layout;
		}
		Set<Long> taken = new HashSet<>();
		int inner = 0;
		for (Cell c : layout.cells()) {
			taken.add(key(c.x(), c.z()));
			inner = Math.max(inner, Math.max(Math.abs(c.x()), Math.abs(c.z())));
		}
		for (int[] p : layout.scrapPiles()) {
			taken.add(key(p[0], p[1]));
		}
		if (layout.kartSpot() != null) {
			taken.add(key(layout.kartSpot()[0], layout.kartSpot()[1]));
		}
		int min = Math.min(inner + 1, MAX_RADIUS);

		Random random = new Random(seed ^ 0x5C2A9L);
		List<int[]> piles = new ArrayList<>(layout.scrapPiles());
		for (int i = 0; i < extra; i++) {
			for (int t = 0; t < TRIES; t++) {
				int[] spot = ringSpot(random, min, MAX_RADIUS);
				if (taken.add(key(spot[0], spot[1]))) {
					piles.add(spot);
					break;
				}
			}
		}
		return new Layout(layout.cells(), List.copyOf(piles), layout.kartSpot());
	}

	/** A random column whose Chebyshev (square-ring) distance from the origin is in {@code [min, max]}. */
	static int[] ringSpot(Random random, int min, int max) {
		int r = min + random.nextInt(max - min + 1);
		int along = -r + random.nextInt(2 * r + 1);
		return switch (random.nextInt(4)) {
			case 0 -> new int[] {along, -r};
			case 1 -> new int[] {along, r};
			case 2 -> new int[] {-r, along};
			default -> new int[] {r, along};
		};
	}

	private static long key(int x, int z) {
		return ((long) x << 32) | (z & 0xFFFFFFFFL);
	}
}
