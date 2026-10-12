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
import java.util.Set;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Stream;

import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
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

	@Test
	void parsesTagsKeystoneLevelsAndRules() {
		Boon keystone = BoonParser.parse("t:k", """
				{"tier": "legendary", "icon": "minecraft:sugar", "keystone": true, "tags": ["on_reload", "blocksouls:explosion"],
				 "effects": [{"type": "rule", "rule": "powder_burn"}]}""");
		assertTrue(keystone.keystone());
		assertEquals(1, keystone.maxLevel(), "keystones have one level");
		assertEquals(List.of("blocksouls:on_reload", "blocksouls:explosion"), keystone.tags(), "tags default to the blocksouls namespace");
		assertEquals(List.of(new BoonEffect.Rule("blocksouls_game:powder_burn")), keystone.effects(), "rules default to the game namespace");

		Boon leveled = BoonParser.parse("t:l", "{\"tier\": \"common\", \"icon\": \"minecraft:sugar\", \"levels\": 2, " + EFFECTS + "}");
		assertEquals(2, leveled.maxLevel());
		Boon defaulted = BoonParser.parse("t:d", JsonParser.parseString("{\"tier\": \"common\", \"icon\": \"minecraft:sugar\", " + EFFECTS + "}"),
				sprite -> true, Boon.Kind.BOON, 3);
		assertEquals(3, defaulted.maxLevel(), "ordinary boons take the configured default");
		assertEquals(1, BoonParser.parse("t:s", "{\"tier\": \"common\", \"icon\": \"minecraft:sugar\", " + EFFECTS + "}").maxLevel(),
				"the old overloads keep one level");

		Boon curse = BoonParser.parse("t:c", JsonParser.parseString("{\"icon\": \"minecraft:sugar\", " + EFFECTS + "}"),
				sprite -> true, Boon.Kind.CURSE, 3);
		assertTrue(curse.isCurse());
		assertEquals(BoonTier.COMMON, curse.tier(), "tier is optional for curses and trinkets");
		assertEquals(1, curse.maxLevel());

		for (String bad : List.of(
				"{\"tier\": \"common\", \"icon\": \"minecraft:sugar\", \"keystone\": true, " + EFFECTS + "}",
				"{\"tier\": \"rare\", \"icon\": \"minecraft:sugar\", \"tags\": [\"bad id!\"], " + EFFECTS + "}",
				"{\"tier\": \"rare\", \"icon\": \"minecraft:sugar\", \"effects\": [{\"type\": \"rule\"}]}")) {
			assertThrows(JsonParseException.class, () -> BoonParser.parse("t:bad", bad), bad);
		}
		assertThrows(JsonParseException.class, () -> BoonParser.parse("t:bad", JsonParser.parseString(
				"{\"icon\": \"minecraft:sugar\", \"keystone\": true, " + EFFECTS + "}"), sprite -> true, Boon.Kind.TRINKET, 3),
				"only boons can be keystones");
	}

	@Test
	void shippedCursesTrinketsAndTagsAreComplete() throws IOException {
		Path resources = Stream.of("src/main/resources", "game/src/main/resources")
				.map(Path::of).filter(Files::isDirectory).findFirst().orElseThrow();
		Predicate<String> spriteExists = icon -> Files.isRegularFile(resources.resolve(BoonIcons.texturePath(icon)));
		Map<Boon.Kind, List<Boon>> shipped = new HashMap<>();
		for (Boon.Kind kind : Boon.Kind.values()) {
			try (Stream<Path> files = Files.list(resources.resolve("data/blocksouls_game/" + kind.folder()))) {
				for (Path file : files.toList()) {
					String id = "blocksouls_game:" + file.getFileName().toString().replace(".json", "");
					Boon boon = BoonParser.parse(id, JsonParser.parseString(Files.readString(file)), spriteExists, kind, 3);
					assertEquals(kind, boon.kind());
					assertEquals("blocksouls_game:boon/" + id.substring(id.indexOf(':') + 1), boon.icon(), id + " has its own sprite");
					shipped.computeIfAbsent(kind, k -> new ArrayList<>()).add(boon);
				}
			}
		}
		List<Boon> boons = shipped.get(Boon.Kind.BOON);
		assertTrue(boons.size() >= 20, "boons: " + boons.size());
		for (Boon boon : boons) {
			assertFalse(boon.tags().isEmpty(), boon.id() + " declares synergy tags");
			assertTrue(boon.maxLevel() <= 3, boon.id() + " stays within 3 pips");
		}
		assertEquals(8, boons.stream().filter(Boon::keystone).count(), "eight keystones");
		for (Boon keystone : boons.stream().filter(Boon::keystone).toList()) {
			assertTrue(keystone.tags().stream().anyMatch(OfferRules::isTrigger) || keystone.id().endsWith("ricochet_doctrine"),
					keystone.id() + " keys on a trigger tag");
		}
		assertTrue(shipped.get(Boon.Kind.CURSE).size() >= 5, "five curses");
		assertEquals(8, shipped.get(Boon.Kind.TRINKET).size(), "eight trinkets");
		assertTrue(spriteExists.test(BoonIcons.UPGRADE), "the Upgrade card back ships");
		Set<String> ids = new HashSet<>();
		shipped.values().forEach(list -> list.forEach(boon -> assertTrue(ids.add(boon.id()), "duplicate id " + boon.id())));
	}
}
