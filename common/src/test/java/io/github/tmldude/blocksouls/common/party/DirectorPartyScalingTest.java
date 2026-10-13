package io.github.tmldude.blocksouls.common.party;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** The v2 Director's party curves: solo is "fewer, scarier", eight players get bigger, plainer bursts. */
class DirectorPartyScalingTest {
	@AfterEach
	void restore() {
		PartyScaling.setConfig(new PartyConfig());
	}

	@Test
	void burstAndStressPoolSoloIsOne() {
		assertEquals(1.0, PartyScaling.directorBurst(1), 1e-9);
		assertEquals(1.0, PartyScaling.directorStressPool(1), 1e-9);
	}

	@Test
	void burstAndStressPoolAtEight() {
		assertEquals(1 + 0.45 * Math.pow(7, 0.9), PartyScaling.directorBurst(8), 1e-9);
		assertEquals(1 + 0.4 * Math.pow(7, 0.95), PartyScaling.directorStressPool(8), 1e-9);
		assertEquals(PartyScaling.directorBurst(8), PartyScaling.directorBurst(20), 1e-9, "clamped at maxParty");
	}

	@Test
	void soloIsFewerButScarier() {
		double solo = PartyScaling.directorEliteShare(1);
		double eight = PartyScaling.directorEliteShare(8);
		assertEquals(0.25, solo, 1e-9);
		assertTrue(eight < solo, "a bigger party gets a smaller elite share");
		assertTrue(eight > 0, "but never none");
		// Fewer: the burst per player shrinks as the party grows.
		assertTrue(PartyScaling.directorBurst(8) / 8 < PartyScaling.directorBurst(1));
	}

	@Test
	void eliteShareIsClamped() {
		PartyConfig config = new PartyConfig();
		config.directorSoloEliteShare = 3;
		PartyScaling.setConfig(config);
		assertEquals(1.0, PartyScaling.directorEliteShare(1), 1e-9);
	}
}
