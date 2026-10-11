package io.github.tmldude.blocksouls.common.party;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class PartyScalingTest {
	@AfterEach
	void restoreDefaults() {
		PartyScaling.setConfig(new PartyConfig());
	}

	@Test
	void soloIsAlwaysOne() {
		assertEquals(1.0, PartyScaling.waveSize(1), 1e-9);
		assertEquals(1.0, PartyScaling.bossHealth(1), 1e-9);
		assertEquals(1.0, PartyScaling.elites(1), 1e-9);
		assertEquals(1.0, PartyScaling.fieldBossChance(1), 1e-9);
		// No players (e.g. everyone in the Nether) counts as one.
		assertEquals(1.0, PartyScaling.waveSize(0), 1e-9);
	}

	@Test
	void curvesGrowButEachPlayerAddsLess() {
		for (int n = 2; n <= 8; n++) {
			double gain = PartyScaling.waveSize(n) - PartyScaling.waveSize(n - 1);
			double nextGain = PartyScaling.waveSize(n + 1) - PartyScaling.waveSize(n);
			assertTrue(gain > 0, "grows at " + n);
			if (n < 8) {
				assertTrue(nextGain <= gain + 1e-9, "sublinear at " + n);
			}
		}
		assertEquals(1.55, PartyScaling.waveSize(2), 1e-9);
		assertEquals(1.5, PartyScaling.bossHealth(2), 1e-9);
	}

	@Test
	void partiesAboveTheMaxCountAsTheMax() {
		assertEquals(PartyScaling.waveSize(8), PartyScaling.waveSize(12), 1e-9);
		assertEquals(PartyScaling.aliveCap(8), PartyScaling.aliveCap(20));
	}

	@Test
	void aliveCapScalesAndCaps() {
		assertEquals(17, PartyScaling.aliveCap(1));
		assertEquals(32, PartyScaling.aliveCap(4));
		assertEquals(52, PartyScaling.aliveCap(8));
		PartyConfig config = new PartyConfig();
		config.aliveCapMax = 40;
		PartyScaling.setConfig(config);
		assertEquals(40, PartyScaling.aliveCap(8));
	}

	@Test
	void raidPlatesOnePerTwoPlayersUpToFour() {
		assertEquals(1, PartyScaling.raidPlates(1));
		assertEquals(1, PartyScaling.raidPlates(2));
		assertEquals(2, PartyScaling.raidPlates(3));
		assertEquals(3, PartyScaling.raidPlates(6));
		assertEquals(4, PartyScaling.raidPlates(8));
	}
}
