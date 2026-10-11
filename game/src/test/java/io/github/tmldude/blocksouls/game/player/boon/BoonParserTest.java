package io.github.tmldude.blocksouls.game.player.boon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Stream;

import com.google.gson.JsonParseException;
import org.junit.jupiter.api.Test;

class BoonParserTest {
	@Test
	void parsesEveryEffectType() {
		Boon boon = BoonParser.parse("blocksouls_game:test", """
				{
				  "tier": "rare",
				  "icon": "feather",
				  "stackable": 3,
				  "requires": ["blocksouls_game:a", "blocksouls_game:b"],
				  "effects": [
				    {"type": "effect", "effect": "minecraft:speed", "amplifier": 1},
				    {"type": "attribute", "attribute": "minecraft:jump_strength", "amount": 0.5, "operation": "add_multiplied_base"},
				    {"type": "trait", "trait": "blocksouls:double_jump", "level": 2},
				    {"type": "item", "item": "minecraft:elytra"}
				  ]
				}
				""");

		assertEquals(BoonTier.RARE, boon.tier());
		assertEquals("minecraft:feather", boon.icon(), "icons without a namespace default to minecraft");
		assertEquals(3, boon.maxLevel());
		assertEquals(List.of("blocksouls_game:a", "blocksouls_game:b"), boon.requires());
		assertTrue(boon.isCombo());
		assertEquals(new BoonEffect.Effect("minecraft:speed", 1), boon.effects().get(0));
		assertEquals(new BoonEffect.Attribute("minecraft:jump_strength", 0.5, "add_multiplied_base"), boon.effects().get(1));
		assertEquals(new BoonEffect.Trait("blocksouls:double_jump", 2), boon.effects().get(2));
		assertEquals(new BoonEffect.Item("minecraft:elytra", 1), boon.effects().get(3));
	}

	@Test
	void defaults() {
		Boon boon = BoonParser.parse("blocksouls_game:test",
				"{\"tier\": \"common\", \"icon\": \"minecraft:sugar\", \"effects\": [{\"type\": \"effect\", \"effect\": \"minecraft:speed\"}]}");
		assertEquals(1, boon.maxLevel());
		assertFalse(boon.isCombo());
		assertInstanceOf(BoonEffect.Effect.class, boon.effects().getFirst());
		assertEquals(0, ((BoonEffect.Effect) boon.effects().getFirst()).amplifier());
	}

	@Test
	void rejectsMalformedFiles() {
		String effects = "\"effects\": [{\"type\": \"effect\", \"effect\": \"minecraft:speed\"}]";
		List<String> bad = List.of(
				"not json {",
				"[]",
				"{\"icon\": \"minecraft:sugar\", " + effects + "}",
				"{\"tier\": \"mythic\", \"icon\": \"minecraft:sugar\", " + effects + "}",
				"{\"tier\": \"common\", " + effects + "}",
				"{\"tier\": \"common\", \"icon\": \"Not An Id\", " + effects + "}",
				"{\"tier\": \"common\", \"icon\": \"minecraft:sugar\"}",
				"{\"tier\": \"common\", \"icon\": \"minecraft:sugar\", \"effects\": []}",
				"{\"tier\": \"common\", \"icon\": \"minecraft:sugar\", \"effects\": [{\"type\": \"teleport\"}]}",
				"{\"tier\": \"common\", \"icon\": \"minecraft:sugar\", \"effects\": [{\"type\": \"attribute\", \"attribute\": \"minecraft:luck\", \"amount\": 1, \"operation\": \"multiply\"}]}",
				"{\"tier\": \"common\", \"icon\": \"minecraft:sugar\", \"effects\": [{\"type\": \"attribute\", \"attribute\": \"minecraft:luck\", \"amount\": \"lots\", \"operation\": \"add_value\"}]}",
				"{\"tier\": \"common\", \"icon\": \"minecraft:sugar\", \"stackable\": 0, " + effects + "}",
				"{\"tier\": \"common\", \"icon\": \"minecraft:sugar\", \"stackable\": 1.5, " + effects + "}",
				"{\"tier\": \"common\", \"icon\": \"minecraft:sugar\", \"requires\": [\"blocksouls_game:test\"], " + effects + "}",
				"{\"tier\": \"common\", \"icon\": \"minecraft:sugar\", \"requires\": \"blocksouls_game:x\", " + effects + "}");
		for (String json : bad) {
			assertThrows(JsonParseException.class, () -> BoonParser.parse("blocksouls_game:test", json), json);
		}
		assertThrows(JsonParseException.class, () -> BoonParser.parse("Bad Id", "{}"));
	}

	private static final String EFFECTS = "\"effects\": [{\"type\": \"effect\", \"effect\": \"minecraft:speed\"}]";

	@Test
	void acceptsItemAndSpriteIcons() {
		Boon item = BoonParser.parse("blocksouls_game:test",
				"{\"tier\": \"common\", \"icon\": \"minecraft:sugar\", " + EFFECTS + "}", sprite -> false);
		assertEquals("minecraft:sugar", item.icon());
		assertFalse(BoonIcons.isSprite(item.icon()), "item icons are never checked against sprites");

		Boon sprite = BoonParser.parse("blocksouls_game:test",
				"{\"tier\": \"common\", \"icon\": \"blocksouls_game:boon/test\", " + EFFECTS + "}",
				"blocksouls_game:boon/test"::equals);
		assertEquals("blocksouls_game:boon/test", sprite.icon());
		assertTrue(BoonIcons.isSprite(sprite.icon()));
		assertEquals("assets/blocksouls_game/textures/gui/sprites/boon/test.png", BoonIcons.texturePath(sprite.icon()));
	}

	@Test
	void rejectsMissingSprite() {
		JsonParseException e = assertThrows(JsonParseException.class, () -> BoonParser.parse("blocksouls_game:test",
				"{\"tier\": \"common\", \"icon\": \"blocksouls_game:boon/nope\", " + EFFECTS + "}", sprite -> false));
		assertTrue(e.getMessage().contains("boon/nope"), e.getMessage());
	}

	@Test
	void shippedBoonsParseAndCombosResolve() throws IOException {
		Path resources = Stream.of("src/main/resources", "game/src/main/resources")
				.map(Path::of).filter(Files::isDirectory).findFirst().orElseThrow();
		Path dir = resources.resolve("data/blocksouls_game/boons");
		Predicate<String> spriteExists = icon -> Files.isRegularFile(resources.resolve(BoonIcons.texturePath(icon)));
		Map<String, Boon> boons = new HashMap<>();
		try (Stream<Path> files = Files.list(dir)) {
			for (Path file : files.toList()) {
				String id = "blocksouls_game:" + file.getFileName().toString().replace(".json", "");
				Boon boon = BoonParser.parse(id, Files.readString(file), spriteExists);
				assertEquals("blocksouls_game:boon/" + id.substring(id.indexOf(':') + 1), boon.icon(), "every shipped boon has its own sprite");
				boons.put(id, boon);
			}
		}
		assertTrue(spriteExists.test(BoonIcons.WILDCARD), "the Wildcard card back ships");

		assertTrue(boons.size() >= 20, "ships ~20 boons, found " + boons.size());
		for (BoonTier tier : BoonTier.values()) {
			assertTrue(boons.values().stream().anyMatch(b -> b.tier() == tier && !b.isCombo()), "no " + tier + " boons");
		}
		long combos = boons.values().stream().filter(Boon::isCombo).count();
		assertEquals(2, combos);
		for (Boon boon : boons.values()) {
			for (String required : boon.requires()) {
				assertTrue(boons.containsKey(required), boon.id() + " requires missing " + required);
			}
		}
	}
}
