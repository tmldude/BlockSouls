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

import io.github.tmldude.blocksouls.world.BlockSoulsWorld;

/** Floor 4's (The Vault) wall blocks. No items: the floor builder places them and they burst to nothing. */
public final class VaultBlocks {
	public static final Block VAULT_BRICKS = wall("vault_bricks");
	public static final Block MOSSY_VAULT_BRICKS = wall("mossy_vault_bricks");
	public static final Block CRACKED_VAULT_BRICKS = wall("cracked_vault_bricks");

	private VaultBlocks() {
	}

	private static Block wall(String name) {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, BlockSoulsWorld.id(name));
		BlockBehaviour.Properties properties = BlockBehaviour.Properties.of().mapColor(MapColor.STONE).strength(-1.0F, 3600000.0F)
				.noLootTable().sound(SoundType.STONE).pushReaction(PushReaction.IMMOVEABLE).isValidSpawn((state, level, pos, type) -> false);
		return Registry.register(BuiltInRegistries.BLOCK, key, new VaultWallBlock(properties.setId(key)));
	}

	/** Forces class loading (registration) from the initializer. */
	public static void init() {
	}
}
