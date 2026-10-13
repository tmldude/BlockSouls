package io.github.tmldude.blocksouls.game.player.rune;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import com.google.gson.JsonParseException;
import org.junit.jupiter.api.Test;

import io.github.tmldude.blocksouls.game.player.classes.ClassEffect;

class RuneParserTest {
	static final List<String> CLASSES = List.of("blocksouls_game:warden", "blocksouls_game:striker", "blocksouls_game:arcanist", "blocksouls_game:ranger");

	@Test
	void parsesEverything() {
		Rune rune = RuneParser.parse("blocksouls_game:ranger_wings", """
				{"class": "ranger", "cost": 40, "requires": ["blocksouls_game:ranger_fleet"], "capabilities": ["glide"],
				 "effects": [{"type": "trait", "trait": "blocksouls:double_jump"}], "ability_mods": ["blocksouls_abilities:x"]}
				""");
		assertEquals("blocksouls_game:ranger", rune.playerClass(), "classes default to the game namespace");
		assertEquals(40, rune.cost());
		assertEquals(List.of("blocksouls_game:ranger_fleet"), rune.requires());
		assertEquals(Set.of("blocksouls:glide"), rune.capabilities());
		assertEquals(List.of(new ClassEffect.Trait("blocksouls:double_jump", 1)), rune.effects());
		assertEquals(List.of("blocksouls_abilities:x"), rune.abilityMods());
	}

	@Test
	void rejectsBadInput() {
		assertThrows(JsonParseException.class, () -> RuneParser.parse("blocksouls_game:a", "{\"class\": \"warden\", \"cost\": 5}"), "grants nothing");
		assertThrows(JsonParseException.class, () -> RuneParser.parse("blocksouls_game:a", "{\"class\": \"warden\", \"cost\": -1, \"capabilities\": [\"glide\"]}"), "negative cost");
		assertThrows(JsonParseException.class, () -> RuneParser.parse("blocksouls_game:a", "{\"class\": \"warden\", \"cost\": 1, \"capabilities\": [\"fly\"]}"), "unknown verb");
		assertThrows(JsonParseException.class, () -> RuneParser.parse("blocksouls_game:a", "{\"class\": \"warden\", \"cost\": 1, \"requires\": [\"blocksouls_game:a\"], \"capabilities\": [\"glide\"]}"), "self");
		assertThrows(JsonParseException.class, () -> RuneParser.parse("blocksouls_game:a", "{\"cost\": 1, \"capabilities\": [\"glide\"]}"), "no class");
	}

	@Test
	void everyShippedRuneParsesIntoAClassTreeWithResolvableEdges() throws IOException {
		Path dir = Path.of("src/main/resources/data/blocksouls_game/runes");
		if (!Files.isDirectory(dir)) {
			dir = Path.of("game").resolve(dir);
		}
		Map<String, Rune> runes = new HashMap<>();
		try (Stream<Path> files = Files.list(dir)) {
			for (Path file : files.filter(f -> f.toString().endsWith(".json")).toList()) {
				String id = "blocksouls_game:" + file.getFileName().toString().replace(".json", "");
				runes.put(id, RuneParser.parse(id, Files.readString(file)));
			}
		}
		assertEquals(24, runes.size(), "six runes per class");
		for (String playerClass : CLASSES) {
			long count = runes.values().stream().filter(r -> r.playerClass().equals(playerClass)).count();
			assertEquals(6, count, playerClass + " tree");
			assertTrue(runes.values().stream().anyMatch(r -> r.playerClass().equals(playerClass) && r.requires().isEmpty()), playerClass + " has a root");
		}
		for (Rune rune : runes.values()) {
			assertTrue(CLASSES.contains(rune.playerClass()), rune.id() + " belongs to a shipped class");
			for (String required : rune.requires()) {
				Rune parent = runes.get(required);
				assertTrue(parent != null, rune.id() + " requires unknown " + required);
				assertEquals(rune.playerClass(), parent.playerClass(), rune.id() + " requires a rune of another class");
				assertTrue(parent.cost() <= rune.cost(), rune.id() + " is cheaper than its prerequisite");
			}
		}
		assertTrue(runes.get("blocksouls_game:ranger_wings").capabilities().contains("blocksouls:glide"), "the Ranger's glide is a rune");
	}
}
