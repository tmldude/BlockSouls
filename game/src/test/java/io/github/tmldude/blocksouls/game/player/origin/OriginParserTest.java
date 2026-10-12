package io.github.tmldude.blocksouls.game.player.origin;

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

class OriginParserTest {
	/** The ten shipped seeds and the movement signature each declares (PLAN2 §3f). */
	static final Map<String, Set<String>> SEEDS = Map.of(
			"drifter", Set.of("blocksouls:multi_jump"),
			"webslinger", Set.of("blocksouls:climb", "blocksouls:swing"),
			"tidecaller", Set.of(),
			"ashborn", Set.of(),
			"zephyr", Set.of("blocksouls:glide"),
			"juggernaut", Set.of(),
			"gunslinger", Set.of(),
			"tinkerer", Set.of(),
			"bloodbound", Set.of(),
			"gambler", Set.of());

	@Test
	void parsesEverything() {
		Origin origin = OriginParser.parse("blocksouls_game:test", """
				{
				  "icon": "blocksouls_game:origin/test",
				  "capabilities": ["climb", "blocksouls:glide"],
				  "kit": [{"item": "minecraft:feather", "count": 3}, {"loot_table": "blocksouls_game:origins/test"}],
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
				            "ammo_drop_multiplier": 0.75, "altar_cost_multiplier": 0.5}
				}
				""");
		assertEquals(Set.of("blocksouls:climb", "blocksouls:glide"), origin.capabilities(), "verbs default to the blocksouls namespace");
		assertEquals(List.of(Origin.KitEntry.item("minecraft:feather", 3), Origin.KitEntry.loot("blocksouls_game:origins/test")), origin.kit());
		assertEquals(new OriginEffect.Effect("minecraft:speed", 1, OriginEffect.When.ALWAYS), origin.passives().get(0));
		assertEquals(new OriginEffect.Effect("minecraft:regeneration", 0, OriginEffect.When.IN_RAIN), origin.passives().get(1));
		assertEquals(new OriginEffect.Trait("blocksouls:double_jump", 2), origin.passives().get(2));
		assertEquals(new OriginEffect.Attribute("minecraft:max_health", -4, "add_value"), origin.drawbacks().get(0));
		assertEquals(4, origin.effects().size(), "passives then drawbacks");
		assertEquals(Optional.of("blocksouls_abilities:test"), origin.signatureAbility());
		assertEquals(Map.of("blocksouls:fire", 2, "blocksouls:storm", 1), origin.synergyTags());
		assertEquals(new OriginRules(2, 0.5, 1, true, 4, true, true, 0.75, 0.5), origin.rules());
	}

	@Test
	void defaultsAreTheBaselinePlayer() {
		Origin origin = OriginParser.parse("blocksouls_game:test", "{\"icon\": \"minecraft:stone\"}");
		assertTrue(origin.capabilities().isEmpty(), "no verbs: roll and hold-jump only");
		assertTrue(origin.kit().isEmpty() && origin.effects().isEmpty() && origin.synergyTags().isEmpty());
		assertFalse(origin.signatureAbility().isPresent());
		assertEquals(OriginRules.NEUTRAL, origin.rules());
	}

	@Test
	void rejectsBadInput() {
		assertThrows(JsonParseException.class, () -> OriginParser.parse("blocksouls_game:test", "{\"icon\": \"x\", \"capabilities\": [\"fly\"]}"), "unknown verb");
		assertThrows(JsonParseException.class, () -> OriginParser.parse("blocksouls_game:test", "{\"icon\": \"x\", \"passives\": [{\"type\": \"item\", \"item\": \"stone\"}]}"), "items go in the kit");
		assertThrows(JsonParseException.class, () -> OriginParser.parse("blocksouls_game:test", "{\"icon\": \"x\", \"passives\": [{\"type\": \"effect\", \"effect\": \"speed\", \"when\": \"at_night\"}]}"), "unknown when");
		assertThrows(JsonParseException.class, () -> OriginParser.parse("blocksouls_game:test", "{\"icon\": \"x\", \"rules\": {\"revive_speed\": 0}}"), "revive speed must be positive");
		assertThrows(JsonParseException.class, () -> OriginParser.parse("blocksouls_game:test", "{\"icon\": \"x\", \"kit\": [{\"item\": \"stone\", \"count\": 0}]}"), "empty kit line");
		assertThrows(JsonParseException.class, () -> OriginParser.parse("blocksouls_game:test", "not json"));
		assertThrows(JsonParseException.class, () -> OriginParser.parse("bad id", "{\"icon\": \"x\"}"));
	}

	@Test
	void everyShippedSeedParsesWithItsDeclaredSignature() throws IOException {
		Path dir = Path.of("src/main/resources/data/blocksouls_game/origins");
		if (!Files.isDirectory(dir)) {
			dir = Path.of("game").resolve(dir);
		}
		for (Map.Entry<String, Set<String>> seed : SEEDS.entrySet()) {
			Path file = dir.resolve(seed.getKey() + ".json");
			assertTrue(Files.exists(file), "seed " + file);
			Origin origin = OriginParser.parse("blocksouls_game:" + seed.getKey(), Files.readString(file));
			assertEquals(seed.getValue(), origin.capabilities(), seed.getKey() + " movement signature");
			assertEquals("blocksouls_game:origin/" + seed.getKey(), origin.icon());
			assertTrue(origin.signatureAbility().isPresent(), seed.getKey() + " names a signature ability for the abilities module");
		}
		try (Stream<Path> files = Files.list(dir)) {
			assertEquals(SEEDS.size(), files.filter(f -> f.toString().endsWith(".json")).count(), "exactly the ten seeds ship");
		}
	}
}
