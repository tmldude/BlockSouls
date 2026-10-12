package io.github.tmldude.blocksouls.game.player.origin;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

/** Parses origin JSON. Throws {@link JsonParseException} with a readable message on malformed input. */
public final class OriginParser {
	private static final Pattern ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");
	private static final Set<String> OPERATIONS = Set.of("add_value", "add_multiplied_base", "add_multiplied_total");
	/** The movement verbs an origin may hold ({@code common}'s {@code Capabilities}). */
	public static final Set<String> VERBS = Set.of("blocksouls:climb", "blocksouls:swing", "blocksouls:multi_jump", "blocksouls:glide");

	private OriginParser() {
	}

	public static Origin parse(String id, String json) {
		JsonElement element;
		try {
			element = JsonParser.parseString(json);
		} catch (RuntimeException e) {
			throw new JsonParseException("invalid JSON: " + e.getMessage(), e);
		}
		return parse(id, element);
	}

	public static Origin parse(String id, JsonElement element) {
		if (!ID.matcher(id).matches()) {
			throw new JsonParseException("invalid origin id '" + id + "'");
		}
		JsonObject json = object(element, "origin");
		String icon = identifier(json, "icon");

		Set<String> capabilities = new LinkedHashSet<>();
		if (json.has("capabilities")) {
			for (JsonElement entry : array(json, "capabilities")) {
				String verb = primitiveString(entry, "capabilities entry");
				if (verb.indexOf(':') < 0) {
					verb = "blocksouls:" + verb;
				}
				if (!VERBS.contains(verb)) {
					throw new JsonParseException("unknown capability '" + verb + "' (" + String.join(", ", VERBS) + ")");
				}
				capabilities.add(verb);
			}
		}

		List<Origin.KitEntry> kit = new ArrayList<>();
		if (json.has("kit")) {
			for (JsonElement entry : array(json, "kit")) {
				JsonObject line = object(entry, "kit entry");
				if (line.has("loot_table")) {
					kit.add(Origin.KitEntry.loot(identifier(line, "loot_table")));
				} else {
					int count = line.has("count") ? integer(line, "count") : 1;
					if (count < 1) {
						throw new JsonParseException("kit 'count' must be at least 1");
					}
					kit.add(Origin.KitEntry.item(identifier(line, "item"), count));
				}
			}
		}

		List<OriginEffect> passives = effects(json, "passives");
		List<OriginEffect> drawbacks = effects(json, "drawbacks");
		Optional<String> signature = json.has("signature_ability") ? Optional.of(identifier(json, "signature_ability")) : Optional.empty();

		Map<String, Integer> tags = new LinkedHashMap<>();
		if (json.has("synergy_tags")) {
			JsonObject tagsJson = object(json.get("synergy_tags"), "'synergy_tags'");
			for (String key : tagsJson.keySet()) {
				String tag = key.indexOf(':') < 0 ? "blocksouls:" + key : key;
				checkId(tag, "synergy tag");
				int weight = integer(tagsJson, key);
				if (weight < 1) {
					throw new JsonParseException("synergy tag weight must be at least 1");
				}
				tags.put(tag, weight);
			}
		}

		return new Origin(id, icon, capabilities, kit, passives, drawbacks, signature, tags, rules(json));
	}

	private static OriginRules rules(JsonObject json) {
		if (!json.has("rules")) {
			return OriginRules.NEUTRAL;
		}
		JsonObject r = object(json.get("rules"), "'rules'");
		OriginRules n = OriginRules.NEUTRAL;
		return new OriginRules(
				r.has("revive_speed") ? positive(r, "revive_speed") : n.reviveSpeed(),
				r.has("heal_multiplier") ? nonNegative(r, "heal_multiplier") : n.healMultiplier(),
				r.has("hearts_per_boss_kill") ? integer(r, "hearts_per_boss_kill") : n.heartsPerBossKill(),
				r.has("roll_mist") ? bool(r, "roll_mist") : n.rollMist(),
				r.has("relic_cards") ? integer(r, "relic_cards") : n.relicCards(),
				r.has("relic_always_wildcard") ? bool(r, "relic_always_wildcard") : n.relicAlwaysWildcard(),
				r.has("relic_hidden") ? bool(r, "relic_hidden") : n.relicHidden(),
				r.has("ammo_drop_multiplier") ? nonNegative(r, "ammo_drop_multiplier") : n.ammoDropMultiplier(),
				r.has("altar_cost_multiplier") ? nonNegative(r, "altar_cost_multiplier") : n.altarCostMultiplier());
	}

	private static List<OriginEffect> effects(JsonObject json, String key) {
		List<OriginEffect> effects = new ArrayList<>();
		if (json.has(key)) {
			for (JsonElement entry : array(json, key)) {
				effects.add(effect(object(entry, key + " entry")));
			}
		}
		return effects;
	}

	private static OriginEffect effect(JsonObject json) {
		String type = string(json, "type");
		return switch (type) {
			case "effect" -> {
				OriginEffect.When when = OriginEffect.When.ALWAYS;
				if (json.has("when")) {
					String name = string(json, "when");
					when = OriginEffect.When.byId(name);
					if (when == null) {
						throw new JsonParseException("unknown 'when' '" + name + "' (always, in_water, in_rain, in_lava, on_fire)");
					}
				}
				yield new OriginEffect.Effect(identifier(json, "effect"), json.has("amplifier") ? integer(json, "amplifier") : 0, when);
			}
			case "attribute" -> {
				String operation = string(json, "operation");
				if (!OPERATIONS.contains(operation)) {
					throw new JsonParseException("unknown operation '" + operation + "' (" + String.join(", ", OPERATIONS) + ")");
				}
				yield new OriginEffect.Attribute(identifier(json, "attribute"), number(json, "amount"), operation);
			}
			case "trait" -> {
				int level = json.has("level") ? integer(json, "level") : 1;
				if (level < 1) {
					throw new JsonParseException("trait 'level' must be at least 1");
				}
				yield new OriginEffect.Trait(identifier(json, "trait"), level);
			}
			default -> throw new JsonParseException("unknown effect type '" + type + "' (effect, attribute, trait)");
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

	private static boolean bool(JsonObject json, String key) {
		JsonElement element = json.get(key);
		if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isBoolean()) {
			throw new JsonParseException("'" + key + "' must be true or false");
		}
		return element.getAsBoolean();
	}

	private static double number(JsonObject json, String key) {
		JsonElement element = json.get(key);
		if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
			throw new JsonParseException("'" + key + "' must be a number");
		}
		return element.getAsDouble();
	}

	private static double positive(JsonObject json, String key) {
		double value = number(json, key);
		if (value <= 0) {
			throw new JsonParseException("'" + key + "' must be positive");
		}
		return value;
	}

	private static double nonNegative(JsonObject json, String key) {
		double value = number(json, key);
		if (value < 0) {
			throw new JsonParseException("'" + key + "' must not be negative");
		}
		return value;
	}

	private static int integer(JsonObject json, String key) {
		double value = number(json, key);
		if (value != Math.rint(value)) {
			throw new JsonParseException("'" + key + "' must be a whole number");
		}
		return (int) value;
	}
}
