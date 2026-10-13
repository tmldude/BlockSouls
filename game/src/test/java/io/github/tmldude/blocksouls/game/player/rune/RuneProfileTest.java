package io.github.tmldude.blocksouls.game.player.rune;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

class RuneProfileTest {
	static final Rune ROOT = new Rune("blocksouls_game:ranger_fleet", "blocksouls_game:ranger", 20, List.of(), Set.of(), List.of(), List.of("x"));
	static final Rune WINGS = new Rune("blocksouls_game:ranger_wings", "blocksouls_game:ranger", 40, List.of(ROOT.id()), Set.of("blocksouls:glide"), List.of(), List.of());

	@Test
	void unlockingSpendsSoulsAndFollowsTheTree() {
		RuneProfile profile = new RuneProfile();
		profile.souls = 50;
		assertFalse(profile.canReach(WINGS), "needs the root first");
		assertTrue(profile.unlock(ROOT));
		assertEquals(30, profile.souls);
		assertFalse(profile.unlock(ROOT), "once");
		assertTrue(profile.canReach(WINGS));
		assertFalse(profile.unlock(WINGS), "can't afford 40 with 30");
		profile.souls += 10;
		assertTrue(profile.unlock(WINGS));
		assertEquals(0, profile.souls);
		assertEquals(Set.of(ROOT.id(), WINGS.id()), profile.unlockedSet());
	}

	@Test
	void roundTripsThroughDiskAndSurvivesAMissingOrBrokenFile() throws IOException {
		Path dir = Files.createTempDirectory("blocksouls-runes");
		Path file = dir.resolve("profile.json");
		assertEquals(0, RuneProfileIO.load(file).souls, "missing file: fresh");

		RuneProfile profile = new RuneProfile();
		profile.souls = 77;
		profile.unlocked.add(ROOT.id());
		profile.runsFinished = 3;
		RuneProfileIO.save(file, profile);
		RuneProfile loaded = RuneProfileIO.load(file);
		assertEquals(77, loaded.souls);
		assertEquals(List.of(ROOT.id()), loaded.unlocked);
		assertEquals(3, loaded.runsFinished);

		Files.writeString(file, "{not json");
		assertEquals(0, RuneProfileIO.load(file).souls, "broken file: fresh, never a crash");
	}
}
