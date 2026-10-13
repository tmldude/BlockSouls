package io.github.tmldude.blocksouls.common;

import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

/** Tags shared across modules (namespace {@code blocksouls}). Each owning module fills them via data. */
public final class CommonTags {
	public static final TagKey<Item> GUNS = item("guns");
	public static final TagKey<Item> AMMO = item("ammo");
	/**
	 * Shopkeeper boards (docs/decisions/v2-boards.md): a BlockSouls recipe whose result is in {@code brew_board}
	 * goes on the brew board, in {@code forge_board} on the forge board. Untagged results default by kind
	 * (food, consumable or ammo → brew, everything else → forge). Any module may add its items.
	 */
	public static final TagKey<Item> FORGE_BOARD = item("forge_board");
	public static final TagKey<Item> BREW_BOARD = item("brew_board");

	/** Player-buildable blocks that wave mobs may break (filled by mobs). Never natural terrain. */
	public static final TagKey<Block> WAVE_BREAKABLE = block("wave_breakable");

	/** Mobs that only exist as part of waves (filled by mobs). */
	public static final TagKey<EntityType<?>> WAVE_MOBS = entity("wave_mobs");
	/** Wave mobs that survive dawn instead of burning (filled by mobs). */
	public static final TagKey<EntityType<?>> ELITES = entity("elites");
	/** All bosses (filled by bosses). Guns' boss-damage perks key off this. */
	public static final TagKey<EntityType<?>> BOSSES = entity("bosses");

	private CommonTags() {
	}

	private static TagKey<Item> item(String path) {
		return TagKey.create(Registries.ITEM, CommonIds.id("blocksouls", path));
	}

	private static TagKey<Block> block(String path) {
		return TagKey.create(Registries.BLOCK, CommonIds.id("blocksouls", path));
	}

	private static TagKey<EntityType<?>> entity(String path) {
		return TagKey.create(Registries.ENTITY_TYPE, CommonIds.id("blocksouls", path));
	}
}
