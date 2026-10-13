package io.github.tmldude.blocksouls.mobs.registry;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.levelgen.Heightmap;

import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityType;

import io.github.tmldude.blocksouls.common.CommonIds;
import io.github.tmldude.blocksouls.mobs.BlockSoulsMobs;
import io.github.tmldude.blocksouls.mobs.MobsConfig;
import io.github.tmldude.blocksouls.mobs.entity.AshHound;
import io.github.tmldude.blocksouls.mobs.entity.Bloater;
import io.github.tmldude.blocksouls.mobs.entity.Bulwark;
import io.github.tmldude.blocksouls.mobs.entity.Brute;
import io.github.tmldude.blocksouls.mobs.entity.Crawler;
import io.github.tmldude.blocksouls.mobs.entity.Debris;
import io.github.tmldude.blocksouls.mobs.entity.HostileMob;
import io.github.tmldude.blocksouls.mobs.entity.InfectedPiglin;
import io.github.tmldude.blocksouls.mobs.entity.MutantBrute;
import io.github.tmldude.blocksouls.mobs.entity.Screamer;
import io.github.tmldude.blocksouls.mobs.entity.Shambler;
import io.github.tmldude.blocksouls.mobs.entity.SpitGlob;
import io.github.tmldude.blocksouls.mobs.entity.Spitter;
import io.github.tmldude.blocksouls.mobs.entity.WaveMob;

/** Entity types (ids fixed in {@link CommonIds}) and their spawn eggs. */
public final class MobEntities {
	private static final MobsConfig C = BlockSoulsMobs.CONFIG;

	public static final EntityType<Shambler> SHAMBLER = mob(CommonIds.SHAMBLER, Shambler::new, 0.6f, 1.95f, 1.74f, false,
			() -> WaveMob.attributes(C.shambler));
	public static final EntityType<Crawler> CRAWLER = mob(CommonIds.CRAWLER, Crawler::new, 0.9f, 0.8f, 0.55f, false,
			() -> WaveMob.attributes(C.crawler));
	public static final EntityType<Screamer> SCREAMER = mob(CommonIds.SCREAMER, Screamer::new, 0.5f, 2.0f, 1.8f, false,
			() -> WaveMob.attributes(C.screamer));
	public static final EntityType<Bloater> BLOATER = mob(CommonIds.BLOATER, Bloater::new, 1.2f, 1.7f, 1.35f, false,
			() -> WaveMob.attributes(C.bloater));
	public static final EntityType<Brute> BRUTE = mob(CommonIds.BRUTE, Brute::new, 1.3f, 2.6f, 2.3f, false,
			() -> WaveMob.attributes(C.brute).add(net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE, 0.6));
	public static final EntityType<MutantBrute> MUTANT_BRUTE = mob(CommonIds.MUTANT_BRUTE, MutantBrute::new, 1.5f, 3.0f, 2.7f, false,
			() -> WaveMob.attributes(C.mutantBrute).add(net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE, 0.8));
	public static final EntityType<InfectedPiglin> INFECTED_PIGLIN = mob(CommonIds.INFECTED_PIGLIN, InfectedPiglin::new, 0.6f, 1.95f, 1.79f, true,
			() -> HostileMob.attributes(C.infectedPiglin, 32));
	public static final EntityType<AshHound> ASH_HOUND = mob(CommonIds.ASH_HOUND, AshHound::new, 0.7f, 0.75f, 0.6f, true,
			() -> HostileMob.attributes(C.ashHound, 64));

	public static final EntityType<Spitter> SPITTER = mob(CommonIds.SPITTER, Spitter::new, 0.6f, 1.9f, 1.65f, false,
			() -> WaveMob.attributes(C.spitter));
	public static final EntityType<Bulwark> BULWARK = mob(CommonIds.BULWARK, Bulwark::new, 1.1f, 2.3f, 2.0f, false,
			() -> WaveMob.attributes(C.bulwark).add(net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE, 0.7));

	public static final EntityType<Debris> DEBRIS = register(CommonIds.id(CommonIds.MOBS, "debris"),
			EntityType.Builder.<Debris>of(Debris::new, MobCategory.MISC).noLootTable().sized(0.5f, 0.5f).clientTrackingRange(4).updateInterval(10));
	public static final EntityType<SpitGlob> SPIT_GLOB = register(CommonIds.id(CommonIds.MOBS, "spit_glob"),
			EntityType.Builder.<SpitGlob>of(SpitGlob::new, MobCategory.MISC).noLootTable().sized(0.4f, 0.4f).clientTrackingRange(4).updateInterval(10));

	/** Spawn eggs by entity type, in roster order. */
	public static final Map<EntityType<?>, Item> SPAWN_EGGS = new LinkedHashMap<>();

	private MobEntities() {
	}

	private static <T extends Monster> EntityType<T> mob(Identifier id, EntityType.EntityFactory<T> factory, float width, float height, float eye,
			boolean fireImmune, Supplier<AttributeSupplier.Builder> attributes) {
		EntityType.Builder<T> builder = FabricEntityType.Builder.createMob(factory, MobCategory.MONSTER, mob -> mob
				.spawnPlacement(SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Monster::checkAnyLightMonsterSpawnRules)
				.defaultAttributes(attributes))
				.sized(width, height)
				.eyeHeight(eye)
				.clientTrackingRange(10);
		if (fireImmune) {
			builder = builder.fireImmune();
		}
		return register(id, builder);
	}

	private static <T extends net.minecraft.world.entity.Entity> EntityType<T> register(Identifier id, EntityType.Builder<T> builder) {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, id);
		return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, builder.build(key));
	}

	private static void spawnEgg(EntityType<?> type) {
		Identifier typeId = BuiltInRegistries.ENTITY_TYPE.getKey(type);
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, CommonIds.id(CommonIds.MOBS, typeId.getPath() + "_spawn_egg"));
		Item egg = Registry.register(BuiltInRegistries.ITEM, key, new SpawnEggItem(new Item.Properties().spawnEgg(type).setId(key)));
		SPAWN_EGGS.put(type, egg);
	}

	public static void init() {
		for (EntityType<?> type : new EntityType<?>[] {SHAMBLER, CRAWLER, SCREAMER, BLOATER, BRUTE, MUTANT_BRUTE, SPITTER, BULWARK, INFECTED_PIGLIN,
				ASH_HOUND}) {
			spawnEgg(type);
		}
		ResourceKey<CreativeModeTab> spawnEggs = ResourceKey.create(Registries.CREATIVE_MODE_TAB, Identifier.withDefaultNamespace("spawn_eggs"));
		CreativeModeTabEvents.modifyOutputEvent(spawnEggs).register(output ->
				output.insertAfter(Items.ZOMBIFIED_PIGLIN_SPAWN_EGG, SPAWN_EGGS.values().toArray(Item[]::new)));
	}
}
