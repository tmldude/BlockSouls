package io.github.tmldude.blocksouls.game.run.director;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import io.github.tmldude.blocksouls.common.party.PartyConfig;
import io.github.tmldude.blocksouls.common.party.PartyScaling;

class StressModelTest {
	private final DirectorConfig.Stress config = new DirectorConfig.Stress();

	@AfterEach
	void restore() {
		PartyScaling.setConfig(new PartyConfig());
	}

	private StressModel.Reading score(int players, double damage, int downs, int ammo, double dpsRecent, double dpsEarlier, int boons, int threat) {
		return StressModel.score(new StressModel.Inputs(players, damage, downs, ammo, dpsRecent, dpsEarlier, boons, threat), config);
	}

	@Test
	void calmPartyReadsZero() {
		StressModel.Reading solo = score(1, 0, 0, 500, 10, 10, 10, 2);
		StressModel.Reading eight = score(8, 0, 0, 4000, 80, 80, 80, 2);
		assertEquals(0, solo.stress(), 1e-9);
		assertEquals(0, eight.stress(), 1e-9);
	}

	@Test
	void sameDamageStressesSoloMoreThanEight() {
		StressModel.Reading solo = score(1, 30, 0, 500, 10, 10, 10, 2);
		StressModel.Reading eight = score(8, 30, 0, 4000, 80, 80, 80, 2);
		assertEquals(1.0, solo.damage(), 1e-9, "a solo player losing the whole pool is full damage stress");
		assertEquals(1.0 / PartyScaling.directorStressPool(8), eight.damage(), 1e-9);
		assertTrue(solo.stress() > eight.stress());
	}

	@Test
	void downsScaleWithParty() {
		StressModel.Reading solo = score(1, 0, 1, 500, 10, 10, 10, 2);
		StressModel.Reading eight = score(8, 0, 1, 4000, 80, 80, 80, 2);
		assertEquals(1.0, solo.downs(), 1e-9);
		assertEquals(0.25, eight.downs(), 1e-9);
		assertTrue(solo.stress() >= 0.8, "a solo down is near-full stress");
		assertTrue(eight.stress() < 0.2, "one down among eight is a nudge");
	}

	@Test
	void ammoTrendAndPowerSignals() {
		StressModel.Reading dry = score(1, 0, 0, 0, 10, 10, 10, 2);
		assertEquals(1.0, dry.ammo(), 1e-9);
		StressModel.Reading eightDry = score(8, 0, 0, 96 * 4, 10, 10, 80, 2);
		assertEquals(0.5, eightDry.ammo(), 1e-9);
		StressModel.Reading fading = score(1, 0, 0, 500, 5, 10, 10, 2);
		assertEquals(0.5, fading.dps(), 1e-9);
		StressModel.Reading weak = score(8, 0, 0, 4000, 10, 10, 0, 5);
		assertEquals(1.0, weak.power(), 1e-9);
		assertEquals(0.0, score(1, 0, 0, 500, 10, 10, 2, 5).power(), 1e-9);
	}

	@Test
	void smoothingMovesTowardRaw() {
		assertEquals(0.25, StressModel.smooth(0, 1, 0.25), 1e-9);
		assertEquals(1.0, StressModel.smooth(0, 1, 5), 1e-9);
		assertEquals(0.5, StressModel.smooth(0.5, 0.5, 0.3), 1e-9);
	}
}
