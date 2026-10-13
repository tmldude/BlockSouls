package io.github.tmldude.blocksouls.common.party;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Floor 6's party curves: the damage rush's window, the phase-2 adds and the arena hazards, at 1 and 8 players. */
class ArenaPartyScalingTest {
	@AfterEach
	void restore() {
		PartyScaling.setConfig(new PartyConfig());
	}

	@Test
	void soloIsOne() {
		assertEquals(1.0, PartyScaling.rushWindow(1), 1e-9);
		assertEquals(1.0, PartyScaling.arenaAdds(1), 1e-9);
		assertEquals(1.0, PartyScaling.arenaHazards(1), 1e-9);
	}

	@Test
	void atEight() {
		assertEquals(1 - 0.025 * Math.pow(7, 0.9), PartyScaling.rushWindow(8), 1e-9);
		assertTrue(PartyScaling.rushWindow(8) < 1, "a full party gets a shorter rush window");
		assertEquals(1 + 0.45 * Math.pow(7, 0.9), PartyScaling.arenaAdds(8), 1e-9);
		assertEquals(1 + 0.25 * Math.pow(7, 0.9), PartyScaling.arenaHazards(8), 1e-9);
		assertEquals(PartyScaling.arenaAdds(8), PartyScaling.arenaAdds(20), 1e-9, "clamped at maxParty");
	}

	@Test
	void rushWindowNeverCollapses() {
		PartyConfig config = new PartyConfig();
		config.rushWindow = new PartyConfig.Curve(-1, 1);
		PartyScaling.setConfig(config);
		assertEquals(0.25, PartyScaling.rushWindow(8), 1e-9);
	}
}
