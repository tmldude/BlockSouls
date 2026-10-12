package io.github.tmldude.blocksouls.game.player.boon;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

/** Parses boon, curse and trinket JSON. Throws {@link JsonParseException} with a readable message on malformed input. */
public final class BoonParser {
	private static final Pattern ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");
	private static final Set<String> OPERATIONS = Set.of("add_value", "add_multiplied_base", "add_multiplied_total");
	/** Synergy tags without a namespace live in {@code blocksouls:} (the {@code SynergyTags} vocabulary). */
	public static final String TAG_NAMESPACE = "blocksouls";
	/** Rules without a namespace live in the game module. */
	public static final String RULE_NAMESPACE = "blocksouls_game";

	private BoonParser() {
	}

	/** Parses a boon without checking that a sprite icon's PNG exists (see {@link #parse(String, JsonElement, Predicate)}). */
	public static Boon parse(String id, String json) {
		return parse(id, json, sprite -> true);
	}

	public static Boon parse(String id, String json, Predicate<String> spriteExists) {
		JsonElement element;
		try {
			element = JsonParser.parseString(json);
		} catch (RuntimeException e) {
			throw new JsonParseException("invalid JSON: " + e.getMessage(), e);
		}
		return parse(id, element, spriteExists);
	}

	public static Boon parse(String id, JsonElement element) {
		return parse(id, element, sprite -> true);
	}

	/** Parses a boon ({@link Boon.Kind#BOON}) whose default level count is 1. */
	public static Boon parse(String id, JsonElement element, Predicate<String> spriteExists) {
		return parse(id, element, spriteExists, Boon.Kind.BOON, 1);
	}

	/**
	 * @param spriteExists  whether a sprite icon id ({@link BoonIcons#isSprite}) has a texture; a sprite icon without
	 *                      one is rejected. Item icons aren't checked here (the item registry isn't known yet).
	 * @param kind          the folder the definition came from
	 * @param defaultLevels levels an ordinary boon gets when its JSON doesn't say ({@code "levels"}); keystones,
	 *                      curses and trinkets default to 1
	 */
	public static Boon parse(String id, JsonElement element, Predicate<String> spriteExists, Boon.Kind kind, int defaultLevels) {
		if (!ID.matcher(id).matches()) {
			throw new JsonParseException("invalid boon id '" + id + "'");
		}
		JsonObject json = object(element, kind.id());

		BoonTier tier = BoonTier.COMMON;
		if (kind == Boon.Kind.BOON || json.has("tier")) {
			String tierName = string(json, "tier");
			tier = BoonTier.byId(tierName);
			if (tier == null) {
				throw new JsonParseException("unknown tier '" + tierName + "' (common, rare, legendary)");
			}
		}

		String icon = identifier(json, "icon");
		if (BoonIcons.isSprite(icon) && !spriteExists.test(icon)) {
			throw new JsonParseException("no sprite for icon '" + icon + "' (expected " + BoonIcons.texturePath(icon) + ")");
		}

		boolean keystone = json.has("keystone") && bool(json, "keystone");
		if (keystone && kind != Boon.Kind.BOON) {
			throw new JsonParseException("only boons can be keystones");
		}
		if (keystone && tier == BoonTier.COMMON) {
			throw new JsonParseException("a keystone must be rare or legendary");
		}

		int maxLevel = kind == Boon.Kind.BOON && !keystone ? Math.max(1, defaultLevels) : 1;
		if (json.has("levels")) {
			maxLevel = integer(json, "levels");
		} else if (json.has("stackable")) {
			maxLevel = integer(json, "stackable");
		}
		if (maxLevel < 1) {
			throw new JsonParseException("'levels' must be at least 1");
		}

		List<String> tags = new ArrayList<>();
		if (json.has("tags")) {
			for (JsonElement entry : array(json, "tags")) {
				String tag = withNamespace(primitiveString(entry, "tags entry"), TAG_NAMESPACE);
				checkId(tag, "tags entry");
				if (!tags.contains(tag)) {
					tags.add(tag);
				}
			}
		}

		List<String> requires = new ArrayList<>();
		if (json.has("requires")) {
			if (kind != Boon.Kind.BOON) {
				throw new JsonParseException("only boons can have 'requires'");
			}
			for (JsonElement entry : array(json, "requires")) {
				String required = primitiveString(entry, "requires entry");
				checkId(required, "requires entry");
				if (required.equals(id)) {
					throw new JsonParseException("a boon can't require itself");
				}
				requires.add(required);
			}
		}

		JsonArray effectsJson = array(json, "effects");
		if (effectsJson.isEmpty()) {
			throw new JsonParseException("'effects' is empty");
		}
		List<BoonEffect> effects = new ArrayList<>();
		for (JsonElement entry : effectsJson) {
			effects.add(effect(object(entry, "effects entry")));
		}

		return new Boon(id, kind, tier, icon, maxLevel, keystone, tags, requires, effects);
	}

	private static BoonEffect effect(JsonObject json) {
		String type = string(json, "type");
		return switch (type) {
			case "effect" -> new BoonEffect.Effect(identifier(json, "effect"), json.has("amplifier") ? integer(json, "amplifier") : 0);
			case "attribute" -> {
				String operation = string(json, "operation");
				if (!OPERATIONS.contains(operation)) {
					throw new JsonParseException("unknown operation '" + operation + "' (" + String.join(", ", OPERATIONS) + ")");
				}
				yield new BoonEffect.Attribute(identifier(json, "attribute"), number(json, "amount"), operation);
			}
			case "trait" -> new BoonEffect.Trait(identifier(json, "trait"), json.has("level") ? integer(json, "level") : 1);
			case "item" -> new BoonEffect.Item(identifier(json, "item"), json.has("count") ? integer(json, "count") : 1);
			case "rule" -> {
				String rule = withNamespace(string(json, "rule"), RULE_NAMESPACE);
				checkId(rule, "'rule'");
				yield new BoonEffect.Rule(rule);
			}
			default -> throw new JsonParseException("unknown effect type '" + type + "' (effect, attribute, trait, item, rule)");
		};
	}

	private static JsonObject object(JsonElement element, String what) {
		if (element == null || !element.isJsonObject()) {
			throw new JsonParseException(what + " must be an object");
		}
		return element.getAsJsonObject();
	}

	private static JsonArray array(JsonObject json, String key) {
		JsonElement element = json.get(key);
		if (element == null || !element.isJsonArray()) {
			throw new JsonParseException("'" + key + "' must be a list");
		}
		return element.getAsJsonArray();
	}

	private static String primitiveString(JsonElement element, String what) {
		if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
			throw new JsonParseException(what + " must be a string");
		}
		return element.getAsString();
	}

	private static String string(JsonObject json, String key) {
		return primitiveString(json.get(key), "'" + key + "'");
	}

	private static boolean bool(JsonObject json, String key) {
		JsonElement element = json.get(key);
		if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isBoolean()) {
			throw new JsonParseException("'" + key + "' must be true or false");
		}
		return element.getAsBoolean();
	}

	private static String identifier(JsonObject json, String key) {
		String value = withNamespace(string(json, key), "minecraft");
		checkId(value, "'" + key + "'");
		return value;
	}

	private static String withNamespace(String value, String namespace) {
		return value.indexOf(':') < 0 ? namespace + ":" + value : value;
	}

	private static void checkId(String value, String what) {
		if (!ID.matcher(value).matches()) {
			throw new JsonParseException(what + " is not a valid id: '" + value + "'");
		}
	}

	private static double number(JsonObject json, String key) {
		JsonElement element = json.get(key);
		if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
			throw new JsonParseException("'" + key + "' must be a number");
		}
		return element.getAsDouble();
	}

	private static int integer(JsonObject json, String key) {
		double value = number(json, key);
		if (value != Math.rint(value)) {
			throw new JsonParseException("'" + key + "' must be a whole number");
		}
		return (int) value;
	}
}
