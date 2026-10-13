package io.github.tmldude.blocksouls.game.player.summary;

/**
 * Where the run summary cards go (pure, so it's tested at 1 and 8 players): up to {@code columns} per row, rows
 * centered, cards shrink to fit the screen width but never below {@link #MIN_CARD_WIDTH}.
 */
public final class SummaryLayout {
	public static final int MIN_CARD_WIDTH = 96;
	public static final int MAX_CARD_WIDTH = 150;
	public static final int GAP = 8;

	private SummaryLayout() {
	}

	public record Slot(int x, int y, int width, int height) {
	}

	public static int columns(int cards, int maxColumns) {
		return Math.max(1, Math.min(Math.max(1, cards), Math.max(1, maxColumns)));
	}

	public static int rows(int cards, int maxColumns) {
		int columns = columns(cards, maxColumns);
		return Math.max(1, (Math.max(1, cards) + columns - 1) / columns);
	}

	public static int cardWidth(int screenWidth, int cards, int maxColumns) {
		int columns = columns(cards, maxColumns);
		int fit = (screenWidth - GAP * (columns + 1)) / columns;
		return Math.max(MIN_CARD_WIDTH, Math.min(MAX_CARD_WIDTH, fit));
	}

	/** The slot of card {@code index}; the last row is centered when it isn't full. */
	public static Slot slot(int index, int cards, int maxColumns, int screenWidth, int top, int cardHeight) {
		int columns = columns(cards, maxColumns);
		int width = cardWidth(screenWidth, cards, maxColumns);
		int row = index / columns;
		int inRow = Math.min(columns, cards - row * columns);
		int rowWidth = inRow * width + (inRow - 1) * GAP;
		int x = (screenWidth - rowWidth) / 2 + (index % columns) * (width + GAP);
		int y = top + row * (cardHeight + GAP);
		return new Slot(x, y, width, cardHeight);
	}
}
