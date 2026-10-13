package io.github.tmldude.blocksouls.game.player.rune;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import io.github.tmldude.blocksouls.game.player.classes.ClassEffect;
import io.github.tmldude.blocksouls.game.player.classes.ClassParser;

/** Parses rune JSON (effects use the class effect vocabulary). Throws {@link JsonParseException} on malformed input. */
public final class RuneParser {
	private static final Pattern ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");

	private RuneParser() {
	}

	public static Rune parse(String id, String json) {
		JsonElement element;
		try {
			element = JsonParser.parseString(json);
		} catch (RuntimeException e) {
			throw new JsonParseException("invalid JSON: " + e.getMessage(), e);
		}
		return parse(id, element);
	}

	public static Rune parse(String id, JsonElement element) {
		if (!ID.matcher(id).matches()) {
			throw new JsonParseException("invalid rune id '" + id + "'");
		}
		if (element == null || !element.isJsonObject()) {
			throw new JsonParseException("rune must be an object");
		}
		JsonObject json = element.getAsJsonObject();
		String playerClass = identifier(json, "class");
		JsonElement costJson = json.get("cost");
		if (costJson == null || !costJson.isJsonPrimitive() || !costJson.getAsJsonPrimitive().isNumber() || costJson.getAsInt() < 0
				|| costJson.getAsDouble() != Math.rint(costJson.getAsDouble())) {
			throw new JsonParseException("'cost' must be a whole number ≥ 0");
		}
		int cost = costJson.getAsInt();

		List<String> requires = new ArrayList<>();
		for (JsonElement entry : array(json, "requires")) {
			String required = string(entry, "requires entry");
			if (!ID.matcher(required).matches()) {
				throw new JsonParseException("requires entry is not a valid id: '" + required + "'");
			}
			if (required.equals(id)) {
				throw new JsonParseException("a rune can't require itself");
			}
			requires.add(required);
		}

		Set<String> capabilities = new LinkedHashSet<>();
		for (JsonElement entry : array(json, "capabilities")) {
			String verb = string(entry, "capabilities entry");
			if (verb.indexOf(':') < 0) {
				verb = "blocksouls:" + verb;
			}
			if (!ClassParser.VERBS.contains(verb)) {
				throw new JsonParseException("unknown capability '" + verb + "' (" + String.join(", ", ClassParser.VERBS) + ")");
			}
			capabilities.add(verb);
		}

		List<ClassEffect> effects = new ArrayList<>();
		if (json.has("effects")) {
			// Parse through a throwaway class so the effect vocabulary stays in one place.
			JsonObject wrapper = new JsonObject();
			wrapper.addProperty("icon", "minecraft:stone");
			wrapper.add("passives", json.get("effects"));
			effects.addAll(ClassParser.parse("blocksouls_game:rune_effects", wrapper).passives());
		}

		List<String> mods = new ArrayList<>();
		for (JsonElement entry : array(json, "ability_mods")) {
			String mod = string(entry, "ability_mods entry");
			if (!ID.matcher(mod).matches()) {
				throw new JsonParseException("ability_mods entry is not a valid id: '" + mod + "'");
			}
			mods.add(mod);
		}
		if (capabilities.isEmpty() && effects.isEmpty() && mods.isEmpty()) {
			throw new JsonParseException("a rune must grant something (capabilities, effects or ability_mods)");
		}
		return new Rune(id, playerClass, cost, requires, capabilities, effects, mods);
	}

	private static JsonArray array(JsonObject json, String key) {
		JsonElement element = json.get(key);
		if (element == null) {
			return new JsonArray();
		}
		if (!element.isJsonArray()) {
			throw new JsonParseException("'" + key + "' must be a list");
		}
		return element.getAsJsonArray();
	}

	private static String string(JsonElement element, String what) {
		if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
			throw new JsonParseException(what + " must be a string");
		}
		return element.getAsString();
	}

	private static String identifier(JsonObject json, String key) {
		String value = string(json.get(key), "'" + key + "'");
		if (value.indexOf(':') < 0) {
			value = "blocksouls_game:" + value;
		}
		if (!ID.matcher(value).matches()) {
			throw new JsonParseException("'" + key + "' is not a valid id: '" + value + "'");
		}
		return value;
	}
}
