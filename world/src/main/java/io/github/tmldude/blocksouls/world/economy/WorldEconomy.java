package io.github.tmldude.blocksouls.world.economy;

import java.util.EnumMap;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.AABB;

import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityType;

import io.github.tmldude.blocksouls.common.CommonIds;
import io.github.tmldude.blocksouls.common.economy.CampObject;
import io.github.tmldude.blocksouls.common.economy.CampObjects;

/**
 * The run economy's world objects (PLAN2 §3c/§3d): four unbreakable blocks and the shopkeeper entity, and the
 * {@link CampObjects} placer the game calls to put them down. What using one does lives in the game module.
 */
public final class WorldEconomy {
	public static final Block BLOOD_ALTAR = block(CampObject.BLOOD_ALTAR, MapColor.COLOR_RED, SoundType.BONE_BLOCK, 6);
	public static final Block CURSED_CHEST = block(CampObject.CURSED_CHEST, MapColor.COLOR_PURPLE, SoundType.WOOD, 3);
	public static final Block CHALLENGE_PYLON = block(CampObject.CHALLENGE_PYLON, MapColor.COLOR_ORANGE, SoundType.DEEPSLATE, 12);
	public static final Block CAMP_HORN = block(CampObject.CAMP_HORN, MapColor.WOOD, SoundType.WOOD, 0);

	public static final EntityType<Shopkeeper> SHOPKEEPER = Registry.register(BuiltInRegistries.ENTITY_TYPE,
			ResourceKey.create(Registries.ENTITY_TYPE, CommonIds.SHOPKEEPER),
			FabricEntityType.Builder.createMob(Shopkeeper::new, MobCategory.MISC, mob -> mob.defaultAttributes(Shopkeeper::createAttributes))
					.sized(0.6F, 1.95F).eyeHeight(1.62F).clientTrackingRange(10)
					.build(ResourceKey.create(Registries.ENTITY_TYPE, CommonIds.SHOPKEEPER)));

	/** Other shopkeepers this close to a new one are removed (the camp moved; one shopkeeper per camp). */
	private static final double SHOPKEEPER_CLEAR_RADIUS = 48.0;

	private static final Map<CampObject, Block> BLOCKS = new EnumMap<>(Map.of(CampObject.BLOOD_ALTAR, BLOOD_ALTAR,
			CampObject.CURSED_CHEST, CURSED_CHEST, CampObject.CHALLENGE_PYLON, CHALLENGE_PYLON, CampObject.CAMP_HORN, CAMP_HORN));

	private WorldEconomy() {
	}

	private static Block block(CampObject object, MapColor color, SoundType sound, int light) {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, object.id());
		BlockBehaviour.Properties properties = BlockBehaviour.Properties.of().mapColor(color).sound(sound).strength(-1.0F, 3600000.0F)
				.noLootTable().pushReaction(PushReaction.IMMOVEABLE).lightLevel(s -> light).setId(key);
		return Registry.register(BuiltInRegistries.BLOCK, key, new EconomyBlock(object, properties));
	}

	public static void init() {
		CampObjects.setPlacer(WorldEconomy::place);
	}

	/** The placer: a block replaces whatever's at {@code pos}; the shopkeeper stands on it, replacing nearby ones. */
	static boolean place(ServerLevel level, BlockPos pos, CampObject object) {
		if (!level.isInWorldBounds(pos)) {
			return false;
		}
		Block block = BLOCKS.get(object);
		if (block != null) {
			return level.setBlockAndUpdate(pos, block.defaultBlockState());
		}
		for (Shopkeeper old : level.getEntitiesOfClass(Shopkeeper.class, new AABB(pos).inflate(SHOPKEEPER_CLEAR_RADIUS))) {
			old.discard();
		}
		return SHOPKEEPER.spawn(level, pos, EntitySpawnReason.EVENT) != null;
	}
}
