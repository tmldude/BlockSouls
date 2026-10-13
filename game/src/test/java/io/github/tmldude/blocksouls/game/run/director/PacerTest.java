package io.github.tmldude.blocksouls.game.run.director;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import io.github.tmldude.blocksouls.common.party.PartyConfig;
import io.github.tmldude.blocksouls.common.party.PartyScaling;

class PacerTest {
	private final DirectorConfig config = new DirectorConfig();

	@AfterEach
	void restore() {
		PartyScaling.setConfig(new PartyConfig());
	}

	@Test
	void batteredPartyGetsALullCappedByTheCeiling() {
		config.levers.lullSeconds = 60;
		config.pacing.lullCeilingSeconds = 20;
		Pacer pacer = new Pacer();
		Pacer.Change change = pacer.update(0, 0.95, 1, config.levers, config.pacing);
		assertNotNull(change);
		assertEquals(Pacer.Mood.LULL, change.to());
		assertEquals(20, change.seconds(), "the lull ceiling holds");
		for (int t = 0; t < 40; t++) {
			pacer.tick();
		}
		assertEquals(0, pacer.take(10), "nothing spawns in a lull");
		pacer.update(19, 0.95, 1, config.levers, config.pacing);
		assertEquals(Pacer.Mood.LULL, pacer.mood());
		pacer.update(20, 0.95, 1, config.levers, config.pacing);
		assertEquals(Pacer.Mood.BUILD, pacer.mood(), "the lull ends at the ceiling even under stress");
	}

	@Test
	void cruisingPartyGetsASpike() {
		Pacer pacer = new Pacer();
		Pacer.Change change = pacer.update(0, 0.05, 1, config.levers, config.pacing);
		assertEquals(Pacer.Mood.SPIKE, change.to());
		int released = pacer.take(100);
		assertEquals((int) Math.floor(config.levers.burstSize * config.levers.spikeBurstMultiplier), released);
		pacer.update(1, 0.5, 1, config.levers, config.pacing);
		assertEquals(Pacer.Mood.BUILD, pacer.mood());
	}

	@Test
	void burstAndRateScaleWithParty() {
		Pacer solo = new Pacer();
		Pacer eight = new Pacer();
		solo.update(0, 0.5, 1, config.levers, config.pacing);
		eight.update(0, 0.5, 8, config.levers, config.pacing);
		assertTrue(eight.rate() > solo.rate());
		for (int t = 0; t < 400; t++) {
			solo.tick();
			eight.tick();
		}
		assertEquals((int) Math.floor(Pacer.burstSize(1, config.levers)), solo.take(1000), "the bucket caps at one burst");
		assertEquals((int) Math.floor(Pacer.burstSize(8, config.levers)), eight.take(1000));
		assertTrue(Pacer.burstSize(8, config.levers) / 8 < Pacer.burstSize(1, config.levers), "fewer per player at 8");
	}

	@Test
	void batteredSpawnsSlowerThanCalm() {
		Pacer calm = new Pacer();
		Pacer battered = new Pacer();
		config.levers.lullAbove = 2;
		config.levers.spikeBelow = -1;
		calm.update(0, 0, 1, config.levers, config.pacing);
		battered.update(0, 0.7, 1, config.levers, config.pacing);
		assertTrue(calm.rate() > battered.rate());
		assertEquals(config.levers.spawnsPerSecond * config.levers.rateAtCalm, calm.rate(), 1e-9);
	}

	@Test
	void pacingCanEndALull() {
		Pacer pacer = new Pacer();
		pacer.update(0, 0.95, 1, config.levers, config.pacing);
		assertTrue(pacer.endLull(5, 10));
		assertEquals(Pacer.Mood.BUILD, pacer.mood());
	}
}
