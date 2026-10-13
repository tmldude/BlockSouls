package io.github.tmldude.blocksouls.game.player.classes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import com.google.gson.JsonParseException;
import org.junit.jupiter.api.Test;

class ClassParserTest {
	/** The four shipped classes (PLAN2 §2) and the signature verbs each adds on top of the universal kit. */
	static final Map<String, Set<String>> CLASSES = Map.of(
			"warden", Set.of(),
			"striker", Set.of(),
			"arcanist", Set.of(),
			"ranger", Set.of());

	@Test
	void parsesEverything() {
		PlayerClass playerClass = ClassParser.parse("blocksouls_game:test", """
				{
				  "icon": "blocksouls_game:class/test",
				  "signature_capabilities": ["glide"],
				  "kit": [{"item": "minecraft:feather", "count": 3}, {"loot_table": "blocksouls_game:classes/test"}],
				  "passives": [
				    {"type": "effect", "effect": "minecraft:speed", "amplifier": 1},
				    {"type": "effect", "effect": "regeneration", "when": "in_rain"},
				    {"type": "trait", "trait": "blocksouls:double_jump", "level": 2}
				  ],
				  "drawbacks": [{"type": "attribute", "attribute": "minecraft:max_health", "amount": -4, "operation": "add_value"}],
				  "signature_ability": "blocksouls_abilities:test",
				  "synergy_tags": {"fire": 2, "blocksouls:storm": 1},
				  "rules": {"revive_speed": 2, "heal_multiplier": 0.5, "hearts_per_boss_kill": 1, "roll_mist": true,
				            "relic_cards": 4, "relic_always_wildcard": true, "relic_hidden": true,
				            "ammo_drop_multiplier": 0.75, "altar_cost_multiplier": 0.5,
				            "slam_min_fall": 4, "slam_radius": 3, "slam_knockback": 1.5, "slam_damage": 6, "hover_ticks": 30}
				}
				""");
		assertEquals(Set.of("blocksouls:glide"), playerClass.capabilities(), "verbs default to the blocksouls namespace");
		assertEquals(List.of(PlayerClass.KitEntry.item("minecraft:feather", 3), PlayerClass.KitEntry.loot("blocksouls_game:classes/test")), playerClass.kit());
		assertEquals(new ClassEffect.Effect("minecraft:speed", 1, ClassEffect.When.ALWAYS), playerClass.passives().get(0));
		assertEquals(new ClassEffect.Effect("minecraft:regeneration", 0, ClassEffect.When.IN_RAIN), playerClass.passives().get(1));
		assertEquals(new ClassEffect.Trait("blocksouls:double_jump", 2), playerClass.passives().get(2));
		assertEquals(new ClassEffect.Attribute("minecraft:max_health", -4, "add_value"), playerClass.drawbacks().get(0));
		assertEquals(4, playerClass.effects().size(), "passives then drawbacks");
		assertEquals(Optional.of("blocksouls_abilities:test"), playerClass.signatureAbility());
		assertEquals(Map.of("blocksouls:fire", 2, "blocksouls:storm", 1), playerClass.synergyTags());
		assertEquals(new ClassRules(2, 0.5, 1, true, 4, true, true, 0.75, 0.5, 4, 3, 1.5, 6, 30), playerClass.rules());
		assertTrue(playerClass.rules().slams() && playerClass.rules().hovers());
	}

	@Test
	void defaultsAreThePlainPlayer() {
		PlayerClass playerClass = ClassParser.parse("blocksouls_game:test", "{\"icon\": \"minecraft:stone\"}");
		assertTrue(playerClass.capabilities().isEmpty(), "no signature verbs: the universal kit only");
		assertTrue(playerClass.kit().isEmpty() && playerClass.effects().isEmpty() && playerClass.synergyTags().isEmpty());
		assertFalse(playerClass.signatureAbility().isPresent());
		assertEquals(ClassRules.NEUTRAL, playerClass.rules());
		assertFalse(playerClass.rules().slams() || playerClass.rules().hovers());
	}

	@Test
	void rejectsBadInput() {
		assertThrows(JsonParseException.class, () -> ClassParser.parse("blocksouls_game:test", "{\"icon\": \"x\", \"signature_capabilities\": [\"fly\"]}"), "unknown verb");
		assertThrows(JsonParseException.class, () -> ClassParser.parse("blocksouls_game:test", "{\"icon\": \"x\", \"passives\": [{\"type\": \"item\", \"item\": \"stone\"}]}"), "items go in the kit");
		assertThrows(JsonParseException.class, () -> ClassParser.parse("blocksouls_game:test", "{\"icon\": \"x\", \"passives\": [{\"type\": \"effect\", \"effect\": \"speed\", \"when\": \"at_night\"}]}"), "unknown when");
		assertThrows(JsonParseException.class, () -> ClassParser.parse("blocksouls_game:test", "{\"icon\": \"x\", \"rules\": {\"revive_speed\": 0}}"), "revive speed must be positive");
		assertThrows(JsonParseException.class, () -> ClassParser.parse("blocksouls_game:test", "{\"icon\": \"x\", \"kit\": [{\"item\": \"stone\", \"count\": 0}]}"), "empty kit line");
		assertThrows(JsonParseException.class, () -> ClassParser.parse("blocksouls_game:test", "not json"));
		assertThrows(JsonParseException.class, () -> ClassParser.parse("bad id", "{\"icon\": \"x\"}"));
	}

	@Test
	void everyShippedClassParsesWithItsDeclaredSignature() throws IOException {
		Path dir = Path.of("src/main/resources/data/blocksouls_game/classes");
		if (!Files.isDirectory(dir)) {
			dir = Path.of("game").resolve(dir);
		}
		for (Map.Entry<String, Set<String>> entry : CLASSES.entrySet()) {
			Path file = dir.resolve(entry.getKey() + ".json");
			assertTrue(Files.exists(file), "class " + file);
			PlayerClass playerClass = ClassParser.parse("blocksouls_game:" + entry.getKey(), Files.readString(file));
			assertEquals(entry.getValue(), playerClass.capabilities(), entry.getKey() + " signature verbs");
			assertEquals("blocksouls_game:class/" + entry.getKey(), playerClass.icon());
			assertTrue(playerClass.signatureAbility().isPresent(), entry.getKey() + " names a signature ability for the abilities module");
			assertFalse(playerClass.kit().isEmpty(), entry.getKey() + " has a kit");
		}
		try (Stream<Path> files = Files.list(dir)) {
			assertEquals(CLASSES.size(), files.filter(f -> f.toString().endsWith(".json")).count(), "exactly the four classes ship");
		}
	}
}
