package io.github.tmldude.blocksouls.world.floor;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

import io.github.tmldude.blocksouls.common.CommonIds;
import io.github.tmldude.blocksouls.world.BlockSoulsWorld;

/**
 * Floor 4's (The Vault) blocks: the wall bricks, the deep iron node set into them and the cave moss herb. No items: the
 * floor builder places them; walls burst to nothing, the node and herb pop their loot tables ({@code nodes/<block>}).
 */
public final class VaultBlocks {
	public static final Block VAULT_BRICKS = wall("vault_bricks");
	public static final Block MOSSY_VAULT_BRICKS = wall("mossy_vault_bricks");
	public static final Block CRACKED_VAULT_BRICKS = wall("cracked_vault_bricks");
	public static final NodeBlock DEEP_IRON_NODE = register(CommonIds.DEEP_IRON_NODE.getPath(), new NodeBlock(BlockBehaviour.Properties.of()
			.mapColor(MapColor.DEEPSLATE).strength(-1.0F, 3600000.0F).noLootTable().sound(SoundType.DEEPSLATE)
			.pushReaction(PushReaction.IMMOVEABLE).setId(key(CommonIds.DEEP_IRON_NODE.getPath()))));
	/** Faintly glowing, so it shows in the dark rooms. */
	public static final HerbBlock CAVE_MOSS_PATCH = register(CommonIds.CAVE_MOSS_PATCH.getPath(), new HerbBlock(BlockBehaviour.Properties.of()
			.mapColor(MapColor.COLOR_GREEN).strength(-1.0F, 3600000.0F).noLootTable().noCollision().sound(SoundType.MOSS)
			.offsetType(BlockBehaviour.OffsetType.XZ).pushReaction(PushReaction.IMMOVEABLE).lightLevel(state -> 7)
			.setId(key(CommonIds.CAVE_MOSS_PATCH.getPath()))));

	private VaultBlocks() {
	}

	private static Block wall(String name) {
		BlockBehaviour.Properties properties = BlockBehaviour.Properties.of().mapColor(MapColor.STONE).strength(-1.0F, 3600000.0F)
				.noLootTable().sound(SoundType.STONE).pushReaction(PushReaction.IMMOVEABLE).isValidSpawn((state, level, pos, type) -> false);
		return register(name, new VaultWallBlock(properties.setId(key(name))));
	}

	private static ResourceKey<Block> key(String name) {
		return ResourceKey.create(Registries.BLOCK, BlockSoulsWorld.id(name));
	}

	private static <T extends Block> T register(String name, T block) {
		return Registry.register(BuiltInRegistries.BLOCK, key(name), block);
	}

	/** Forces class loading (registration) from the initializer. */
	public static void init() {
	}
}
