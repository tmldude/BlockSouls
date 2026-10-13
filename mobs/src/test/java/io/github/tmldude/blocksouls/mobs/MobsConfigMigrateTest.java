package io.github.tmldude.blocksouls.mobs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MobsConfigMigrateTest {
	@Test
	void oldMutantBruteWaveMovesEarlier() {
		MobsConfig config = new MobsConfig();
		config.defaultsVersion = 0;
		config.mutantBrute.minWave = 7;
		assertTrue(MobsConfig.migrate(config));
		assertEquals(new MobsConfig().mutantBrute.minWave, config.mutantBrute.minWave);
		assertEquals(MobsConfig.CURRENT_DEFAULTS, config.defaultsVersion);
	}

	@Test
	void handTunedWaveIsKept() {
		MobsConfig config = new MobsConfig();
		config.defaultsVersion = 0;
		config.mutantBrute.minWave = 10;
		MobsConfig.migrate(config);
		assertEquals(10, config.mutantBrute.minWave);
		assertFalse(MobsConfig.migrate(config), "second run is a no-op");
	}

	// --- floor 3, Skyrift (Stage 6C) ---
	@Test
	void floorAffixMixesJoinAnOlderFile() {
		MobsConfig config = new MobsConfig();
		config.defaultsVersion = 1;
		config.affixes.weightsByFloorTag = new java.util.LinkedHashMap<>(java.util.Map.of("vault", new java.util.LinkedHashMap<>(java.util.Map.of("volatile", 9))));
		assertTrue(MobsConfig.migrate(config));
		assertEquals(5, config.affixes.weightsByFloorTag.get("skyrift").get("frosted"));
		assertEquals(9, config.affixes.weightsByFloorTag.get("vault").get("volatile"), "a tuned tag is kept");
	}

	// v2-floor2-infernal: a file Skyrift's step already brought to 2 still gets the Infernal's mix, once.
	@Test
	void infernalAffixMixJoinsARevision2File() {
		MobsConfig config = new MobsConfig();
		config.defaultsVersion = 2;
		config.affixes.weightsByFloorTag = new java.util.LinkedHashMap<>(java.util.Map.of("skyrift", new java.util.LinkedHashMap<>(java.util.Map.of("frosted", 9))));
		assertTrue(MobsConfig.migrate(config));
		assertEquals(5, config.affixes.weightsByFloorTag.get("infernal").get("burning"));
		assertEquals(9, config.affixes.weightsByFloorTag.get("skyrift").get("frosted"), "a tuned tag is kept");
		assertFalse(MobsConfig.migrate(config), "second run is a no-op");
	}
}
