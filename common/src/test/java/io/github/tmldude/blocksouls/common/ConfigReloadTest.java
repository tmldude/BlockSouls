package io.github.tmldude.blocksouls.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConfigReloadTest {
	enum Mode { SLOW, FAST }

	record Tier(String name, double chance) {
	}

	static class Swing {
		double releaseBoost = 0.4;
		List<String> blocks = new ArrayList<>(List.of("stone"));
	}

	static class Sample {
		double fallGravityMult = 1.6;
		int coyoteTicks = 3;
		String label = "jump";
		Mode mode = Mode.SLOW;
		Swing swing = new Swing();
		List<Tier> tiers = new ArrayList<>(List.of(new Tier("common", 0.5)));
		List<Integer> fixed = List.of(1, 2);
		Map<String, Double> weights = new HashMap<>(Map.of("a", 1.0));
		int[] array = {1};
		transient int cached = 7;
	}

	@TempDir
	Path dir;

	@Test
	void copiesFieldsIntoTheSameInstances() {
		Sample live = new Sample();
		Swing swing = live.swing;
		List<String> blocks = swing.blocks;
		List<Tier> tiers = live.tiers;
		Map<String, Double> weights = live.weights;

		Sample fresh = new Sample();
		fresh.fallGravityMult = 2.5;
		fresh.label = "hop";
		fresh.mode = Mode.FAST;
		fresh.swing.releaseBoost = 0.9;
		fresh.swing.blocks = new ArrayList<>(List.of("dirt", "sand"));
		fresh.tiers = new ArrayList<>(List.of(new Tier("rare", 0.1), new Tier("epic", 0.01)));
		fresh.fixed = List.of(3);
		fresh.weights = new HashMap<>(Map.of("b", 2.0));
		fresh.array = new int[] {4, 5};
		fresh.cached = 99;

		Config.copyFields(fresh, live);

		assertEquals(2.5, live.fallGravityMult);
		assertEquals("hop", live.label);
		assertEquals(Mode.FAST, live.mode);
		assertSame(swing, live.swing, "nested object keeps its identity");
		assertEquals(0.9, live.swing.releaseBoost);
		assertSame(blocks, live.swing.blocks, "mutable list refilled in place");
		assertEquals(List.of("dirt", "sand"), live.swing.blocks);
		assertSame(tiers, live.tiers);
		assertEquals(List.of(new Tier("rare", 0.1), new Tier("epic", 0.01)), live.tiers);
		assertEquals(List.of(3), live.fixed, "immutable list replaced");
		assertSame(weights, live.weights);
		assertEquals(Map.of("b", 2.0), live.weights);
		assertEquals(5, live.array[1]);
		assertEquals(7, live.cached, "transient fields are left alone");
	}

	@Test
	void reloadUpdatesTheLoadedInstanceAndCallsBack() throws IOException {
		Sample live = Config.load(dir, "reload_sample", Sample.class, Sample::new);
		Swing swing = live.swing;
		Files.writeString(dir.resolve("reload_sample.json"), "{\"coyoteTicks\": 6, \"swing\": {\"releaseBoost\": 1.25}}");

		List<String> names = new ArrayList<>();
		List<Object> instances = new ArrayList<>();
		String error = Config.reload("reload_sample", (name, config) -> {
			names.add(name);
			instances.add(config);
		});

		assertNull(error);
		assertEquals(6, live.coyoteTicks);
		assertEquals(1.25, live.swing.releaseBoost);
		assertSame(swing, live.swing);
		assertEquals(1.6, live.fallGravityMult, "missing keys go back to their defaults");
		assertEquals(List.of("reload_sample"), names);
		assertSame(live, instances.get(0));
		assertSame(live, Config.loaded("reload_sample"));
		assertTrue(Config.loadedNames().contains("reload_sample"));
	}

	@Test
	void badJsonKeepsTheOldValues() throws IOException {
		Sample live = Config.load(dir, "bad_sample", Sample.class, Sample::new);
		live.coyoteTicks = 5;
		Files.writeString(dir.resolve("bad_sample.json"), "{\"coyoteTicks\": 9,");

		List<String> called = new ArrayList<>();
		String error = Config.reload("bad_sample", (name, config) -> called.add(name));

		assertNotNull(error);
		assertEquals(5, live.coyoteTicks);
		assertTrue(called.isEmpty(), "no reload event for a file that didn't parse");
		assertEquals("{\"coyoteTicks\": 9,", Files.readString(dir.resolve("bad_sample.json")), "the file is not rewritten");
	}

	@Test
	void wrongTypeKeepsTheOldValues() throws IOException {
		Sample live = Config.load(dir, "type_sample", Sample.class, Sample::new);
		Files.writeString(dir.resolve("type_sample.json"), "{\"coyoteTicks\": \"lots\"}");

		assertNotNull(Config.reload("type_sample", (name, config) -> { }));
		assertEquals(3, live.coyoteTicks);
	}

	@Test
	void unknownNameIsAnError() {
		assertNotNull(Config.reload("never_loaded", (name, config) -> { }));
	}
}
