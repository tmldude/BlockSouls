package io.github.tmldude.blocksouls.game.player.rune;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** The run-end rune payout: play well for the bulk of it, keep a minority of what you didn't spend. */
class RunePayoutTest {
	final RunesConfig config = new RunesConfig();

	@Test
	void performanceScalesWithSiegesAndThreat() {
		assertEquals(0, RunePayout.performance(0, 0, config));
		assertEquals(10, RunePayout.performance(1, 0, config), "one siege at threat 0");
		assertEquals(30, RunePayout.performance(2, 10, config), "two sieges at threat 10: 20 × 1.5");
		assertEquals(RunePayout.performance(3, 4, config), RunePayout.performance(3, 4, config), "pure");
	}

	@Test
	void onlyAQuarterOfUnspentSoulsCarries() {
		assertEquals(25, RunePayout.carried(100, config));
		assertEquals(0, RunePayout.carried(3, config), "floored");
		assertEquals(0, RunePayout.carried(-5, config));
		config.unspentShare = 2.0;
		assertEquals(100, RunePayout.carried(100, config), "the share is clamped to 1");
	}

	@Test
	void payoutIsPerformancePlusCarriedWithAFloorOnceASiegeWasSurvived() {
		assertEquals(0, RunePayout.payout(0, 0, 0, config), "no siege survived, nothing spent: nothing");
		assertEquals(5, RunePayout.payout(0, 0, 20, config), "nothing survived but 20 unspent: a quarter");
		assertEquals(10 + 25, RunePayout.payout(1, 0, 100, config));
		config.soulsPerSiege = 1;
		assertEquals(config.minimumPayout, RunePayout.payout(1, 0, 0, config), "a survived siege always pays the minimum");
	}

	@Test
	void hoardingNeverBeatsPlaying() {
		// Spending 100 souls in-run (and surviving one more siege for it) beats keeping them for the payout.
		int kept = RunePayout.payout(2, 5, 100, config);
		int spent = RunePayout.payout(3, 5, 0, config);
		assertEquals(true, spent > kept - 25, "the carried share stays a minority of the performance income");
	}
}
