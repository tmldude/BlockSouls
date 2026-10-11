package io.github.tmldude.blocksouls.game.player.boon;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

/** Parses boon JSON. Throws {@link JsonParseException} with a readable message on malformed input. */
public final class BoonParser {
	private static final Pattern ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");
	private static final Set<String> OPERATIONS = Set.of("add_value", "add_multiplied_base", "add_multiplied_total");

	private BoonParser() {
	}

	public static Boon parse(String id, String json) {
		JsonElement element;
		try {
			element = JsonParser.parseString(json);
		} catch (RuntimeException e) {
			throw new JsonParseException("invalid JSON: " + e.getMessage(), e);
		}
		return parse(id, element);
	}

	public static Boon parse(String id, JsonElement element) {
		if (!ID.matcher(id).matches()) {
			throw new JsonParseException("invalid boon id '" + id + "'");
		}
		JsonObject json = object(element, "boon");

		String tierName = string(json, "tier");
		BoonTier tier = BoonTier.byId(tierName);
		if (tier == null) {
			throw new JsonParseException("unknown tier '" + tierName + "' (common, rare, legendary)");
		}

		String icon = identifier(json, "icon");
		int maxLevel = json.has("stackable") ? integer(json, "stackable") : 1;
		if (maxLevel < 1) {
			throw new JsonParseException("'stackable' must be at least 1");
		}

		List<String> requires = new ArrayList<>();
		if (json.has("requires")) {
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

		return new Boon(id, tier, icon, maxLevel, requires, effects);
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
			default -> throw new JsonParseException("unknown effect type '" + type + "' (effect, attribute, trait, item)");
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

	private static String identifier(JsonObject json, String key) {
		String value = string(json, key);
		if (value.indexOf(':') < 0) {
			value = "minecraft:" + value;
		}
		checkId(value, "'" + key + "'");
		return value;
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
