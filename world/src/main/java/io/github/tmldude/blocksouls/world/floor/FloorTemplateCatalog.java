package io.github.tmldude.blocksouls.world.floor;

import java.io.IOException;
import java.io.Reader;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

import com.google.gson.JsonParser;

import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.Resource;

import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.fabricmc.fabric.api.resource.v1.reloader.SimpleReloadListener;

import io.github.tmldude.blocksouls.world.BlockSoulsWorld;

/** The floor templates from data packs ({@code data/<ns>/floor_template/*.json}), reloaded with {@code /reload}. */
public final class FloorTemplateCatalog {
	private static final FileToIdConverter FILES = FileToIdConverter.json("floor_template");
	private static Map<String, FloorTemplate> templates = Map.of();

	private FloorTemplateCatalog() {
	}

	public static Collection<FloorTemplate> all() {
		return templates.values();
	}

	public static FloorTemplate get(String id) {
		return templates.get(id);
	}

	static void register() {
		ResourceLoader.get(PackType.SERVER_DATA).registerReloadListener(BlockSoulsWorld.id("floor_templates"),
				new SimpleReloadListener<Map<String, FloorTemplate>>() {
					@Override
					protected Map<String, FloorTemplate> prepare(SharedState state) {
						Map<String, FloorTemplate> loaded = new LinkedHashMap<>();
						for (Map.Entry<Identifier, Resource> entry : FILES.listMatchingResources(state.resourceManager()).entrySet()) {
							String id = FILES.fileToId(entry.getKey()).toString();
							try (Reader reader = entry.getValue().openAsReader()) {
								loaded.put(id, FloorTemplate.parse(id, JsonParser.parseReader(reader)));
							} catch (IOException | RuntimeException e) {
								BlockSoulsWorld.LOGGER.error("Skipping floor template {}: {}", id, e.getMessage());
							}
						}
						return loaded;
					}

					@Override
					protected void apply(Map<String, FloorTemplate> prepared, SharedState state) {
						templates = Map.copyOf(prepared);
						BlockSoulsWorld.LOGGER.info("Loaded {} floor templates", prepared.size());
					}
				});
	}
}
