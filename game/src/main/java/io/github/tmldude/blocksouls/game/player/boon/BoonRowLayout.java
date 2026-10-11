package io.github.tmldude.blocksouls.game.player.boon;

/**
 * Where the HUD's boon row puts each icon: right-aligned against {@code right}, growing leftward, wrapping upward.
 * Columns are limited to what fits between {@code right} and the screen's left margin; if the rows would then climb
 * past {@code top}, columns are added back with a tighter (overlapping) step so the row stays on screen. Pure math so
 * it can be unit-tested at any GUI size.
 */
public final class BoonRowLayout {
	public static final int SLOT = 18;
	public static final int STEP = 20;
	public static final int ROW_STEP = 23;
	public static final int MAX_PER_ROW = 6;
	public static final int MARGIN = 2;

	private BoonRowLayout() {
	}

	/** {x, y} of each of {@code count} slots. {@code bottom} is the slots' bottom edge, {@code top} the highest a slot may start. */
	public static int[][] place(int count, int right, int bottom, int top) {
		int[][] slots = new int[count][];
		if (count == 0) {
			return slots;
		}
		int room = Math.max(0, right - SLOT - MARGIN);
		int columns = Math.clamp(room / STEP + 1, 1, MAX_PER_ROW);
		int maxRows = Math.max(1, (bottom - SLOT - top) / ROW_STEP + 1);
		if (ceilDiv(count, columns) > maxRows) {
			columns = ceilDiv(count, maxRows);
		}
		int step = columns > 1 ? Math.clamp(room / (columns - 1), 1, STEP) : STEP;
		for (int i = 0; i < count; i++) {
			int x = Math.max(MARGIN, right - SLOT - (i % columns) * step);
			int y = bottom - SLOT - (i / columns) * ROW_STEP;
			slots[i] = new int[] {x, y};
		}
		return slots;
	}

	private static int ceilDiv(int a, int b) {
		return (a + b - 1) / b;
	}
}
