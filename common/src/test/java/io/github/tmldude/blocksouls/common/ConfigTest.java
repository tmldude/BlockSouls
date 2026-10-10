package io.github.tmldude.blocksouls.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConfigTest {
	static class Sample {
		double fallGravityMult = 1.6;
		int coyoteTicks = 3;
	}

	@TempDir
	Path dir;

	@Test
	void writesDefaultsWhenMissing() {
		Sample config = Config.load(dir, "sample", Sample.class, Sample::new);

		assertEquals(1.6, config.fallGravityMult);
		assertTrue(Files.exists(dir.resolve("sample.json")));
	}

	@Test
	void keepsDefaultsForMissingKeysAndRewritesFile() throws IOException {
		Files.writeString(dir.resolve("sample.json"), "{\"fallGravityMult\": 2.0}");

		Sample config = Config.load(dir, "sample", Sample.class, Sample::new);

		assertEquals(2.0, config.fallGravityMult);
		assertEquals(3, config.coyoteTicks);
		assertTrue(Files.readString(dir.resolve("sample.json")).contains("coyoteTicks"));
	}

	@Test
	void leavesMalformedFileUntouched() throws IOException {
		Path file = dir.resolve("sample.json");
		Files.writeString(file, "{not json");

		Sample config = Config.load(dir, "sample", Sample.class, Sample::new);

		assertEquals(1.6, config.fallGravityMult);
		assertEquals("{not json", Files.readString(file));
	}

	@Test
	void treatsEmptyFileAsDefaults() throws IOException {
		Files.writeString(dir.resolve("sample.json"), "");

		Sample config = Config.load(dir, "sample", Sample.class, Sample::new);

		assertEquals(3, config.coyoteTicks);
	}
}
