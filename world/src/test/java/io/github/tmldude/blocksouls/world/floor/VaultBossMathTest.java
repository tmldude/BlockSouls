package io.github.tmldude.blocksouls.world.floor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import io.github.tmldude.blocksouls.common.party.PartyScaling;

class VaultBossMathTest {
	@Test
	void miniBossHealthFollowsPartyScalingAtOneAndEightPlayers() {
		VaultConfig config = new VaultConfig();
		for (double base : new double[] {config.listenerHealth, config.colossusHealth}) {
			double solo = VaultBossMath.health(base, 1);
			double eight = VaultBossMath.health(base, 8);
			assertEquals(base * PartyScaling.bossHealth(1), solo, 1e-9);
			assertEquals(base * PartyScaling.bossHealth(8), eight, 1e-9);
			assertTrue(solo > 0 && eight > solo, "a party of eight faces a tougher mini-boss than a solo player");
		}
	}

	@Test
	void miniBossHealthClampsThePartySize() {
		assertEquals(VaultBossMath.health(160, 1), VaultBossMath.health(160, 0), 1e-9);
		assertEquals(VaultBossMath.health(160, 8), VaultBossMath.health(160, 20), 1e-9);
		assertEquals(1.0, VaultBossMath.health(0, 4), 1e-9);
	}
}
