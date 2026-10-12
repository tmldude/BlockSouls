package io.github.tmldude.blocksouls.game.player.boon;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntSupplier;

import com.google.gson.JsonParser;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.Resource;

import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.fabricmc.fabric.api.resource.v1.reloader.SimpleReloadListener;
import net.fabricmc.loader.api.FabricLoader;

import io.github.tmldude.blocksouls.common.synergy.SynergyTags;
import io.github.tmldude.blocksouls.game.BlockSoulsGame;
import io.github.tmldude.blocksouls.game.player.boon.rule.BoonRules;

/**
 * Every boon, curse and trinket loaded from {@code data/<ns>/boons|curses|trinkets/*.json}, reloaded with
 * {@code /reload}. One id space: a curse and a boon can't share an id.
 */
public final class BoonCatalog {
	private static volatile Map<String, Boon> definitions = Map.of();
	private static IntSupplier defaultLevels = () -> 1;

	private BoonCatalog() {
	}

	/** @param defaultBoonLevels levels an ordinary boon gets when its JSON doesn't say (config) */
	public static void register(IntSupplier defaultBoonLevels) {
		defaultLevels = defaultBoonLevels;
		ResourceLoader.get(PackType.SERVER_DATA).registerReloadListener(
				Identifier.fromNamespaceAndPath(BlockSoulsGame.MOD_ID, "boons"),
				new SimpleReloadListener<Map<String, Boon>>() {
					@Override
					protected Map<String, Boon> prepare(SharedState state) {
						Map<String, Boon> loaded = new LinkedHashMap<>();
						for (Boon.Kind kind : Boon.Kind.values()) {
							FileToIdConverter files = FileToIdConverter.json(kind.folder());
							for (Map.Entry<Identifier, Resource> entry : files.listMatchingResources(state.resourceManager()).entrySet()) {
								String id = files.fileToId(entry.getKey()).toString();
								try (Reader reader = entry.getValue().openAsReader()) {
									Boon boon = BoonParser.parse(id, JsonParser.parseReader(reader), BoonCatalog::spriteExists, kind,
											defaultLevels.getAsInt());
									Boon clash = loaded.putIfAbsent(id, boon);
									if (clash != null) {
										BlockSoulsGame.LOGGER.error("Skipping {} {}: a {} already has that id", kind.id(), id, clash.kind().id());
									}
								} catch (IOException | RuntimeException e) {
									BlockSoulsGame.LOGGER.error("Skipping {} {}: {}", kind.id(), id, e.getMessage());
								}
							}
						}
						return loaded;
					}

					@Override
					protected void apply(Map<String, Boon> prepared, SharedState state) {
						prepared.values().forEach(BoonCatalog::warnUnknownIds);
						set(prepared);
						BlockSoulsGame.LOGGER.info("Loaded {} boons, {} curses, {} trinkets", all().size(), curses().size(), trinkets().size());
					}
				});
	}

	/** Every boon (not curses or trinkets): what relics offer. */
	public static Collection<Boon> all() {
		return ofKind(Boon.Kind.BOON);
	}

	public static Collection<Boon> curses() {
		return ofKind(Boon.Kind.CURSE);
	}

	public static Collection<Boon> trinkets() {
		return ofKind(Boon.Kind.TRINKET);
	}

	/** Boons, curses and trinkets together. */
	public static Collection<Boon> everything() {
		return definitions.values();
	}

	public static Collection<Boon> ofKind(Boon.Kind kind) {
		List<Boon> out = new ArrayList<>();
		for (Boon boon : definitions.values()) {
			if (boon.kind() == kind) {
				out.add(boon);
			}
		}
		return out;
	}

	public static Boon get(String id) {
		return definitions.get(id);
	}

	public static Boon get(Identifier id) {
		return definitions.get(id.toString());
	}

	/** Replaces the catalog (reload listener, tests). */
	public static void set(Map<String, Boon> newDefinitions) {
		definitions = Collections.unmodifiableMap(new LinkedHashMap<>(newDefinitions));
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
		for (String tag : boon.tags()) {
			check(boon, "synergy tag", SynergyTags.isKnown(Identifier.parse(tag)), tag);
		}
		if (boon.keystone() && boon.tags().stream().noneMatch(tag -> SynergyTags.category(Identifier.parse(tag)) == SynergyTags.Category.TRIGGER)) {
			BlockSoulsGame.LOGGER.warn("Keystone {} has no trigger tag, so nothing excludes it", boon.id());
		}
		for (BoonEffect effect : boon.effects()) {
			switch (effect) {
				case BoonEffect.Effect e -> check(boon, "effect", BuiltInRegistries.MOB_EFFECT.containsKey(Identifier.parse(e.effect())), e.effect());
				case BoonEffect.Attribute a -> check(boon, "attribute", BuiltInRegistries.ATTRIBUTE.containsKey(Identifier.parse(a.attribute())), a.attribute());
				case BoonEffect.Item i -> check(boon, "item", BuiltInRegistries.ITEM.containsKey(Identifier.parse(i.item())), i.item());
				case BoonEffect.Rule r -> check(boon, "rule", BoonRules.has(Identifier.parse(r.rule())), r.rule());
				case BoonEffect.Trait t -> {
				}
			}
		}
	}

	private static void check(Boon boon, String what, boolean exists, String id) {
		if (!exists) {
			BlockSoulsGame.LOGGER.warn("{} {}: unknown {} {}", boon.kind().id(), boon.id(), what, id);
		}
	}
}
