package io.github.tmldude.blocksouls.game.player.classes;

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

/** Every playerClass loaded from {@code data/<ns>/classes/*.json}, reloaded with {@code /reload}. */
public final class ClassCatalog {
	private static final FileToIdConverter FILES = FileToIdConverter.json("classes");

	private static volatile Map<String, PlayerClass> classes = Map.of();

	private ClassCatalog() {
	}

	public static void register() {
		ResourceLoader.get(PackType.SERVER_DATA).registerReloadListener(
				Identifier.fromNamespaceAndPath(BlockSoulsGame.MOD_ID, "classes"),
				new SimpleReloadListener<Map<String, PlayerClass>>() {
					@Override
					protected Map<String, PlayerClass> prepare(SharedState state) {
						Map<String, PlayerClass> loaded = new LinkedHashMap<>();
						for (Map.Entry<Identifier, Resource> entry : FILES.listMatchingResources(state.resourceManager()).entrySet()) {
							String id = FILES.fileToId(entry.getKey()).toString();
							try (Reader reader = entry.getValue().openAsReader()) {
								loaded.put(id, ClassParser.parse(id, JsonParser.parseReader(reader)));
							} catch (IOException | RuntimeException e) {
								BlockSoulsGame.LOGGER.error("Skipping playerClass {}: {}", id, e.getMessage());
							}
						}
						return loaded;
					}

					@Override
					protected void apply(Map<String, PlayerClass> prepared, SharedState state) {
						prepared.values().forEach(ClassCatalog::warnUnknownIds);
						set(prepared);
						BlockSoulsGame.LOGGER.info("Loaded {} classes", prepared.size());
					}
				});
	}

	public static Collection<PlayerClass> all() {
		return classes.values();
	}

	public static PlayerClass get(String id) {
		return classes.get(id);
	}

	public static PlayerClass get(Identifier id) {
		return classes.get(id.toString());
	}

	/** Replaces the catalog (reload listener, tests). */
	public static void set(Map<String, PlayerClass> newOrigins) {
		classes = Collections.unmodifiableMap(new LinkedHashMap<>(newOrigins));
	}

	/** Whether some mod ships the sprite icon's PNG ({@code <ns>:class/<name>}). */
	public static boolean spriteExists(String icon) {
		int colon = icon.indexOf(':');
		String path = "assets/" + icon.substring(0, colon) + "/textures/gui/sprites/" + icon.substring(colon + 1) + ".png";
		return FabricLoader.getInstance().getAllMods().stream().anyMatch(mod -> mod.findPath(path).isPresent());
	}

	/** Ids other modules haven't registered are only a warning: the pack ships every module, isolated runs don't. */
	private static void warnUnknownIds(PlayerClass playerClass) {
		String icon = playerClass.icon();
		boolean sprite = icon.substring(icon.indexOf(':') + 1).startsWith("class/");
		check(playerClass, "icon", sprite ? spriteExists(icon) : BuiltInRegistries.ITEM.containsKey(Identifier.parse(icon)), icon);
		for (PlayerClass.KitEntry entry : playerClass.kit()) {
			entry.item().ifPresent(item -> check(playerClass, "kit item", BuiltInRegistries.ITEM.containsKey(Identifier.parse(item)), item));
		}
		for (ClassEffect effect : playerClass.effects()) {
			switch (effect) {
				case ClassEffect.Effect e -> check(playerClass, "effect", BuiltInRegistries.MOB_EFFECT.containsKey(Identifier.parse(e.effect())), e.effect());
				case ClassEffect.Attribute a -> check(playerClass, "attribute", BuiltInRegistries.ATTRIBUTE.containsKey(Identifier.parse(a.attribute())), a.attribute());
				case ClassEffect.Trait t -> {
				}
			}
		}
	}

	private static void check(PlayerClass playerClass, String what, boolean exists, String id) {
		if (!exists) {
			BlockSoulsGame.LOGGER.warn("PlayerClass {}: unknown {} {}", playerClass.id(), what, id);
		}
	}
}
