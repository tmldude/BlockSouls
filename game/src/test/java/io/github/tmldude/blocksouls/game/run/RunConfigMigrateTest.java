package io.github.tmldude.blocksouls.game.run;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.tmldude.blocksouls.common.Config;

class RunConfigMigrateTest {
	@TempDir
	Path dir;
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
	void aV1EraFileLoadsCleanlyAndTheSpineFieldsGetTheirDefaults() throws IOException {
		// An actual game-run.json as v0.9/v0.10.0 wrote it: old pacing defaults, no defaultsVersion, and
		// none of the v2 spine keys (runShape, acts, portalChargeSeconds).
		Files.writeString(dir.resolve("game-run.json"), """
				{
					"autoStart": true,
					"defaultDifficulty": "normal",
					"dayLengthMinutes": 17.0,
					"duskCueTime": 11500,
					"duskTime": 13000,
					"dawnTime": 23000,
					"wavesPerNight": [1, 1, 2, 2, 2, 2, 2, 3],
					"waveBreakSeconds": 20,
					"dropChance": 0.35,
					"starterPistolLastWave": 3
				}
				""");
		RunConfig config = Config.load(dir, "game-run", RunConfig.class, RunConfig::new);
		assertTrue(RunConfig.migrate(config));
		RunConfig fresh = new RunConfig();
		// The spine keys fall back to their defaults and migration leaves them untouched.
		assertEquals("classic", config.runShape);
		assertEquals(4, config.acts.size());
		assertEquals(fresh.acts.get(1).dimension, config.acts.get(1).dimension);
		assertEquals(fresh.portalChargeSeconds, config.portalChargeSeconds);
		// The pacing migration still applies to the untouched old defaults.
		assertEquals(fresh.dayLengthMinutes, config.dayLengthMinutes);
		assertEquals(RunConfig.CURRENT_DEFAULTS, config.defaultsVersion);
	}

	@Test
	void migrateLeavesHandEditedSpineFieldsAlone() {
		RunConfig config = oldFile();
		config.runShape = "expedition";
		config.portalChargeSeconds = 60;
		config.acts = new ArrayList<>(List.of(new RunConfig.ActSpec("blocksouls_game:solo", "minecraft:overworld", "Solo")));
		RunConfig.migrate(config);
		assertEquals("expedition", config.runShape);
		assertEquals(60, config.portalChargeSeconds);
		assertEquals(1, config.acts.size());
	}

	@Test
	void currentFilesAreLeftAlone() {
		RunConfig config = new RunConfig();
		assertTrue(RunConfig.migrate(config), "a fresh config only gets its version stamped");
		assertEquals(new RunConfig().waveBreakSeconds, config.waveBreakSeconds);
		assertFalse(RunConfig.migrate(config), "second run is a no-op");
	}
}
