package io.github.tmldude.blocksouls.vehicles;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;

import io.github.tmldude.blocksouls.common.CommonIds;
import io.github.tmldude.blocksouls.vehicles.kart.KartEntity;
import io.github.tmldude.blocksouls.vehicles.kart.KartWreckEntity;

public final class VehicleRegistry {
	public static final EntityType<KartEntity> KART = entity("kart",
			EntityType.Builder.of(KartEntity::new, MobCategory.MISC).noLootTable().sized(1.8f, 0.9f).eyeHeight(0.6f)
					.fireImmune().clientTrackingRange(10));
	public static final EntityType<KartWreckEntity> KART_WRECK = entity(CommonIds.KART_WRECK.getPath(),
			EntityType.Builder.of(KartWreckEntity::new, MobCategory.MISC).noLootTable().sized(1.8f, 0.8f).eyeHeight(0.5f)
					.fireImmune().clientTrackingRange(10));

	/** Repair parts; world's loot tables reference these ids. */
	public static final Item WHEEL = item("wheel", 16);
	public static final Item ENGINE = item("engine", 4);
	/** Refuels a kart (amount in config {@code fuel.items}). */
	public static final Item FUEL_CAN = item("fuel_can", 16);

	/** Items that count as scrap for repairs (world's scrap; test mods may add more). */
	public static final TagKey<Item> REPAIR_SCRAP = TagKey.create(Registries.ITEM, id("repair_scrap"));

	public static final SoundEvent KART_ENGINE = sound("kart.engine");
	public static final SoundEvent KART_HOP = sound("kart.hop");
	public static final SoundEvent KART_BOOST = sound("kart.boost");
	public static final SoundEvent KART_RAM = sound("kart.ram");
	public static final SoundEvent KART_BREAK = sound("kart.break");
	public static final SoundEvent KART_REPAIR = sound("kart.repair");
	public static final SoundEvent KART_REFUEL = sound("kart.refuel");
	public static final SoundEvent KART_SPUTTER = sound("kart.sputter");
	public static final SoundEvent KART_HORN = sound("kart.horn");
	public static final SoundEvent WRECK_FIT_PART = sound("wreck.fit_part");
	public static final SoundEvent WRECK_REPAIRED = sound("wreck.repaired");

	private VehicleRegistry() {
	}

	public static Identifier id(String path) {
		return CommonIds.id(CommonIds.VEHICLES, path);
	}

	/** Forces class loading so everything registers during init. */
	public static void init() {
		CreativeModeTabEvents.modifyOutputEvent(ResourceKey.create(Registries.CREATIVE_MODE_TAB, Identifier.withDefaultNamespace("ingredients")))
				.register(output -> output.insertAfter(Items.IRON_INGOT, WHEEL, ENGINE, FUEL_CAN));
	}

	private static <T extends Entity> EntityType<T> entity(String path, EntityType.Builder<T> builder) {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, id(path));
		return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, builder.build(key));
	}

	private static Item item(String path, int stackSize) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, id(path));
		return Registry.register(BuiltInRegistries.ITEM, key, new Item(new Item.Properties().setId(key).stacksTo(stackSize)));
	}

	private static SoundEvent sound(String path) {
		Identifier id = id(path);
		return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
	}
}
