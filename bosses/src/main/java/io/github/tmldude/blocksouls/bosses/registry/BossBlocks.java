package io.github.tmldude.blocksouls.bosses.registry;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

import io.github.tmldude.blocksouls.bosses.arena.FogWallBlock;
import io.github.tmldude.blocksouls.common.CommonIds;

public final class BossBlocks {
	/** The arena wall. No item, unbreakable, drops nothing; only the framework places and removes it. */
	public static final Block FOG_WALL = register("fog_wall", BlockBehaviour.Properties.of()
			.mapColor(MapColor.COLOR_LIGHT_GRAY)
			.strength(-1.0f, 3600000.0f)
			.noLootTable()
			.noOcclusion()
			.dynamicShape()
			.sound(SoundType.WOOL)
			.lightLevel(state -> 4)
			.pushReaction(PushReaction.IMMOVEABLE)
			.isValidSpawn((state, level, pos, type) -> false)
			.isRedstoneConductor((state, level, pos) -> false)
			.isSuffocating((state, level, pos) -> false)
			.isViewBlocking((state, level, pos, box) -> false));

	private BossBlocks() {
	}

	private static Block register(String name, BlockBehaviour.Properties properties) {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, CommonIds.id(CommonIds.BOSSES, name));
		return Registry.register(BuiltInRegistries.BLOCK, key, new FogWallBlock(properties.setId(key)));
	}

	public static void init() {
	}
}
