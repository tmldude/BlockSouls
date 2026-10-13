package io.github.tmldude.blocksouls.bosses.riftwarden;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import io.github.tmldude.blocksouls.common.party.PartyConfig;
import io.github.tmldude.blocksouls.common.party.PartyScaling;

/** The Riftwarden's adds and fissures at 1 and 8 players (shipped defaults: 3 adds, 2 fissures). */
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
}
