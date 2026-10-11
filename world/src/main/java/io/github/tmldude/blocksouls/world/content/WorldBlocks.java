package io.github.tmldude.blocksouls.world.content;

import java.util.function.Function;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.valueproviders.ConstantInt;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DropExperienceBlock;
import net.minecraft.world.level.block.GlowLichenBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

import io.github.tmldude.blocksouls.world.BlockSoulsWorld;

public final class WorldBlocks {
	public static final Block GUNMETAL_ORE = register("gunmetal_ore", p -> new DropExperienceBlock(ConstantInt.of(0), p),
			BlockBehaviour.Properties.of().mapColor(MapColor.STONE).instrument(NoteBlockInstrument.BASEDRUM)
					.requiresCorrectToolForDrops().strength(3.0F, 3.0F));
	public static final Block DEEPSLATE_GUNMETAL_ORE = register("deepslate_gunmetal_ore", p -> new DropExperienceBlock(ConstantInt.of(0), p),
			BlockBehaviour.Properties.of().mapColor(MapColor.DEEPSLATE).instrument(NoteBlockInstrument.BASEDRUM)
					.requiresCorrectToolForDrops().strength(4.5F, 3.0F).sound(SoundType.DEEPSLATE));
	public static final Block RAW_GUNMETAL_BLOCK = register("raw_gunmetal_block", Block::new,
			BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GRAY).instrument(NoteBlockInstrument.BASEDRUM)
					.requiresCorrectToolForDrops().strength(5.0F, 6.0F));
	public static final Block GUNMETAL_BLOCK = register("gunmetal_block", Block::new,
			BlockBehaviour.Properties.of().mapColor(MapColor.METAL).instrument(NoteBlockInstrument.IRON_XYLOPHONE)
					.requiresCorrectToolForDrops().strength(5.0F, 6.0F).sound(SoundType.METAL));

	/** A heap of junk on the surface; breaks into scrap by hand. */
	public static final Block SCRAP_PILE = register("scrap_pile", ScrapPileBlock::new,
			BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BROWN).strength(0.8F).sound(SoundType.CHAIN)
					.noOcclusion().pushReaction(PushReaction.POPPED));

	public static final Block BLIGHTED_NETHERRACK = register("blighted_netherrack", Block::new,
			BlockBehaviour.Properties.of().mapColor(MapColor.CRIMSON_NYLIUM).instrument(NoteBlockInstrument.BASEDRUM)
					.requiresCorrectToolForDrops().strength(0.5F).sound(SoundType.WART_BLOCK));
	public static final Block BLIGHT_GROWTH = register("blight_growth", BlightGrowthBlock::new,
			BlockBehaviour.Properties.of().mapColor(MapColor.CRIMSON_NYLIUM).replaceable().noCollision().instabreak()
					.sound(SoundType.ROOTS).offsetType(BlockBehaviour.OffsetType.XZ).pushReaction(PushReaction.POPPED)
					.lightLevel(s -> 2));
	public static final Block BLIGHT_VEINS = register("blight_veins", GlowLichenBlock::new,
			BlockBehaviour.Properties.of().mapColor(MapColor.CRIMSON_NYLIUM).replaceable().noCollision().strength(0.2F)
					.sound(SoundType.SCULK_VEIN).lightLevel(GlowLichenBlock.emission(3)).pushReaction(PushReaction.POPPED));
	/** Glowing, fleshy node in the Nether; drops one blight core (needs an iron pickaxe). */
	public static final Block BLIGHT_HEART = register("blight_heart", Block::new,
			BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_RED).instrument(NoteBlockInstrument.BASEDRUM)
					.requiresCorrectToolForDrops().strength(3.0F, 6.0F).sound(SoundType.SCULK_CATALYST).lightLevel(s -> 10));

	private WorldBlocks() {
	}

	private static Block register(String name, Function<BlockBehaviour.Properties, Block> factory, BlockBehaviour.Properties properties) {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, BlockSoulsWorld.id(name));
		return Registry.register(BuiltInRegistries.BLOCK, key, factory.apply(properties.setId(key)));
	}

	/** Forces class loading (registration) from the initializer. */
	public static void init() {
	}
}
