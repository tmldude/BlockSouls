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
}
