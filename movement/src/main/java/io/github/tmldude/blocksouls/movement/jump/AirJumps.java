package io.github.tmldude.blocksouls.movement.jump;

/** Counts mid-air jumps ({@code DOUBLE_JUMP}) since the player last stood on the ground. Pure. */
public final class AirJumps {
	private int used;

	/** Call every tick; landing gives every air jump back. */
	public void tick(boolean onGround) {
		if (onGround) {
			used = 0;
		}
	}

	/** Spends an air jump if one of {@code allowed} is left. */
	public boolean tryUse(int allowed) {
		if (used >= allowed) {
			return false;
		}
		used++;
		return true;
	}

	public int used() {
		return used;
	}

	public void reset() {
		used = 0;
	}
}
