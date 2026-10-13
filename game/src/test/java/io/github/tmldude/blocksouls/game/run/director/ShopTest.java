package io.github.tmldude.blocksouls.game.run.director;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;

import org.junit.jupiter.api.Test;

class ShopTest {
	private static DirectorConfig.Offer offer(String product, int minAct, int maxAct) {
		return DirectorConfig.Offer.of(minAct, maxAct, 1).product(product, 1).price("blocksouls_game:soul", 5);
	}

	@Test
	void everyDefaultOfferIsValidAndRareOnesCostTwoResourceKinds() {
		List<DirectorConfig.Offer> pool = new DirectorConfig().shop.pool;
		assertFalse(pool.isEmpty());
		assertTrue(pool.stream().anyMatch(o -> o.rare), "the default board has rare offers");
		for (DirectorConfig.Offer offer : pool) {
			assertTrue(offer.valid(), "default offer " + offer.item + offer.loot + " is valid");
			if (offer.rare) {
				assertTrue(offer.resourceKinds() >= 2, offer.item + offer.loot + " is rare and costs 2+ resource kinds");
			}
		}
	}

	@Test
	void aRareOfferPayingOnlySoulsAndOneResourceIsInvalid() {
		DirectorConfig.Offer cheap = offer("minecraft:diamond", 1, 99).price("minecraft:iron_ingot", 4).rare();
		assertFalse(cheap.valid());
		cheap.price("minecraft:gold_ingot", 2);
		assertTrue(cheap.valid());
		assertFalse(DirectorConfig.Offer.of(1, 99, 1).product("minecraft:diamond", 1).valid(), "an offer needs a price");
		assertFalse(offer("minecraft:air", 1, 99).valid(), "an offer needs a product");
	}

	@Test
	void stockIsSeededDistinctAndFilteredByAct() {
		List<DirectorConfig.Offer> pool = List.of(offer("minecraft:apple", 1, 1), offer("minecraft:bread", 1, 99), offer("minecraft:carrot", 2, 99),
				offer("minecraft:potato", 1, 99), offer("minecraft:cookie", 3, 99));
		List<DirectorConfig.Offer> first = DirectorRules.stock(pool, 2, 42L, 6);
		assertEquals(first, DirectorRules.stock(pool, 2, 42L, 6), "same run and act, same board");
		assertEquals(3, first.size(), "only the act-2 offers: bread, carrot, potato");
		assertEquals(first.size(), new HashSet<>(first).size(), "no offer twice");
		assertTrue(first.stream().noneMatch(o -> o.item.equals("minecraft:apple") || o.item.equals("minecraft:cookie")));
		assertEquals(2, DirectorRules.stock(pool, 2, 42L, 2).size(), "slots cap the board");
		assertTrue(DirectorRules.stock(pool, 1, 7L, 6).stream().anyMatch(o -> o.item.equals("minecraft:apple")));
	}
}
