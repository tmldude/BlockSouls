package io.github.tmldude.blocksouls.game.player.origin;

import java.io.IOException;
import java.io.Reader;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import com.google.gson.JsonParser;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.Resource;

import net.fabricmc.fabric.api.resource.v1.reloader.SimpleReloadListener;

import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.fabricmc.loader.api.FabricLoader;

import io.github.tmldude.blocksouls.game.BlockSoulsGame;

/** Every origin loaded from {@code data/<ns>/origins/*.json}, reloaded with {@code /reload}. */
public final class OriginCatalog {
	private static final FileToIdConverter FILES = FileToIdConverter.json("origins");

	private static volatile Map<String, Origin> origins = Map.of();

	private OriginCatalog() {
	}

	public static void register() {
		ResourceLoader.get(PackType.SERVER_DATA).registerReloadListener(
				Identifier.fromNamespaceAndPath(BlockSoulsGame.MOD_ID, "origins"),
				new SimpleReloadListener<Map<String, Origin>>() {
					@Override
					protected Map<String, Origin> prepare(SharedState state) {
						Map<String, Origin> loaded = new LinkedHashMap<>();
						for (Map.Entry<Identifier, Resource> entry : FILES.listMatchingResources(state.resourceManager()).entrySet()) {
							String id = FILES.fileToId(entry.getKey()).toString();
							try (Reader reader = entry.getValue().openAsReader()) {
								loaded.put(id, OriginParser.parse(id, JsonParser.parseReader(reader)));
							} catch (IOException | RuntimeException e) {
								BlockSoulsGame.LOGGER.error("Skipping origin {}: {}", id, e.getMessage());
							}
						}
						return loaded;
					}

					@Override
					protected void apply(Map<String, Origin> prepared, SharedState state) {
						prepared.values().forEach(OriginCatalog::warnUnknownIds);
						set(prepared);
						BlockSoulsGame.LOGGER.info("Loaded {} origins", prepared.size());
					}
				});
	}

	public static Collection<Origin> all() {
		return origins.values();
	}

	public static Origin get(String id) {
		return origins.get(id);
	}

	public static Origin get(Identifier id) {
		return origins.get(id.toString());
	}

	/** Replaces the catalog (reload listener, tests). */
	public static void set(Map<String, Origin> newOrigins) {
		origins = Collections.unmodifiableMap(new LinkedHashMap<>(newOrigins));
	}

	/** Whether some mod ships the sprite icon's PNG ({@code <ns>:origin/<name>}). */
	public static boolean spriteExists(String icon) {
		int colon = icon.indexOf(':');
		String path = "assets/" + icon.substring(0, colon) + "/textures/gui/sprites/" + icon.substring(colon + 1) + ".png";
		return FabricLoader.getInstance().getAllMods().stream().anyMatch(mod -> mod.findPath(path).isPresent());
	}

	/** Ids other modules haven't registered are only a warning: the pack ships every module, isolated runs don't. */
	private static void warnUnknownIds(Origin origin) {
		String icon = origin.icon();
		boolean sprite = icon.substring(icon.indexOf(':') + 1).startsWith("origin/");
		check(origin, "icon", sprite ? spriteExists(icon) : BuiltInRegistries.ITEM.containsKey(Identifier.parse(icon)), icon);
		for (Origin.KitEntry entry : origin.kit()) {
			entry.item().ifPresent(item -> check(origin, "kit item", BuiltInRegistries.ITEM.containsKey(Identifier.parse(item)), item));
		}
		for (OriginEffect effect : origin.effects()) {
			switch (effect) {
				case OriginEffect.Effect e -> check(origin, "effect", BuiltInRegistries.MOB_EFFECT.containsKey(Identifier.parse(e.effect())), e.effect());
				case OriginEffect.Attribute a -> check(origin, "attribute", BuiltInRegistries.ATTRIBUTE.containsKey(Identifier.parse(a.attribute())), a.attribute());
				case OriginEffect.Trait t -> {
				}
			}
		}
	}

	private static void check(Origin origin, String what, boolean exists, String id) {
		if (!exists) {
			BlockSoulsGame.LOGGER.warn("Origin {}: unknown {} {}", origin.id(), what, id);
		}
	}
}
