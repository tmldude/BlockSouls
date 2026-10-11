package io.github.tmldude.blocksouls.guns.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;

class PerkStateTest {
	@Test
	void rampageStacksToThreeAndRefreshes() {
		RampageStacks stacks = RampageStacks.NONE;
		stacks = stacks.onKill(0, 80, 3);
		assertEquals(1, stacks.active(10));
		stacks = stacks.onKill(10, 80, 3).onKill(20, 80, 3).onKill(30, 80, 3);
		assertEquals(3, stacks.active(30));
		assertEquals(1.3, stacks.multiplier(30, 0.1), 1e-9);
		// Refreshed at tick 30, so it lasts until 110.
		assertEquals(3, stacks.active(109));
		assertEquals(0, stacks.active(110));
		assertEquals(1.0, stacks.multiplier(110, 0.1), 1e-9);
	}

	@Test
	void rampageRestartsFromOneAfterExpiry() {
		RampageStacks stacks = RampageStacks.NONE.onKill(0, 80, 3).onKill(10, 80, 3);
		stacks = stacks.onKill(200, 80, 3);
		assertEquals(1, stacks.active(200));
	}

	@Test
	void rarityRollFollowsWeights() {
		Random random = new Random(3);
		int[] counts = new int[3];
		for (int i = 0; i < 20000; i++) {
			counts[Rolls.rarity(new double[] {70, 25, 5}, random).ordinal()]++;
		}
		assertEquals(0.70, counts[0] / 20000.0, 0.02);
		assertEquals(0.25, counts[1] / 20000.0, 0.02);
		assertEquals(0.05, counts[2] / 20000.0, 0.01);
		assertEquals(GunRarity.LEGENDARY, Rolls.rarity(new double[] {0, 0, 1}, random));
		assertEquals(GunRarity.COMMON, Rolls.rarity(new double[] {0, 0, 0}, random));
	}

	@Test
	void perkRollsAreDistinct() {
		Random random = new Random(5);
		List<Perk> pool = List.of(Perk.values());
		for (int i = 0; i < 100; i++) {
			List<Perk> perks = Rolls.perks(pool, GunRarity.LEGENDARY.perkCount(), random);
			assertEquals(2, perks.size());
			assertEquals(2, new HashSet<>(perks).size());
		}
		assertEquals(1, Rolls.perks(pool, GunRarity.RARE.perkCount(), random).size());
		assertTrue(Rolls.perks(pool, GunRarity.COMMON.perkCount(), random).isEmpty());
		assertEquals(1, Rolls.perks(List.of(Perk.VORPAL), 2, random).size());
	}

	@Test
	void ids() {
		assertEquals("explosive_rounds", Perk.EXPLOSIVE_ROUNDS.id());
		assertEquals(Perk.OUTLAW, Perk.byId("outlaw").orElseThrow());
		assertTrue(Perk.byId("unknown_perk").isEmpty());
		assertEquals(GunRarity.COMMON, GunRarity.byId("nope"));
		assertEquals(GunRarity.LEGENDARY, GunRarity.byId("legendary"));
	}
}
