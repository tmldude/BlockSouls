package io.github.tmldude.blocksouls.bosses.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import io.github.tmldude.blocksouls.bosses.BossesConfig;
import io.github.tmldude.blocksouls.common.party.PartyScaling;

class PostureMathTest {
	private static final BossesConfig.Posture C = new BossesConfig().posture;

	@Test
	void maxPoiseScalesWithThePartyLikeHealth() {
		double solo = PostureMath.maxPoise(C.basePoise, 1);
		double full = PostureMath.maxPoise(C.basePoise, 8);
		assertEquals(C.basePoise * PartyScaling.bossHealth(1), solo, 1e-9);
		assertEquals(C.basePoise * PartyScaling.bossHealth(8), full, 1e-9);
		assertTrue(full > solo, "8 players need more poise damage than 1");
		assertEquals(solo, PostureMath.maxPoise(C.basePoise, 0), 1e-9, "an empty fight counts as one player");
	}

	@Test
	void soloCanBreakPostureInAReasonableNumberOfPrecisionHits() {
		// A solo player landing a precision hit every second (hand cannon ~12 damage) breaks posture before decay wins.
		double max = PostureMath.maxPoise(C.basePoise, 1);
		double poise = 0;
		int hits = 0;
		while (!PostureMath.broken(poise, max) && hits < 100) {
			poise = PostureMath.gain(poise, PostureMath.precisionHit(12, C.precisionHitPoise, C.precisionDamagePoise), max);
			hits++;
		}
		assertTrue(hits <= 15, "solo needs " + hits + " precision hits");
		assertTrue(hits >= 5, "posture shouldn't break almost at once (" + hits + " hits)");
	}

	@Test
	void eightPlayersBreakPostureNoFasterPerPlayerThanSolo() {
		double perHit = PostureMath.precisionHit(12, C.precisionHitPoise, C.precisionDamagePoise);
		double soloHits = PostureMath.maxPoise(C.basePoise, 1) / perHit;
		double partyHitsEach = PostureMath.maxPoise(C.basePoise, 8) / perHit / 8;
		assertTrue(partyHitsEach < soloHits, "a party still breaks it sooner together");
		assertTrue(partyHitsEach * 8 >= soloHits, "but needs more hits in total");
	}

	@Test
	void gainCapsAndIgnoresNegatives() {
		assertEquals(100, PostureMath.gain(90, 50, 100), 1e-9);
		assertEquals(40, PostureMath.gain(40, -5, 100), 1e-9);
		assertTrue(PostureMath.broken(100, 100));
		assertFalse(PostureMath.broken(99, 100));
	}

	@Test
	void decayWaitsForTheDelay() {
		assertEquals(50, PostureMath.decay(50, C.decayDelayTicks - 1, C.decayDelayTicks, C.decayPerTick), 1e-9);
		assertEquals(50 - C.decayPerTick, PostureMath.decay(50, C.decayDelayTicks, C.decayDelayTicks, C.decayPerTick), 1e-9);
		assertEquals(0, PostureMath.decay(0.1, 999, C.decayDelayTicks, 5), 1e-9);
	}

	@Test
	void rollTimingWindow() {
		int lead = C.rollLeadTicks;
		assertTrue(PostureMath.wellTimedRoll(lead, 5, lead), "just inside the lead");
		assertFalse(PostureMath.wellTimedRoll(lead + 1, 5, lead), "too early");
		assertTrue(PostureMath.wellTimedRoll(0, 5, lead), "as hitboxes go live");
		assertTrue(PostureMath.wellTimedRoll(-4, 5, lead), "during the active part");
		assertFalse(PostureMath.wellTimedRoll(-5, 5, lead), "after the active part");
		assertFalse(PostureMath.wellTimedRoll(Integer.MIN_VALUE / 2, 5, lead), "recovery / no attack");
	}
}
