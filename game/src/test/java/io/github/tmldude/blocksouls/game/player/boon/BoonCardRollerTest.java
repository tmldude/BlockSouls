package io.github.tmldude.blocksouls.game.player.boon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import org.junit.jupiter.api.Test;

class BoonCardRollerTest {
	private static final List<BoonEffect> EFFECT = List.of(new BoonEffect.Effect("minecraft:speed", 0));

	private static Boon boon(String path, BoonTier tier, int maxLevel, String... requires) {
		return new Boon("t:" + path, tier, "minecraft:sugar", maxLevel, List.of(requires), EFFECT);
	}

	private static final List<Boon> POOL = List.of(
			boon("c1", BoonTier.COMMON, 1), boon("c2", BoonTier.COMMON, 1), boon("c3", BoonTier.COMMON, 1),
			boon("c4", BoonTier.COMMON, 2), boon("c5", BoonTier.COMMON, 1),
			boon("r1", BoonTier.RARE, 1), boon("r2", BoonTier.RARE, 1),
			boon("l1", BoonTier.LEGENDARY, 1),
			boon("combo", BoonTier.LEGENDARY, 1, "t:c1", "t:r1"));

	private static CardRoller.Offer roll(Map<String, Integer> owned, BoonTier tier, double wildcard, long seed) {
		return CardRoller.roll(POOL, owned, tier, 3, wildcard, true, new Random(seed));
	}

	private static List<String> ids(CardRoller.Offer offer) {
		List<String> ids = new ArrayList<>();
		for (CardRoller.Card card : offer.cards()) {
			ids.add(card.wildcard() ? "*" : card.boon().id());
		}
		return ids;
	}

	@Test
	void threeDistinctCardsOfTheRelicTier() {
		for (long seed = 0; seed < 200; seed++) {
			CardRoller.Offer offer = roll(Map.of(), BoonTier.COMMON, 0, seed);
			List<String> ids = ids(offer);
			assertEquals(3, ids.size());
			assertEquals(3, new HashSet<>(ids).size(), "duplicates in " + ids);
			for (CardRoller.Card card : offer.cards()) {
				assertEquals(BoonTier.COMMON, card.boon().tier());
			}
		}
	}

	@Test
	void respectsStackingCaps() {
		Map<String, Integer> owned = Map.of("t:c1", 1, "t:c2", 1, "t:c3", 1, "t:c4", 1);
		Set<String> seen = new HashSet<>();
		for (long seed = 0; seed < 200; seed++) {
			seen.addAll(ids(roll(owned, BoonTier.COMMON, 0, seed)));
		}
		assertFalse(seen.contains("t:c1"));
		assertFalse(seen.contains("t:c2"));
		assertFalse(seen.contains("t:c3"));
		assertTrue(seen.contains("t:c4"), "c4 stacks to 2, so level 1 can be offered again");
		assertTrue(seen.contains("t:c5"));
	}

	@Test
	void fillsFromOtherTiersWhenTheTierRunsDry() {
		Map<String, Integer> owned = Map.of("t:r1", 1);
		CardRoller.Offer offer = roll(owned, BoonTier.RARE, 0, 1);
		List<String> ids = ids(offer);
		assertEquals(3, ids.size());
		assertTrue(ids.contains("t:r2"));
		long commons = offer.cards().stream().filter(c -> c.boon().tier() == BoonTier.COMMON).count();
		assertEquals(2, commons, "lower tiers fill first: " + ids);
	}

	@Test
	void combosNeedTheirRequirements() {
		for (long seed = 0; seed < 200; seed++) {
			assertFalse(ids(roll(Map.of("t:c1", 1), BoonTier.LEGENDARY, 0, seed)).contains("t:combo"));
		}

		boolean offered = false;
		for (long seed = 0; seed < 200 && !offered; seed++) {
			offered = ids(roll(Map.of("t:c1", 1, "t:r1", 1), BoonTier.COMMON, 0, seed)).contains("t:combo");
		}
		assertTrue(offered, "an unlocked combo can show up on any relic tier");
		assertTrue(CardRoller.canOffer(POOL.getLast(), Map.of("t:c1", 1, "t:r1", 1)));
		assertFalse(CardRoller.canOffer(POOL.getLast(), Map.of("t:c1", 1, "t:r1", 1, "t:combo", 1)));
	}

	@Test
	void combosStayInTheirTierWhenConfigured() {
		Map<String, Integer> owned = Map.of("t:c1", 1, "t:r1", 1);
		for (long seed = 0; seed < 200; seed++) {
			List<String> ids = ids(CardRoller.roll(POOL, owned, BoonTier.COMMON, 3, 0, false, new Random(seed)));
			assertFalse(ids.contains("t:combo"));
		}
	}

	@Test
	void wildcardChance() {
		int wildcards = 0;
		int rolls = 10_000;
		for (long seed = 0; seed < rolls; seed++) {
			CardRoller.Offer offer = roll(Map.of(), BoonTier.COMMON, 0.10, seed);
			if (offer.cards().getLast().wildcard()) {
				wildcards++;
				assertEquals(5, offer.wildcardOptions().size(), "a wildcard lists every common boon the player can take");
				assertEquals(1, offer.cards().stream().filter(CardRoller.Card::wildcard).count());
			} else {
				assertTrue(offer.wildcardOptions().isEmpty());
			}
		}
		assertTrue(Math.abs(wildcards / (double) rolls - 0.10) < 0.02, "wildcard rate " + wildcards / (double) rolls);

		for (long seed = 0; seed < 100; seed++) {
			assertFalse(roll(Map.of(), BoonTier.COMMON, 0, seed).cards().getLast().wildcard());
			assertTrue(roll(Map.of(), BoonTier.COMMON, 1, seed).cards().getLast().wildcard());
		}
	}

	@Test
	void emptyWhenNothingIsLeft() {
		Map<String, Integer> owned = Map.of("t:c1", 1, "t:c2", 1, "t:c3", 1, "t:c4", 2, "t:c5", 1,
				"t:r1", 1, "t:r2", 1, "t:l1", 1, "t:combo", 1);
		assertTrue(roll(owned, BoonTier.RARE, 1, 3).isEmpty());
	}

	@Test
	void fallbackOrderPrefersLowerTiers() {
		assertEquals(List.of(BoonTier.RARE, BoonTier.COMMON), CardRoller.fallbackOrder(BoonTier.LEGENDARY));
		assertEquals(List.of(BoonTier.COMMON, BoonTier.LEGENDARY), CardRoller.fallbackOrder(BoonTier.RARE));
		assertEquals(List.of(BoonTier.RARE, BoonTier.LEGENDARY), CardRoller.fallbackOrder(BoonTier.COMMON));
	}
}
