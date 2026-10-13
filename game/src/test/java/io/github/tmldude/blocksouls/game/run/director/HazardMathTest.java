package io.github.tmldude.blocksouls.game.run.director;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import io.github.tmldude.blocksouls.common.party.PartyConfig;
import io.github.tmldude.blocksouls.common.party.PartyScaling;

/** Floor hazards (v2-floor5-blight): storm drift, party scaling at 1 and 8, the problem pick and the blight defaults. */
class HazardMathTest {
	@AfterEach
	void restore() {
		PartyScaling.setConfig(new PartyConfig());
	}

	@Test
	void stormAndSurgeScaleWithTheParty() {
		PartyScaling.setConfig(new PartyConfig());
		assertEquals(9, HazardMath.stormRadius(9, 1), 1e-9);
		assertTrue(HazardMath.stormRadius(9, 8) > HazardMath.stormRadius(9, 1));
		assertEquals(9 * PartyScaling.floorSize(8), HazardMath.stormRadius(9, 8), 1e-9);
		assertEquals(Math.round(6 * PartyScaling.directorBurst(1)), HazardMath.surgePack(6, 1));
		assertTrue(HazardMath.surgePack(6, 8) > HazardMath.surgePack(6, 1));
		assertEquals(1, HazardMath.surgePack(0, 1), "a surge always sends someone");
	}

	@Test
	void stormDriftsToItsTargetAndStaysOnTheIsland() {
		HazardMath.Storm storm = new HazardMath.Storm(0, 0, 8, 2, 56);
		storm.aim(10, 0);
		assertFalse(storm.step(1));
		assertEquals(2, storm.x, 1e-9);
		for (int i = 0; i < 4; i++) {
			storm.step(1);
		}
		assertEquals(10, storm.x, 1e-9);
		assertTrue(storm.inside(10, 7.9));
		assertFalse(storm.inside(10, 8.1));
		// A target off the island is pulled in so the circle's centre stays inside radius - half the storm.
		storm.aim(500, 0);
		assertEquals(52, storm.targetX, 1e-9);
		HazardMath.Storm edge = new HazardMath.Storm(0, -900, 8, 2, 56);
		assertEquals(-52, edge.z, 1e-9);
	}

	@Test
	void stormAtEightPlayersCoversMoreGroundButStaysOnTheBiggerIsland() {
		PartyScaling.setConfig(new PartyConfig());
		for (int players : new int[] {1, 8}) {
			double island = 56 * PartyScaling.floorSize(players);
			HazardMath.Storm storm = new HazardMath.Storm(1000, 1000, HazardMath.stormRadius(9, players), 1, island);
			assertTrue(Math.hypot(storm.x, storm.z) <= island, players + " players");
		}
	}

	@Test
	void pickIsWeighted() {
		FloorHazardsConfig.TagHazards blight = new FloorHazardsConfig().byTag.get("blight");
		Random random = new Random(5);
		Map<String, Integer> counts = new HashMap<>();
		for (int i = 0; i < 6000; i++) {
			counts.merge(HazardMath.pick(blight.problems, random::nextDouble).id, 1, Integer::sum);
		}
		assertTrue(counts.get("blight_storm") > counts.get("spawn_surge"));
		assertTrue(counts.get("spawn_surge") > counts.get("dead_camp"));
		assertNull(HazardMath.pick(List.of(), random::nextDouble));
		FloorHazardsConfig.Problem off = new FloorHazardsConfig.Problem();
		off.weight = 0;
		assertNull(HazardMath.pick(List.of(off), random::nextDouble));
	}

	@Test
	void blightDefaultsAreMeanerThanTheDirectorsDay() {
		FloorHazardsConfig.TagHazards blight = new FloorHazardsConfig().byTag.get("blight");
		assertNotNull(blight);
		DirectorConfig.DayMobs normal = new DirectorConfig().dayMobs;
		assertTrue(blight.dayMobs.intervalSeconds < normal.intervalSeconds);
		assertTrue(blight.dayMobs.pack > normal.pack);
		assertTrue(blight.dayMobs.maxAlive > normal.maxAlive);
		assertTrue(blight.dayMobs.eliteChance > normal.eliteChance);
		assertEquals(List.of("blight_storm", "spawn_surge", "dead_camp"), blight.problems.stream().map(p -> p.id).toList());
		assertEquals(60, blight.problems.get(2).seconds, "the shop closes for 60 s");
		assertEquals(2, blight.roamingBossesAlive);
		assertTrue(blight.roamingBosses.contains("blocksouls_bosses:pale_rider"));
	}
}
