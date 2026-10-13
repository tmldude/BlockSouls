package io.github.tmldude.blocksouls.game.player.rune;

import java.io.IOException;
import java.io.Reader;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonParser;

import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.Resource;

import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.fabricmc.fabric.api.resource.v1.reloader.SimpleReloadListener;

import io.github.tmldude.blocksouls.game.BlockSoulsGame;

/** Every rune loaded from {@code data/<ns>/runes/*.json}, reloaded with {@code /reload}. */
public final class RuneCatalog {
	private static final FileToIdConverter FILES = FileToIdConverter.json("runes");

	private static volatile Map<String, Rune> runes = Map.of();

	private RuneCatalog() {
	}

	public static void register() {
		ResourceLoader.get(PackType.SERVER_DATA).registerReloadListener(
				Identifier.fromNamespaceAndPath(BlockSoulsGame.MOD_ID, "runes"),
				new SimpleReloadListener<Map<String, Rune>>() {
					@Override
					protected Map<String, Rune> prepare(SharedState state) {
						Map<String, Rune> loaded = new LinkedHashMap<>();
						for (Map.Entry<Identifier, Resource> entry : FILES.listMatchingResources(state.resourceManager()).entrySet()) {
							String id = FILES.fileToId(entry.getKey()).toString();
							try (Reader reader = entry.getValue().openAsReader()) {
								loaded.put(id, RuneParser.parse(id, JsonParser.parseReader(reader)));
							} catch (IOException | RuntimeException e) {
								BlockSoulsGame.LOGGER.error("Skipping rune {}: {}", id, e.getMessage());
							}
						}
						return loaded;
					}

					@Override
					protected void apply(Map<String, Rune> prepared, SharedState state) {
						for (Rune rune : prepared.values()) {
							for (String required : rune.requires()) {
								if (!prepared.containsKey(required)) {
									BlockSoulsGame.LOGGER.warn("Rune {} requires unknown rune {}", rune.id(), required);
								}
							}
						}
						set(prepared);
						BlockSoulsGame.LOGGER.info("Loaded {} runes", prepared.size());
					}
				});
	}

	public static Collection<Rune> all() {
		return runes.values();
	}

	public static Rune get(String id) {
		return runes.get(id);
	}

	public static Rune get(Identifier id) {
		return runes.get(id.toString());
	}

	/** The tree of one class, in file order. */
	public static List<Rune> forClass(String classId) {
		return runes.values().stream().filter(rune -> rune.playerClass().equals(classId)).toList();
	}

	/** Replaces the catalog (reload listener, tests). */
	public static void set(Map<String, Rune> newRunes) {
		runes = Collections.unmodifiableMap(new LinkedHashMap<>(newRunes));
	}
}
