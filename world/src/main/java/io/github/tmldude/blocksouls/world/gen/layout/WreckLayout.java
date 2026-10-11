package io.github.tmldude.blocksouls.world.gen.layout;

import java.util.Random;

/**
 * A crashed car: a 5×3 chassis, a battered shell with a loot chest in the cabin, a nose dug into the ground, a few scrap
 * piles flung around and a spot for the kart wreck entity alongside.
 */
public final class WreckLayout {
	private WreckLayout() {
	}

	public static Layout generate(long seed) {
		Random random = new Random(seed);
		Layout.Builder b = new Layout.Builder();

		// Nose (x = 2) is dug one block into the ground: "crashed".
		for (int x = -2; x <= 2; x++) {
			int sink = x == 2 ? -1 : 0;
			for (int z = -1; z <= 1; z++) {
				b.set(x, sink, z, Part.CHASSIS);
			}
		}

		// Body sides and tail at y = 1, cabin hollow; the chest sits in the cabin.
		for (int x = -2; x <= 1; x++) {
			b.set(x, 1, -1, Part.HULL);
			b.set(x, 1, 1, Part.HULL);
		}
		b.set(-2, 1, 0, Part.HULL);
		b.set(2, 0, -1, Part.HULL);
		b.set(2, 0, 1, Part.HULL);
		b.set(2, 0, 0, Part.GRILLE);
		b.set(-1, 1, 0, Part.CHEST);
		b.set(0, 1, 0, Part.AIR);
		b.set(1, 1, 0, Part.AIR);

		// Windscreen at the front of the cabin, mostly smashed.
		for (int z = -1; z <= 1; z++) {
			if (random.nextInt(3) == 0) {
				b.set(1, 2, z, Part.GLASS);
			}
		}

		// Roof over the back half, with holes.
		for (int x = -2; x <= 0; x++) {
			for (int z = -1; z <= 1; z++) {
				if (random.nextInt(4) != 0) {
					b.set(x, 2, z, Part.HULL);
				}
			}
		}
		b.set(-1, 2, 0, Part.HULL); // the roof right over the chest always survives

		// Dents: knock 1-3 random shell blocks off the sides.
		int dents = 1 + random.nextInt(3);
		for (int i = 0; i < dents; i++) {
			int x = -2 + random.nextInt(4);
			int z = random.nextBoolean() ? -1 : 1;
			if (x != -1) {
				b.set(x, 1, z, Part.GRILLE);
			}
		}

		// Debris flung out ahead and to the sides.
		int piles = 2 + random.nextInt(3);
		while (b.scrapPileCount() < piles) {
			int x = -3 + random.nextInt(8);
			int z = random.nextBoolean() ? -(3 + random.nextInt(2)) : 3 + random.nextInt(2);
			b.scrapPile(x, z);
		}

		// The kart wreck rests beside the car.
		b.kart(0, random.nextBoolean() ? -3 : 3);

		return b.build().rotate(random.nextInt(4));
	}
}
