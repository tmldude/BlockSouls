package io.github.tmldude.blocksouls.world.gen.layout;

import java.util.Random;

/**
 * A collapsed road checkpoint: a broken barrier wall across the road, a line of sandbags in front, a half-fallen
 * watchtower at one end, one or two loot chests and scrap piles.
 */
public final class CheckpointLayout {
	private CheckpointLayout() {
	}

	public static Layout generate(long seed) {
		Random random = new Random(seed);
		Layout.Builder b = new Layout.Builder();

		// Barrier wall along x at z = 0, 3 high at best, crumbling toward the ends and with a breach.
		int breach = -3 + random.nextInt(5);
		for (int x = -5; x <= 5; x++) {
			if (x == breach || x == breach + 1) {
				b.set(x, 0, 0, Part.WALL_CRACKED);
				continue;
			}
			int height = 3 - (Math.abs(x) >= 4 ? 1 : 0) - random.nextInt(2);
			for (int y = 0; y < height; y++) {
				b.set(x, y, 0, wall(random));
			}
		}
		// Concrete barrier blocks behind the wall.
		for (int x = -4; x <= 4; x += 4) {
			b.set(x, 0, 1, Part.CONCRETE);
		}

		// Sandbag line in front of the wall at z = -2, with gaps.
		for (int x = -4; x <= 3; x++) {
			if (random.nextInt(5) == 0) {
				continue;
			}
			b.set(x, 0, -2, random.nextInt(3) == 0 ? Part.SANDBAG_MUD : Part.SANDBAG);
			if (random.nextInt(3) == 0) {
				b.set(x, 1, -2, Part.SANDBAG);
			}
		}

		// Watchtower at the east end: 2×2 posts, a floor at y = 4, the far corner post fallen short.
		int tx = 5;
		int tz = 3;
		int[][] posts = {{tx - 1, tz - 1}, {tx, tz - 1}, {tx - 1, tz}, {tx, tz}};
		for (int i = 0; i < posts.length; i++) {
			int top = i == 3 ? 1 + random.nextInt(2) : 4;
			for (int y = 0; y < top; y++) {
				b.set(posts[i][0], y, posts[i][1], Part.TOWER_POST);
			}
		}
		for (int x = tx - 2; x <= tx; x++) {
			for (int z = tz - 2; z <= tz; z++) {
				boolean overPost = (x == tx - 1 || x == tx) && (z == tz - 1 || z == tz);
				if (!overPost && random.nextInt(3) != 0) {
					b.set(x, 4, z, Part.TOWER_FLOOR);
				}
			}
		}
		b.set(tx - 1, 4, tz - 1, Part.TOWER_FLOOR);
		b.set(tx - 1, 5, tz - 1, Part.FENCE);
		b.set(tx - 2, 5, tz - 2, Part.FENCE);

		// Chest at the tower's foot, behind the wall; a second one behind the sandbags half the time.
		b.set(tx - 2, 0, tz, Part.CHEST);
		if (random.nextBoolean()) {
			b.set(-3 + random.nextInt(3), 0, -1, Part.CHEST);
		}

		int piles = 2 + random.nextInt(2);
		for (int i = 0; i < piles; i++) {
			int x = -6 + random.nextInt(9);
			int z = random.nextBoolean() ? -(4 + random.nextInt(2)) : 2 + random.nextInt(3);
			if (!b.has(x, 0, z)) {
				b.scrapPile(x, z);
			}
		}

		return b.build().rotate(random.nextInt(4));
	}

	private static Part wall(Random random) {
		int roll = random.nextInt(10);
		return roll < 6 ? Part.WALL : roll < 8 ? Part.WALL_CRACKED : roll < 9 ? Part.WALL_MOSSY : Part.CONCRETE;
	}
}
