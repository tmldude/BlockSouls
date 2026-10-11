package io.github.tmldude.blocksouls.common.run;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import io.github.tmldude.blocksouls.common.party.PartyConfig;
import io.github.tmldude.blocksouls.common.party.PartyScaling;

class ThreatTest {
	@AfterEach
	void restoreDefaults() {
		Threat.setConfig(new ThreatConfig());
		PartyScaling.setConfig(new PartyConfig());
	}

	@Test
	void threatZeroIsAlwaysOne() {
		for (Threat.Kind kind : Threat.Kind.values()) {
			assertEquals(1.0, Threat.scale(kind, 0), 1e-9, kind.name());
			assertEquals(1.0, Threat.scale(kind, -3), 1e-9, kind.name() + " below zero");
		}
	}

	@Test
	void bossCurvesGrowButEachThreatPointAddsLess() {
		for (Threat.Kind kind : new Threat.Kind[] {Threat.Kind.BOSS_HEALTH, Threat.Kind.BOSS_DAMAGE}) {
			for (int t = 1; t <= 30; t++) {
				double gain = Threat.scale(kind, t) - Threat.scale(kind, t - 1);
				double nextGain = Threat.scale(kind, t + 1) - Threat.scale(kind, t);
				assertTrue(gain > 0, kind + " grows at " + t);
				assertTrue(nextGain <= gain + 1e-9, kind + " sublinear at " + t);
			}
		}
	}

	@Test
	void curvesCapAtMax() {
		ThreatConfig config = new ThreatConfig();
		config.bossHealth = new ThreatConfig.Curve(1.0, 1.0, 4.0);
		Threat.setConfig(config);
		assertEquals(3.0, Threat.bossHealth(2), 1e-9);
		assertEquals(4.0, Threat.bossHealth(10), 1e-9, "capped");
		assertEquals(4.0, Threat.bossHealth(1000), 1e-9, "stays capped");
	}

	@Test
	void uncappedCurveKeepsGrowing() {
		ThreatConfig config = new ThreatConfig();
		config.bossDamage = new ThreatConfig.Curve(0.1, 1.0, 0);
		Threat.setConfig(config);
		assertEquals(11.0, Threat.bossDamage(100), 1e-9);
	}

	@Test
	void waveSizeIsNeutralByDefault() {
		// The classic wave formula already grows with the counter; a non-1 default would double-scale sieges.
		for (int t = 0; t <= 50; t += 10) {
			assertEquals(1.0, Threat.waveSize(t), 1e-9, "threat " + t);
		}
	}

	@Test
	void threatMultipliesWithPartyScalingInsteadOfReplacingIt() {
		// A boss engaged at threat 20: the two factors stack, and solo party scaling stays 1.
		double threatFactor = Threat.bossHealth(20);
		assertTrue(threatFactor > 1.0 && threatFactor < 3.0, "default curve is modest: " + threatFactor);
		double solo = threatFactor * PartyScaling.bossHealth(1);
		double eight = threatFactor * PartyScaling.bossHealth(8);
		assertEquals(threatFactor, solo, 1e-9, "solo adds nothing on top of threat");
		assertEquals(PartyScaling.bossHealth(8), eight / threatFactor, 1e-9, "party factor unchanged by threat");
		assertTrue(eight > solo, "8 players outscale solo at equal threat");
	}

	@Test
	void defaultBossCurvesStayModestAcrossARun() {
		// ~2.5 h median run ends around threat 20–25; the finale shouldn't be a stat wall.
		assertTrue(Threat.bossHealth(25) < 2.0, "health at threat 25: " + Threat.bossHealth(25));
		assertTrue(Threat.bossDamage(25) < 1.8, "damage at threat 25: " + Threat.bossDamage(25));
		// The caps hold for marathon endless runs.
		assertEquals(3.0, Threat.bossHealth(100000), 1e-9);
		assertEquals(2.5, Threat.bossDamage(100000), 1e-9);
	}
}
