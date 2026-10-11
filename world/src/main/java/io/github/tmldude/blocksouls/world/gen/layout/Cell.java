package io.github.tmldude.blocksouls.world.gen.layout;

/** One block of a layout, relative to the structure origin (y = 0 is the first block above the ground). */
public record Cell(int x, int y, int z, Part part) {
	/** Rotated a quarter turn clockwise {@code turns} times around the origin. */
	public Cell rotate(int turns) {
		int[] r = Layout.rotate(x, z, turns);
		return new Cell(r[0], y, r[1], part);
	}
}
