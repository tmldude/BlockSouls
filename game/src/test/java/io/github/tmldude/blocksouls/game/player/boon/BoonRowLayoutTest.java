package io.github.tmldude.blocksouls.game.player.boon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class BoonRowLayoutTest {
	/** The HUD's numbers: left of the offhand slot, 2 px above the bottom, below the run info. */
	private static int[][] place(int count, int guiWidth, int guiHeight) {
		return BoonRowLayout.place(count, guiWidth / 2 - 91 - 29 - 4, guiHeight - 2, 40);
	}

	private static void assertOnScreen(int count, int guiWidth, int guiHeight) {
		int right = guiWidth / 2 - 91 - 29 - 4;
		int[][] slots = place(count, guiWidth, guiHeight);
		assertEquals(count, slots.length);
		for (int[] slot : slots) {
			String where = count + " boons at " + guiWidth + "x" + guiHeight + ": slot at " + slot[0] + "," + slot[1];
			assertTrue(slot[0] >= BoonRowLayout.MARGIN, where + " is off the left edge");
			assertTrue(slot[0] + BoonRowLayout.SLOT <= Math.max(right, BoonRowLayout.MARGIN + BoonRowLayout.SLOT), where + " runs into the hotbar");
			assertTrue(slot[1] >= 40, where + " climbs into the top HUD");
			assertTrue(slot[1] + BoonRowLayout.SLOT <= guiHeight - 2, where + " is off the bottom");
		}
	}

	@Test
	void staysOnScreenAtEveryGuiScale() {
		// 854x480 (the client test) at GUI scale 1 and 2, 1080p at 1-4, and the smallest GUI vanilla allows.
		int[][] sizes = {{854, 480}, {427, 240}, {1920, 1080}, {960, 540}, {640, 360}, {480, 270}, {320, 240}};
		for (int[] size : sizes) {
			for (int count = 0; count <= 24; count++) {
				assertOnScreen(count, size[0], size[1]);
			}
		}
	}

	@Test
	void roomyScreenKeepsSixPerRowAtFullSpacing() {
		int[][] slots = place(8, 1920, 1080);
		int right = 1920 / 2 - 91 - 29 - 4;
		assertEquals(right - BoonRowLayout.SLOT, slots[0][0], "first slot hugs the offhand side");
		assertEquals(BoonRowLayout.STEP, slots[0][0] - slots[1][0]);
		assertEquals(slots[0][0], slots[6][0], "seventh boon wraps to a new row");
		assertEquals(BoonRowLayout.ROW_STEP, slots[0][1] - slots[6][1]);
	}

	@Test
	void narrowScreenWrapsInsteadOfOverflowing() {
		// 854x480 at GUI scale 2: room for four columns left of the offhand slot (it used to start at x = -29).
		int[][] slots = place(8, 427, 240);
		assertEquals(slots[0][0], slots[4][0], "fifth boon wraps");
		assertTrue(slots[3][0] >= BoonRowLayout.MARGIN);
	}
}
