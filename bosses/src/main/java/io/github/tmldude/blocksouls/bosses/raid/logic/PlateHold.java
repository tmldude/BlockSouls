package io.github.tmldude.blocksouls.bosses.raid.logic;

/**
 * The hold timer of the grounding plates: fills while enough plates are held and drains at the same rate while they
 * aren't, so stepping off for a moment costs a moment. Completes once full. Pure logic, no Minecraft.
 */
public final class PlateHold {
	private int ticks;

	/**
	 * One tick.
	 *
	 * @param held     plates with someone on them
	 * @param required plates that must be held together
	 * @param needed   ticks of holding to complete
	 * @return true on the tick the hold completes (the timer then resets)
	 */
	public boolean tick(int held, int required, int needed) {
		if (held >= required) {
			ticks++;
		} else if (ticks > 0) {
			ticks--;
		}
		if (ticks >= needed) {
			ticks = 0;
			return true;
		}
		return false;
	}

	/** 0..1 toward completion. */
	public float progress(int needed) {
		return needed <= 0 ? 1f : Math.min(1f, ticks / (float) needed);
	}

	public int ticks() {
		return ticks;
	}

	public void reset() {
		ticks = 0;
	}
}
