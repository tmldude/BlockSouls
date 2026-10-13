package io.github.tmldude.blocksouls.abilities.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import io.github.tmldude.blocksouls.common.party.PartyConfig;
import io.github.tmldude.blocksouls.common.party.PartyScaling;

class ChargeMathTest {
	@AfterEach
	void restore() {
		PartyScaling.setConfig(new PartyConfig());
	}

	@Test
	void stackMultiplierCaps() {
		assertEquals(1.0, ChargeMath.stackMultiplier(0, 0.05, 0.5), 1e-9);
		assertEquals(1.15, ChargeMath.stackMultiplier(3, 0.05, 0.5), 1e-9);
		assertEquals(1.5, ChargeMath.stackMultiplier(40, 0.05, 0.5), 1e-9);
		assertEquals(1.0, ChargeMath.stackMultiplier(-2, 0.05, 0.5), 1e-9);
	}

	@Test
	void killKindsMultiply() {
		assertEquals(0.045, ChargeMath.perKill(0.045, false, 2, false, 6, 1, 1), 1e-9);
		assertEquals(0.09, ChargeMath.perKill(0.045, true, 2, false, 6, 1, 1), 1e-9);
		assertEquals(0.27, ChargeMath.perKill(0.045, true, 2, true, 6, 1, 1), 1e-9, "a boss counts as a boss, not an elite");
	}

	@Test
	void killChargeScalesWithPartyAtOneAndEight() {
		double solo = ChargeMath.perKill(0.045, false, 2, false, 6, PartyScaling.ultimateCharge(1), 1);
		double eight = ChargeMath.perKill(0.045, false, 2, false, 6, PartyScaling.ultimateCharge(8), 1);
		assertEquals(0.045, solo, 1e-9, "solo is the base value");
		assertEquals(0.045 * (1 + 0.25 * Math.pow(7, 0.9)), eight, 1e-9, "eight players charge about 2.4× per kill");
		assertTrue(eight > solo * 2 && eight < solo * 3);
	}

	@Test
	void precisionChargeScalesWithParty() {
		assertEquals(0.02, ChargeMath.perPrecision(0.02, PartyScaling.ultimateCharge(1), 1), 1e-9);
		assertEquals(0.02 * PartyScaling.ultimateCharge(8) * 1.2, ChargeMath.perPrecision(0.02, PartyScaling.ultimateCharge(8), 1.2), 1e-9);
	}

	@Test
	void addClampsAndReadyFiresOnce() {
		assertEquals(1.0, ChargeMath.add(0.9, 0.5), 1e-9);
		assertEquals(0.0, ChargeMath.add(0.1, -0.5), 1e-9);
		assertTrue(ChargeMath.becomesReady(0.95, 0.1));
		assertFalse(ChargeMath.becomesReady(1.0, 0.1), "already full");
		assertFalse(ChargeMath.becomesReady(0.5, 0.1));
	}

	@Test
	void killsToFillSoloVersusEight() {
		int solo = killsToFill(PartyScaling.ultimateCharge(1));
		int eight = killsToFill(PartyScaling.ultimateCharge(8));
		assertEquals(23, solo, "solo: 23 kills at 4.5% each");
		assertTrue(eight < solo / 2, "eight players need fewer than half the kills each: " + eight);
	}

	private static int killsToFill(double partyMult) {
		double charge = 0;
		int kills = 0;
		while (charge < 1.0) {
			charge = ChargeMath.add(charge, ChargeMath.perKill(0.045, false, 2, false, 6, partyMult, 1));
			kills++;
		}
		return kills;
	}
}
