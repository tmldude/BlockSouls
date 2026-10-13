package io.github.tmldude.blocksouls.game.run.director;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class StressWindowTest {
	@Test
	void damageAndDownsSumOverTheWindowThenAgeOut() {
		StressWindow window = new StressWindow(4);
		window.addDamage(5);
		window.addDown();
		window.roll();
		window.addDamage(3);
		window.addDamage(-2); // ignored
		assertEquals(8, window.damage(), 1e-9);
		assertEquals(1, window.downs());
		for (int i = 0; i < 3; i++) {
			window.roll();
		}
		// The first second (5 damage, 1 down) has left the 4-second window.
		assertEquals(3, window.damage(), 1e-9);
		assertEquals(0, window.downs());
		window.roll();
		assertEquals(0, window.damage(), 1e-9);
	}

	@Test
	void dpsSplitsTheWindowIntoRecentAndEarlierHalves() {
		StressWindow window = new StressWindow(4);
		window.addDealt(40); // will be 3 seconds old: the earlier half
		window.roll();
		window.roll();
		window.addDealt(10); // 1 second old: the recent half
		window.roll();
		assertEquals(5, window.dpsRecent(), 1e-9);
		assertEquals(20, window.dpsEarlier(), 1e-9);
	}

	@Test
	void resizeKeepsAtLeastTwoSecondsAndClears() {
		StressWindow window = new StressWindow(30);
		window.addDamage(10);
		window.resize(0);
		assertEquals(2, window.seconds());
		assertEquals(0, window.damage(), 1e-9);
	}
}
