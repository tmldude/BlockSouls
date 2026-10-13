package io.github.tmldude.blocksouls.world.floor;

import org.jspecify.annotations.Nullable;

/**
 * The Vault's (floor 4) hidden stair: behind the hollow wall, a spiral stair in a corner of the secret room climbs
 * through the rock to a chamber over the room, where the second mini-boss waits. Pure (from the {@link BoxLayout}), so
 * the planner tests check it; {@code VaultBuilder} carves it.
 *
 * <p>The stair is a 3×3 shaft around a one-block pillar: step {@code k} (1..{@link #floorDy}) is one block at
 * {@code dy = k} on the ring around the pillar, starting on the ring cell that faces into the room and going round,
 * so each step is one up and one across from the last and the step above it is eight up. The last step is flush with
 * the chamber floor at {@code dy = floorDy}; the chamber is open from {@code floorDy + 1} for {@link #chamberHeight}.
 *
 * @param room          the secret room (its footprint is the chamber's)
 * @param shaftX        the stair pillar's column
 * @param shaftZ        the stair pillar's column
 * @param start         the ring index of the first step
 * @param floorDy       the chamber floor's height above the ground (above every room's and hall's ceiling)
 * @param chamberHeight the chamber's clear height
 */
public record VaultSecret(BoxLayout.Room room, int shaftX, int shaftZ, int start, int floorDy, int chamberHeight) {
	/** The ring around the pillar, in climbing order (each cell touches the next). */
	static final int[][] RING = {{-1, -1}, {0, -1}, {1, -1}, {1, 0}, {1, 1}, {0, 1}, {-1, 1}, {-1, 0}};
	/** Least clear height the chamber needs (the bosses are about three blocks tall). */
	static final int MIN_CHAMBER = 4;

	/** The chamber floor's height: two over the tallest room or hall ceiling, so nothing below breaks into it. */
	public static int floorDy(FloorTemplate.Box box) {
		return Math.max(box.roomHeight, box.hallHeight) + 2;
	}

	/** The secret stair and chamber for a layout, or null when there is no secret room or no headroom under the lid. */
	public static @Nullable VaultSecret plan(BoxLayout layout, FloorTemplate.Box box, int height) {
		var secrets = layout.rooms("secret");
		if (secrets.isEmpty()) {
			return null;
		}
		BoxLayout.Room room = secrets.getFirst();
		int floorDy = floorDy(box);
		int chamber = Math.min(6, height - floorDy - 1);
		if (chamber < MIN_CHAMBER || room.x1() - room.x0() < 6 || room.z1() - room.z0() < 6) {
			return null;
		}
		// The four inner corners (the 3×3 sits one in from the rim, so a hollow rim never cuts it); the one farthest
		// from where the tunnel comes in keeps the way in clear.
		int best = -1;
		long bestScore = Long.MIN_VALUE;
		int[][] corners = {{room.x0() + 2, room.z0() + 2}, {room.x0() + 2, room.z1() - 2}, {room.x1() - 2, room.z0() + 2}, {room.x1() - 2, room.z1() - 2}};
		for (int i = 0; i < corners.length; i++) {
			long score = Long.MAX_VALUE;
			for (int x = room.x0(); x <= room.x1(); x++) {
				for (int z = room.z0(); z <= room.z1(); z++) {
					if (entry(layout, room, x, z)) {
						long dx = x - corners[i][0];
						long dz = z - corners[i][1];
						score = Math.min(score, dx * dx + dz * dz);
					}
				}
			}
			if (score > bestScore) {
				bestScore = score;
				best = i;
			}
		}
		int sx = corners[best][0];
		int sz = corners[best][1];
		// The first step is the ring's corner cell facing into the room (the room's centre side on both axes).
		int ix = Integer.signum(room.cx() - sx);
		int iz = Integer.signum(room.cz() - sz);
		int start = 0;
		for (int i = 0; i < RING.length; i++) {
			if (RING[i][0] == ix && RING[i][1] == iz) {
				start = i;
			}
		}
		return new VaultSecret(room, sx, sz, start, floorDy, chamber);
	}

	/** A rim column of the room that is open onto an open column outside it (where the tunnel comes in). */
	private static boolean entry(BoxLayout layout, BoxLayout.Room room, int x, int z) {
		if (!(x == room.x0() || x == room.x1() || z == room.z0() || z == room.z1()) || !layout.open(x, z)) {
			return false;
		}
		for (int[] d : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
			if (!room.contains(x + d[0], z + d[1]) && layout.open(x + d[0], z + d[1])) {
				return true;
			}
		}
		return false;
	}

	/** Steps from the floor to the chamber. */
	public int steps() {
		return floorDy;
	}

	/** Step {@code k} (1..{@link #steps}) as {@code {x, dy, z}}. */
	public int[] step(int k) {
		int[] ring = RING[(start + k - 1) % RING.length];
		return new int[] {shaftX + ring[0], k, shaftZ + ring[1]};
	}

	/** Whether a column is in the 3×3 shaft. */
	public boolean inShaft(int x, int z) {
		return Math.abs(x - shaftX) <= 1 && Math.abs(z - shaftZ) <= 1;
	}

	/** Where the secret mini-boss stands, on the chamber floor: across the room's centre from the stair (never over it). */
	public int[] bossSpot() {
		return new int[] {2 * room.cx() - shaftX, floorDy + 1, 2 * room.cz() - shaftZ};
	}
}
