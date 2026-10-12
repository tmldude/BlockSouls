package io.github.tmldude.blocksouls.game.player.downed;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class LastStandRuleTest {
	@Test
	void soloMeansExactlyOnePlayerCounts() {
		assertTrue(LastStandRule.isSolo(1));
		assertFalse(LastStandRule.isSolo(2));
		assertFalse(LastStandRule.isSolo(8), "a full party revives each other instead");
		assertFalse(LastStandRule.isSolo(0));
	}

	@Test
	void startsOncePerSiegeForSoloOnly() {
		assertTrue(LastStandRule.canStart(true, true, false, true));
		assertFalse(LastStandRule.canStart(true, true, true, true), "spent this siege");
		assertTrue(LastStandRule.canStart(true, true, true, false), "unless once-per-siege is off");
		assertFalse(LastStandRule.canStart(true, false, false, true), "never for a party");
		assertFalse(LastStandRule.canStart(false, true, false, true), "disabled");
	}

	@Test
	void threeKillsBeforeTheTimerRevives() {
		LastStandRule.Timer timer = new LastStandRule.Timer(400, 3);
		for (int i = 0; i < 100; i++) {
			assertFalse(timer.tick());
		}
		assertFalse(timer.onKill());
		assertFalse(timer.onKill());
		assertTrue(timer.onKill(), "the third kill is the self-revive");
		assertTrue(timer.revives());
		assertFalse(timer.onKill(), "later kills change nothing");
		assertEquals(3, timer.kills());
	}

	@Test
	void theTimerRunningOutExpiresOnce() {
		LastStandRule.Timer timer = new LastStandRule.Timer(3, 3);
		assertFalse(timer.tick());
		assertFalse(timer.tick());
		assertTrue(timer.tick(), "the tick it hits zero");
		assertTrue(timer.expired());
		assertFalse(timer.tick(), "only reported once");
		assertFalse(timer.onKill(), "too late");
		assertEquals(0, timer.ticksLeft());
	}
}
