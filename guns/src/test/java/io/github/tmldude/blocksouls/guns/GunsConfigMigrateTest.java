package io.github.tmldude.blocksouls.guns;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class GunsConfigMigrateTest {
	/** A guns.json from v0.10 or earlier: the old ammo chances, no defaultsVersion. */
	private static GunsConfig oldFile() {
		GunsConfig config = new GunsConfig();
		config.defaultsVersion = 0;
		config.loot.waveCommonPrimaryChance = 0.12;
		config.loot.waveCommonSpecialChance = 0.03;
		config.loot.waveRarePrimaryChance = 0.15;
		config.loot.waveRareSpecialChance = 0.06;
		config.loot.waveLegendaryPrimaryChance = 0.18;
		config.loot.waveLegendarySpecialChance = 0.10;
		return config;
	}

	@Test
	void untouchedOldDefaultsTakeTheNewOnes() {
		GunsConfig config = oldFile();
		assertTrue(GunsConfig.migrate(config));
		GunsConfig.Loot fresh = new GunsConfig().loot;
		assertEquals(fresh.waveCommonPrimaryChance, config.loot.waveCommonPrimaryChance);
		assertEquals(fresh.waveRareSpecialChance, config.loot.waveRareSpecialChance);
		assertEquals(fresh.waveLegendaryPrimaryChance, config.loot.waveLegendaryPrimaryChance);
		assertEquals(GunsConfig.CURRENT_DEFAULTS, config.defaultsVersion);
	}

	@Test
	void valuesChangedByHandAreKept() {
		GunsConfig config = oldFile();
		config.loot.waveCommonPrimaryChance = 0.5;
		GunsConfig.migrate(config);
		assertEquals(0.5, config.loot.waveCommonPrimaryChance);
		assertEquals(new GunsConfig().loot.waveRarePrimaryChance, config.loot.waveRarePrimaryChance);
	}

	@Test
	void currentFilesAreLeftAlone() {
		GunsConfig config = new GunsConfig();
		assertTrue(GunsConfig.migrate(config), "a fresh config only gets its version stamped");
		assertFalse(GunsConfig.migrate(config), "second run is a no-op");
	}
}
