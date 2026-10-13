package io.github.tmldude.blocksouls.world.floor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.github.tmldude.blocksouls.common.party.PartyConfig;
import io.github.tmldude.blocksouls.common.party.PartyScaling;

/** Skyrift's mini-bosses scale with the party: a solo Roc and Matriarch pack, and the same at 8 players. */
class SkyBossMathTest {
	private final FloorsConfig config = new FloorsConfig();

	@BeforeEach
	void defaults() {
		PartyScaling.setConfig(new PartyConfig());
	}

	@Test
	void soloKeepsTheBaseNumbers() {
		assertEquals(config.rocHealth, SkyBossMath.health(config.rocHealth, 1), 1e-6);
		assertEquals(config.matriarchHealth, SkyBossMath.health(config.matriarchHealth, 1), 1e-6);
		assertEquals(config.gliderPack, SkyBossMath.gliderPack(config.gliderPack, 1));
	}

	@Test
	void eightPlayersMeetTougherBossesAndBiggerPacks() {
		double roc = SkyBossMath.health(config.rocHealth, 8);
		assertEquals(config.rocHealth * PartyScaling.bossHealth(8), roc, 1e-6);
		assertTrue(roc > config.rocHealth * 2, "an 8-player Roc has well over twice the solo health, got " + roc);
		assertTrue(SkyBossMath.health(config.matriarchHealth, 8) > config.matriarchHealth);
		int pack = SkyBossMath.gliderPack(config.gliderPack, 8);
		assertTrue(pack > config.gliderPack, "an 8-player Matriarch calls more gliders, got " + pack);
	}

	@Test
	void neverBelowOne() {
		assertEquals(1, SkyBossMath.gliderPack(0, 1));
		assertEquals(1.0, SkyBossMath.health(0, 1), 1e-6);
	}
}
