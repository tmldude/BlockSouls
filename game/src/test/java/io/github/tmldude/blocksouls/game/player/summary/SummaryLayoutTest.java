package io.github.tmldude.blocksouls.game.player.summary;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SummaryLayoutTest {
	private static final int WIDTH = 640;
	private static final int HEIGHT = 132;

	@Test
	void soloIsOneCenteredCard() {
		assertEquals(1, SummaryLayout.columns(1, 4));
		assertEquals(1, SummaryLayout.rows(1, 4));
		SummaryLayout.Slot slot = SummaryLayout.slot(0, 1, 4, WIDTH, 40, HEIGHT);
		assertEquals(WIDTH / 2, slot.x() + slot.width() / 2, 1, "centered");
		assertEquals(SummaryLayout.MAX_CARD_WIDTH, slot.width(), "a lone card takes the max width");
	}

	@Test
	void eightPlayersAreTwoFullRowsOfFour() {
		assertEquals(4, SummaryLayout.columns(8, 4));
		assertEquals(2, SummaryLayout.rows(8, 4));
		for (int i = 0; i < 8; i++) {
			SummaryLayout.Slot slot = SummaryLayout.slot(i, 8, 4, WIDTH, 40, HEIGHT);
			assertEquals(40 + (i / 4) * (HEIGHT + SummaryLayout.GAP), slot.y(), "row of card " + i);
			assertTrue(slot.x() >= 0 && slot.x() + slot.width() <= WIDTH, "card " + i + " on screen");
			if (i % 4 > 0) {
				SummaryLayout.Slot left = SummaryLayout.slot(i - 1, 8, 4, WIDTH, 40, HEIGHT);
				assertTrue(slot.x() >= left.x() + left.width() + SummaryLayout.GAP, "no overlap at " + i);
			}
		}
	}

	@Test
	void unevenLastRowIsCentered() {
		SummaryLayout.Slot first = SummaryLayout.slot(4, 5, 4, WIDTH, 40, HEIGHT);
		assertEquals(WIDTH / 2, first.x() + first.width() / 2, 1, "the fifth card sits centered on row two");
	}

	@Test
	void narrowScreensClampToTheMinimumWidth() {
		assertEquals(SummaryLayout.MIN_CARD_WIDTH, SummaryLayout.cardWidth(200, 8, 4));
		assertEquals(1, SummaryLayout.columns(0, 4), "no cards still lays out one column");
		assertEquals(1, SummaryLayout.columns(3, 0), "a bad column count falls back to one");
	}
}
