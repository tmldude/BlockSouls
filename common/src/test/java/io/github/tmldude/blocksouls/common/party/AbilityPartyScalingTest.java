package io.github.tmldude.blocksouls.common.party;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** The ability curves in party.json: solo is always ×1, eight players is the tuned top. */
class AbilityPartyScalingTest {
	@AfterEach
	void restore() {
		PartyScaling.setConfig(new PartyConfig());
	}

	@Test
	void ultimateChargeSoloIsOne() {
		assertEquals(1.0, PartyScaling.ultimateCharge(1), 1e-9);
		assertEquals(1.0, PartyScaling.ultimateCharge(0), 1e-9);
	}

	@Test
	void ultimateChargeGrowsToEight() {
		double eight = PartyScaling.ultimateCharge(8);
		assertTrue(eight > PartyScaling.ultimateCharge(4), "8 players charge faster than 4");
		assertEquals(1 + 0.25 * Math.pow(7, 0.9), eight, 1e-9);
		assertEquals(eight, PartyScaling.ultimateCharge(12), 1e-9, "clamped at maxParty");
	}

	@Test
	void abilityHealSoloAndEight() {
		assertEquals(1.0, PartyScaling.abilityHeal(1), 1e-9);
		assertEquals(1 + 0.1 * Math.pow(7, 0.9), PartyScaling.abilityHeal(8), 1e-9);
	}

	@Test
	void curvesComeFromConfig() {
		PartyConfig config = new PartyConfig();
		config.ultimateCharge = new PartyConfig.Curve(0, 1);
		config.abilityHeal = new PartyConfig.Curve(1, 1);
		PartyScaling.setConfig(config);
		assertEquals(1.0, PartyScaling.ultimateCharge(8), 1e-9);
		assertEquals(8.0, PartyScaling.abilityHeal(8), 1e-9);
	}
}
