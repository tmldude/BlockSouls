package io.github.tmldude.blocksouls.world.floor;

import java.util.function.Function;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

import io.github.tmldude.blocksouls.common.CommonIds;

/** The floors' blocks: resource nodes, herbs and the exit portal. None has an item; the floor builder places them. */
public final class FloorBlocks {
	public static final Block SCRAP_NODE = register(CommonIds.SCRAP_NODE, NodeBlock::new, node(MapColor.COLOR_BROWN, SoundType.CHAIN));
	public static final Block GUNMETAL_NODE = register(CommonIds.GUNMETAL_NODE, NodeBlock::new, node(MapColor.METAL, SoundType.METAL));
	// v2-floor5-blight: the Blighted Surface's rare node and its herb (glowing, so they read through the fog).
	public static final Block BLIGHT_CRYSTAL_NODE = register(CommonIds.BLIGHT_CRYSTAL_NODE, NodeBlock::new,
			node(MapColor.COLOR_PURPLE, SoundType.AMETHYST).lightLevel(state -> 6).noOcclusion());
	public static final Block GHOSTCAP = register(CommonIds.GHOSTCAP, props -> new HerbBlock(props, 1),
			node(MapColor.TERRACOTTA_WHITE, SoundType.FUNGUS).lightLevel(state -> 9).noCollision().noOcclusion());
	/** v2-floor2-infernal: the Infernal floor's rare node and herb. */
	public static final Block CINDER_ORE = register(CommonIds.CINDER_ORE, NodeBlock::new,
			node(MapColor.FIRE, SoundType.NETHER_ORE).lightLevel(state -> 6));
	public static final Block ASHCAP = register(CommonIds.ASHCAP, AshcapBlock::new,
			node(MapColor.CRIMSON_NYLIUM, SoundType.FUNGUS).noCollision().noOcclusion().lightLevel(state -> 9));
	public static final Block FLOOR_PORTAL = register(CommonIds.FLOOR_PORTAL, FloorPortalBlock::new,
			BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_PURPLE).strength(-1.0F, 3600000.0F).noLootTable().noOcclusion()
					.pushReaction(PushReaction.IMMOVEABLE).sound(SoundType.GLASS)
					.lightLevel(state -> state.getValue(FloorPortalBlock.LIT) ? 11 : 3));

	private FloorBlocks() {
	}

	private static BlockBehaviour.Properties node(MapColor color, SoundType sound) {
		return BlockBehaviour.Properties.of().mapColor(color).strength(-1.0F, 3600000.0F).noLootTable().sound(sound)
				.pushReaction(PushReaction.IMMOVEABLE);
	}

	private static Block register(Identifier id, Function<BlockBehaviour.Properties, Block> factory, BlockBehaviour.Properties properties) {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, id);
		return Registry.register(BuiltInRegistries.BLOCK, key, factory.apply(properties.setId(key)));
	}

	/** Forces class loading (registration) from the initializer. */
	public static void init() {
	}
}
