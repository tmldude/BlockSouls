package io.github.tmldude.blocksouls.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class WorldConfigMigrateTest {
	/** A world.json written by v0.10 or earlier: the old defaults, no defaultsVersion. */
	private static WorldConfig oldFile() {
		WorldConfig config = new WorldConfig();
		config.defaultsVersion = 0;
		config.gen.gunmetalVeinsPerChunk = 7;
		config.gen.scrapPileRarityChunks = 24;
		config.loot.waveCommonChance = 0.25;
		config.loot.waveRareChance = 0.4;
		config.loot.waveLegendaryChance = 0.6;
		config.loot.materialChests = new ArrayList<>(List.of("minecraft:chests/simple_dungeon"));
		return config;
	}

	@Test
	void untouchedOldDefaultsTakeTheNewOnes() {
		WorldConfig config = oldFile();
		assertTrue(WorldConfig.migrate(config));
		WorldConfig fresh = new WorldConfig();
		assertEquals(fresh.gen.gunmetalVeinsPerChunk, config.gen.gunmetalVeinsPerChunk);
		assertEquals(fresh.gen.scrapPileRarityChunks, config.gen.scrapPileRarityChunks);
		assertEquals(fresh.loot.waveCommonChance, config.loot.waveCommonChance);
		assertEquals(fresh.loot.waveRareChance, config.loot.waveRareChance);
		assertEquals(fresh.loot.waveLegendaryChance, config.loot.waveLegendaryChance);
		assertTrue(config.loot.materialChests.containsAll(List.of("blocksouls_world:chests/wreck", "blocksouls_world:chests/checkpoint")));
		assertTrue(config.loot.materialChests.contains("minecraft:chests/simple_dungeon"));
		assertEquals(WorldConfig.CURRENT_DEFAULTS, config.defaultsVersion);
	}

	@Test
	void newDefaultsAreRoughlyDouble() {
		WorldConfig fresh = new WorldConfig();
		assertEquals(14, fresh.gen.gunmetalVeinsPerChunk);
		assertEquals(12, fresh.gen.scrapPileRarityChunks);
		assertEquals(2 * 0.6, fresh.loot.materialRolls * fresh.loot.materialChance, 1.0E-9);
	}

	@Test
	void valuesChangedByHandAreKept() {
		WorldConfig config = oldFile();
		config.gen.gunmetalVeinsPerChunk = 3;
		config.loot.waveRareChance = 0.1;
		WorldConfig.migrate(config);
		assertEquals(3, config.gen.gunmetalVeinsPerChunk);
		assertEquals(0.1, config.loot.waveRareChance);
		assertEquals(12, config.gen.scrapPileRarityChunks);
	}

	@Test
	void currentFilesAreLeftAlone() {
		WorldConfig config = new WorldConfig();
		int chests = config.loot.materialChests.size();
		assertTrue(WorldConfig.migrate(config), "a fresh config only gets its version stamped");
		assertEquals(chests, config.loot.materialChests.size(), "no duplicate chest entries");
		assertFalse(WorldConfig.migrate(config), "second run is a no-op");
	}
}
