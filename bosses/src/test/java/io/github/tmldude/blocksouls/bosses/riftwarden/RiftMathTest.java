package io.github.tmldude.blocksouls.bosses.riftwarden;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import io.github.tmldude.blocksouls.common.party.PartyConfig;
import io.github.tmldude.blocksouls.common.party.PartyScaling;

/** The Riftwarden's adds, fissures and damage rush at 1 and 8 players (shipped defaults: 3 adds, 2 fissures, 60 s). */
class RiftMathTest {
	private final RiftwardenConfig config = new RiftwardenConfig();

	@AfterEach
	void restore() {
		PartyScaling.setConfig(new PartyConfig());
	}

	@Test
	void soloGetsTheBaseCounts() {
		assertEquals(config.summon.adds, RiftMath.adds(config.summon.adds, 1, config.maxAdds));
		assertEquals(config.fissures, RiftMath.fissures(config.fissures, 1, config.maxFissures));
	}

	@Test
	void fullPartyGetsMoreWithinTheCaps() {
		int adds = RiftMath.adds(config.summon.adds, 8, config.maxAdds);
		int fissures = RiftMath.fissures(config.fissures, 8, config.maxFissures);
		assertEquals(Math.round(config.summon.adds * PartyScaling.arenaAdds(8)), adds); // 3 × 3.6 → 11
		assertEquals(Math.round(config.fissures * PartyScaling.arenaHazards(8)), fissures); // 2 × 2.4 → 5
		assertTrue(adds <= config.maxAdds && fissures <= config.maxFissures);
		assertTrue(adds > config.summon.adds && fissures > config.fissures);
	}

	@Test
	void countsStayBetweenOneAndTheCap() {
		assertEquals(4, RiftMath.adds(30, 8, 4));
		assertEquals(1, RiftMath.fissures(0, 1, 8));
		assertEquals(1, RiftMath.scaled(5, 2, 0), "a zero cap still leaves one");
	}

	private int rush(int players, int rush) {
		return RiftMath.rushTicks(config.rushSeconds, players, rush, config.rushShrink, config.minRushSeconds);
	}

	@Test
	void soloFirstRushIsTheFullWindow() {
		assertEquals(20 * 60, rush(1, 1));
		assertEquals(Math.round(20 * 60 * 0.8), rush(1, 2), "each later rush is shrink × shorter");
	}

	@Test
	void fullPartyGetsAShorterWindowButNeverUnderTheFloor() {
		int solo = rush(1, 1);
		int eight = rush(8, 1);
		assertEquals(Math.round(20 * config.rushSeconds * PartyScaling.rushWindow(8)), eight);
		assertTrue(eight < solo && eight > solo / 2, "8 players: " + eight + " vs solo " + solo);
		for (int players : new int[] {1, 8}) {
			for (int n = 1; n <= 10; n++) {
				assertTrue(rush(players, n) >= 20 * config.minRushSeconds, players + "p rush " + n);
				assertTrue(n == 1 || rush(players, n) <= rush(players, n - 1), "rushes never lengthen");
			}
		}
	}

	@Test
	void rushIsWonAtTheFractionOfMaxHealth() {
		double max = 3240; // about 8 players' health
		assertTrue(RiftMath.rushWon(max * config.rushFraction, max, config.rushFraction));
		assertTrue(!RiftMath.rushWon(max * config.rushFraction - 1, max, config.rushFraction));
		assertTrue(RiftMath.rushWon(config.health * config.rushFraction, config.health, config.rushFraction), "solo too");
		assertTrue(config.rushFraction < config.phase3Threshold, "a won rush must come before the health runs out");
	}

	@Test
	void theLastTimedRushIsMaxRushes() {
		assertTrue(!RiftMath.lastRush(config.maxRushes - 1, config.maxRushes));
		assertTrue(RiftMath.lastRush(config.maxRushes, config.maxRushes));
		assertTrue(RiftMath.lastRush(1, 0), "a zero setting still allows one rush");
	}

	@Test
	void aFailedRushHealsBackIntoPhase2() {
		assertTrue(config.rushFailHealShare > config.phase3Threshold && config.rushFailHealShare < config.phase2Threshold);
	}
}
