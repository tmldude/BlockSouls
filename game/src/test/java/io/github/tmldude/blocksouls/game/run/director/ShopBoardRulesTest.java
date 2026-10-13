package io.github.tmldude.blocksouls.game.run.director;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import io.github.tmldude.blocksouls.game.run.director.ShopBoardRules.Board;
import io.github.tmldude.blocksouls.game.run.director.ShopBoardRules.EnchantSpec;
import io.github.tmldude.blocksouls.game.run.director.ShopBoardRules.Entry;
import io.github.tmldude.blocksouls.game.run.director.ShopBoardRules.FloorView;
import io.github.tmldude.blocksouls.game.run.director.ShopBoardRules.Kind;
import io.github.tmldude.blocksouls.game.run.director.ShopBoardRules.Placement;

/** Nothing on the boards scales with party size (each player crafts from their own inventory), so no 1/8-player cases. */
class ShopBoardRulesTest {
	private static Entry entry(String recipe, Board board, Placement placement, boolean hidden, List<List<String>> units) {
		return new Entry(recipe, board, "x:" + recipe, 1, units, placement, hidden);
	}

	private static Entry entry(String recipe, Board board, Placement placement) {
		return entry(recipe, board, placement, false, List.of(List.of("x:a")));
	}

	@Test
	void entriesFilterByBoardFloorRangeTagsAndHidden() {
		List<Entry> entries = List.of(
				entry("anywhere", Board.FORGE, Placement.EVERYWHERE),
				entry("brew", Board.BREW, Placement.EVERYWHERE),
				entry("deep", Board.FORGE, new Placement(3, 99, Set.of())),
				entry("shallow", Board.FORGE, new Placement(1, 2, Set.of())),
				entry("infernal", Board.FORGE, new Placement(1, 99, Set.of("infernal", "ash"))),
				entry("hidden", Board.FORGE, Placement.EVERYWHERE, true, List.of(List.of("x:a"))));
		assertEquals(List.of("anywhere", "shallow"), recipes(ShopBoardRules.visible(entries, Board.FORGE, new FloorView(1, Set.of()))));
		assertEquals(List.of("anywhere", "shallow"), recipes(ShopBoardRules.visible(entries, Board.FORGE, new FloorView(2, Set.of("frozen")))));
		assertEquals(List.of("anywhere", "deep", "infernal"), recipes(ShopBoardRules.visible(entries, Board.FORGE, new FloorView(3, Set.of("ash")))));
		assertEquals(List.of("brew"), recipes(ShopBoardRules.visible(entries, Board.BREW, new FloorView(5, Set.of("ash")))));
	}

	@Test
	void placementBoundsAreInclusive() {
		Placement two = new Placement(2, 2, Set.of());
		assertFalse(two.shows(new FloorView(1, Set.of())));
		assertTrue(two.shows(new FloorView(2, Set.of())));
		assertFalse(two.shows(new FloorView(3, Set.of())));
	}

	private static List<String> recipes(List<Entry> entries) {
		return entries.stream().map(Entry::recipe).toList();
	}

	@Test
	void classifyPrefersEntryThenTagsThenConsumable() {
		assertEquals(Board.FORGE, ShopBoardRules.classify(Board.FORGE, true, false, true));
		assertEquals(Board.BREW, ShopBoardRules.classify(null, true, true, false));
		assertEquals(Board.FORGE, ShopBoardRules.classify(null, false, true, true));
		assertEquals(Board.BREW, ShopBoardRules.classify(null, false, false, true));
		assertEquals(Board.FORGE, ShopBoardRules.classify(null, false, false, false));
		assertEquals(Board.BREW, Board.parse(" Brew "));
		assertNull(Board.parse("trade"));
	}

	@Test
	void matchTakesOneItemPerUnit() {
		List<List<String>> units = List.of(List.of("x:ingot"), List.of("x:ingot"), List.of("x:stick"));
		assertEquals(List.of("x:ingot", "x:ingot", "x:stick"), ShopBoardRules.match(units, Map.of("x:ingot", 2, "x:stick", 5)));
		assertNull(ShopBoardRules.match(units, Map.of("x:ingot", 1, "x:stick", 5)));
		assertNull(ShopBoardRules.match(units, Map.of()));
	}

	@Test
	void matchLeavesTheOnlyItemForTheNarrowUnit() {
		// The tag unit comes first but must not take the oak plank the narrow unit needs.
		List<List<String>> units = List.of(List.of("x:oak", "x:birch"), List.of("x:oak"));
		assertEquals(List.of("x:birch", "x:oak"), ShopBoardRules.match(units, Map.of("x:oak", 1, "x:birch", 1)));
	}

	@Test
	void matchBacktracksAcrossEqualWidthUnits() {
		// The first unit tries a, which leaves the second with nothing; it has to back up and take b.
		List<List<String>> units = List.of(List.of("x:a", "x:b"), List.of("x:a", "x:c"));
		assertEquals(List.of("x:b", "x:a"), ShopBoardRules.match(units, Map.of("x:a", 1, "x:b", 1)));
	}

	@Test
	void kindsAndMissingCountShortfalls() {
		Entry entry = entry("pick", Board.FORGE, Placement.EVERYWHERE, false,
				List.of(List.of("x:ingot"), List.of("x:ingot"), List.of("x:ingot"), List.of("x:stick"), List.of("x:stick")));
		List<Kind> kinds = entry.kinds();
		assertEquals(List.of(new Kind(List.of("x:ingot"), 3), new Kind(List.of("x:stick"), 2)), kinds);
		assertEquals(Map.of(List.of("x:ingot"), 2), ShopBoardRules.missing(kinds, Map.of("x:ingot", 1, "x:stick", 4)));
		assertTrue(ShopBoardRules.missing(kinds, Map.of("x:ingot", 3, "x:stick", 2)).isEmpty());
	}

	@Test
	void enchantSpecPrefersTaggedThenDeepest() {
		EnchantSpec surface = new EnchantSpec("surface", new Placement(1, 99, Set.of()), "x:gunmetal", 2, 6, List.of("e:a"));
		EnchantSpec deep = new EnchantSpec("deep", new Placement(3, 99, Set.of()), "x:cobalt", 2, 6, List.of("e:b"));
		EnchantSpec infernal = new EnchantSpec("infernal", new Placement(1, 99, Set.of("infernal")), "x:cinder", 3, 8, List.of("e:c"));
		List<EnchantSpec> specs = List.of(surface, deep, infernal);
		assertEquals("surface", ShopBoardRules.enchantSpec(specs, new FloorView(1, Set.of())).source());
		assertEquals("deep", ShopBoardRules.enchantSpec(specs, new FloorView(4, Set.of())).source());
		assertEquals("infernal", ShopBoardRules.enchantSpec(specs, new FloorView(4, Set.of("infernal"))).source());
		assertNull(ShopBoardRules.enchantSpec(List.of(deep), new FloorView(1, Set.of())));
	}

	@Test
	void nextTierUsesTheFirstChainWithOne() {
		List<List<String>> chains = List.of(List.of("x:leather", "x:chain", "x:plate"), List.of("x:cinder", "x:plate"), List.of("x:chain", "x:other"));
		assertEquals("x:chain", ShopBoardRules.nextTier(chains, "x:leather"));
		assertEquals("x:plate", ShopBoardRules.nextTier(chains, "x:chain"));
		assertEquals("x:plate", ShopBoardRules.nextTier(chains, "x:cinder"));
		assertNull(ShopBoardRules.nextTier(chains, "x:plate"));
	}

	@Test
	void enchantLevelStartsAtOneAndStopsAtMax() {
		assertEquals(1, ShopBoardRules.enchantLevel(0, 4));
		assertEquals(3, ShopBoardRules.enchantLevel(2, 4));
		assertEquals(0, ShopBoardRules.enchantLevel(4, 4));
	}

	@Test
	void pagesHoldTenEntries() {
		assertEquals(1, ShopBoardRules.pages(0));
		assertEquals(1, ShopBoardRules.pages(10));
		assertEquals(2, ShopBoardRules.pages(11));
	}
}
