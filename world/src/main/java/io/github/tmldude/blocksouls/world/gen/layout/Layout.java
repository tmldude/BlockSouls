package io.github.tmldude.blocksouls.world.gen.layout;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A small structure as plain data, so the shape logic is testable without Minecraft. {@code cells} are blocks;
 * {@code scrapPiles} are columns where a scrap pile goes on top of the ground; {@code kartSpot} is where a kart wreck
 * entity goes (null for none).
 */
public record Layout(List<Cell> cells, List<int[]> scrapPiles, int[] kartSpot) {
	public List<Cell> chests() {
		return cells.stream().filter(c -> c.part() == Part.CHEST).toList();
	}

	/** Largest |x| or |z| over everything in the layout. */
	public int radius() {
		int r = 0;
		for (Cell c : cells) {
			r = Math.max(r, Math.max(Math.abs(c.x()), Math.abs(c.z())));
		}
		for (int[] p : scrapPiles) {
			r = Math.max(r, Math.max(Math.abs(p[0]), Math.abs(p[1])));
		}
		if (kartSpot != null) {
			r = Math.max(r, Math.max(Math.abs(kartSpot[0]), Math.abs(kartSpot[1])));
		}
		return r;
	}

	public Layout rotate(int turns) {
		List<Cell> rotated = cells.stream().map(c -> c.rotate(turns)).toList();
		List<int[]> piles = scrapPiles.stream().map(p -> rotate(p[0], p[1], turns)).toList();
		return new Layout(rotated, piles, kartSpot == null ? null : rotate(kartSpot[0], kartSpot[1], turns));
	}

	static int[] rotate(int x, int z, int turns) {
		int rx = x;
		int rz = z;
		for (int i = 0; i < Math.floorMod(turns, 4); i++) {
			int t = rx;
			rx = -rz;
			rz = t;
		}
		return new int[] {rx, rz};
	}

	/** Collects cells; a later {@link #set} on the same position replaces the earlier one. */
	static final class Builder {
		private final Map<Long, Cell> cells = new LinkedHashMap<>();
		private final List<int[]> scrapPiles = new ArrayList<>();
		private int[] kartSpot;

		Builder set(int x, int y, int z, Part part) {
			cells.put(key(x, y, z), new Cell(x, y, z, part));
			return this;
		}

		Builder remove(int x, int y, int z) {
			cells.remove(key(x, y, z));
			return this;
		}

		boolean has(int x, int y, int z) {
			return cells.containsKey(key(x, y, z));
		}

		Builder scrapPile(int x, int z) {
			for (int[] p : scrapPiles) {
				if (p[0] == x && p[1] == z) {
					return this;
				}
			}
			scrapPiles.add(new int[] {x, z});
			return this;
		}

		int scrapPileCount() {
			return scrapPiles.size();
		}

		Builder kart(int x, int z) {
			kartSpot = new int[] {x, z};
			return this;
		}

		Layout build() {
			return new Layout(List.copyOf(cells.values()), List.copyOf(scrapPiles), kartSpot);
		}

		private static long key(int x, int y, int z) {
			return ((long) (x & 0xFFFF) << 32) | ((long) (y & 0xFFFF) << 16) | (z & 0xFFFF);
		}
	}
}
