package io.github.tmldude.blocksouls.game.player.boon;

import java.io.IOException;
import java.io.Reader;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.Resource;

import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.fabricmc.fabric.api.resource.v1.reloader.SimpleReloadListener;
import net.fabricmc.loader.api.FabricLoader;

import io.github.tmldude.blocksouls.game.BlockSoulsGame;

/** Every boon loaded from {@code data/<ns>/boons/*.json}, reloaded with {@code /reload}. */
public final class BoonCatalog {
	private static final FileToIdConverter FILES = FileToIdConverter.json("boons");

	private static volatile Map<String, Boon> boons = Map.of();

	private BoonCatalog() {
	}

	public static void register() {
		ResourceLoader.get(PackType.SERVER_DATA).registerReloadListener(
				Identifier.fromNamespaceAndPath(BlockSoulsGame.MOD_ID, "boons"),
				new SimpleReloadListener<Map<String, Boon>>() {
					@Override
					protected Map<String, Boon> prepare(SharedState state) {
						Map<String, Boon> loaded = new LinkedHashMap<>();
						for (Map.Entry<Identifier, Resource> entry : FILES.listMatchingResources(state.resourceManager()).entrySet()) {
							String id = FILES.fileToId(entry.getKey()).toString();
							try (Reader reader = entry.getValue().openAsReader()) {
								loaded.put(id, BoonParser.parse(id, JsonParser.parseReader(reader), BoonCatalog::spriteExists));
							} catch (IOException | RuntimeException e) {
								BlockSoulsGame.LOGGER.error("Skipping boon {}: {}", id, e.getMessage());
							}
						}
						return loaded;
					}

					@Override
					protected void apply(Map<String, Boon> prepared, SharedState state) {
						prepared.values().forEach(BoonCatalog::warnUnknownIds);
						set(prepared);
						BlockSoulsGame.LOGGER.info("Loaded {} boons", prepared.size());
					}
				});
	}

	public static Collection<Boon> all() {
		return boons.values();
	}

	public static Boon get(String id) {
		return boons.get(id);
	}

	public static Boon get(Identifier id) {
		return boons.get(id.toString());
	}

	/** Replaces the catalog (reload listener, tests). */
	public static void set(Map<String, Boon> newBoons) {
		boons = Collections.unmodifiableMap(new LinkedHashMap<>(newBoons));
	}

	/**
	 * Whether some mod ships the sprite icon's PNG. The server can't see client resource packs, so a datapack boon
	 * with its own sprite needs a mod (or an item icon).
	 */
	public static boolean spriteExists(String icon) {
		String path = BoonIcons.texturePath(icon);
		return FabricLoader.getInstance().getAllMods().stream().anyMatch(mod -> mod.findPath(path).isPresent());
	}

	/** Whether the boon's icon draws as something real: an existing sprite or a registered item. */
	public static boolean iconResolves(Boon boon) {
		return BoonIcons.isSprite(boon.icon())
				? spriteExists(boon.icon())
				: BuiltInRegistries.ITEM.containsKey(Identifier.parse(boon.icon()));
	}

	/** Ids other modules haven't registered are only a warning: the pack ships every module, isolated runs don't. */
	private static void warnUnknownIds(Boon boon) {
		check(boon, "icon", iconResolves(boon), boon.icon());
		for (BoonEffect effect : boon.effects()) {
			switch (effect) {
				case BoonEffect.Effect e -> check(boon, "effect", BuiltInRegistries.MOB_EFFECT.containsKey(Identifier.parse(e.effect())), e.effect());
				case BoonEffect.Attribute a -> check(boon, "attribute", BuiltInRegistries.ATTRIBUTE.containsKey(Identifier.parse(a.attribute())), a.attribute());
				case BoonEffect.Item i -> check(boon, "item", BuiltInRegistries.ITEM.containsKey(Identifier.parse(i.item())), i.item());
				case BoonEffect.Trait t -> {
				}
			}
		}
	}

	private static void check(Boon boon, String what, boolean exists, String id) {
		if (!exists) {
			BlockSoulsGame.LOGGER.warn("Boon {}: unknown {} {}", boon.id(), what, id);
		}
	}
}
