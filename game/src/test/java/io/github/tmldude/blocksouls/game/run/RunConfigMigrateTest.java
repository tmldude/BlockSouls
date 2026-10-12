package io.github.tmldude.blocksouls.game.run;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class RunConfigMigrateTest {
	/** A game-run.json written by v0.10.0 or earlier: the old defaults, no defaultsVersion. */
	private static RunConfig oldFile() {
		RunConfig config = new RunConfig();
		config.defaultsVersion = 0;
		config.dayLengthMinutes = 17.0;
		config.wavesPerNight = new ArrayList<>(List.of(1, 1, 2, 2, 2, 2, 2, 3));
		config.waveBreakSeconds = 20;
		config.dropChance = 0.35;
		config.starterPistolLastWave = 3;
		config.duskCueTime = 11500;
		config.tierOdds = new ArrayList<>(List.of(new RunConfig.TierOdds(1, 1, 0, 0), new RunConfig.TierOdds(4, 0.8, 0.2, 0),
				new RunConfig.TierOdds(7, 0.65, 0.3, 0.05)));
		return config;
	}

	@Test
	void untouchedOldDefaultsTakeTheNewOnes() {
		RunConfig config = oldFile();
		assertTrue(RunConfig.migrate(config));
		RunConfig fresh = new RunConfig();
		assertEquals(fresh.dayLengthMinutes, config.dayLengthMinutes);
		assertEquals(fresh.wavesPerNight, config.wavesPerNight);
		assertEquals(fresh.waveBreakSeconds, config.waveBreakSeconds);
		assertEquals(fresh.dropChance, config.dropChance);
		assertEquals(fresh.starterPistolLastWave, config.starterPistolLastWave);
		assertEquals(fresh.duskCueTime, config.duskCueTime);
		assertEquals(fresh.tierOdds.get(1).fromWave, config.tierOdds.get(1).fromWave);
		assertEquals(RunConfig.CURRENT_DEFAULTS, config.defaultsVersion);
	}

	@Test
	void valuesChangedByHandAreKept() {
		RunConfig config = oldFile();
		config.waveBreakSeconds = 30;
		config.dayLengthMinutes = 25.0;
		RunConfig.migrate(config);
		assertEquals(30, config.waveBreakSeconds);
		assertEquals(25.0, config.dayLengthMinutes);
		assertEquals(0.5, config.dropChance);
	}

	@Test
	void versionOneFilesOnlyTakeTheVersionTwoDefaults() {
		RunConfig config = oldFile();
		config.defaultsVersion = 1;
		config.waveBreakSeconds = 20;
		RunConfig.migrate(config);
		assertEquals(20, config.waveBreakSeconds, "v1 already chose its break length");
		assertEquals(new RunConfig().duskCueTime, config.duskCueTime);
		assertEquals(3, config.tierOdds.get(1).fromWave);
	}

	@Test
	void handTunedTierOddsAreKept() {
		RunConfig config = oldFile();
		config.tierOdds.get(2).legendary = 0.2;
		RunConfig.migrate(config);
		assertEquals(4, config.tierOdds.get(1).fromWave);
		assertEquals(0.2, config.tierOdds.get(2).legendary);
	}

	@Test
	void currentFilesAreLeftAlone() {
		RunConfig config = new RunConfig();
		assertTrue(RunConfig.migrate(config), "a fresh config only gets its version stamped");
		assertEquals(new RunConfig().waveBreakSeconds, config.waveBreakSeconds);
		assertFalse(RunConfig.migrate(config), "second run is a no-op");
	}
}
