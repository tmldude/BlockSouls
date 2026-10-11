package io.github.tmldude.blocksouls.bosses.raid;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;

import io.github.tmldude.blocksouls.bosses.api.Bosses;
import io.github.tmldude.blocksouls.common.CommonIds;
import io.github.tmldude.blocksouls.common.Config;

/** Registers the final raid: config, sounds, the dragon and its crystals, the trophy, the End hooks, debug commands. */
public final class FinalRaids {
	public static FinalRaidConfig CONFIG = new FinalRaidConfig();
	public static EntityType<FinalDragon> TYPE;
	public static EntityType<RaidCrystal> CRYSTAL;
	/** The unique drop: a crown of horns from the final dragon. Purely a trophy. */
	public static Item TROPHY;

	private FinalRaids() {
	}

	public static void register() {
		CONFIG = Config.load("boss-final_dragon", FinalRaidConfig.class, FinalRaidConfig::new);
		FinalRaidSounds.init();
		FinalDragon.createDefinition();
		TYPE = Bosses.register(CommonIds.FINAL_BOSS, FinalDragon::new, new Bosses.Size(5.0f, 3.0f, 2.5f), true,
				() -> Bosses.attributes(CONFIG.health, CONFIG.armor, 0.3, CONFIG.dive.damage));

		ResourceKey<EntityType<?>> crystalKey = ResourceKey.create(Registries.ENTITY_TYPE, CommonIds.id(CommonIds.BOSSES, "raid_crystal"));
		CRYSTAL = Registry.register(BuiltInRegistries.ENTITY_TYPE, crystalKey, EntityType.Builder.<RaidCrystal>of(RaidCrystal::new, MobCategory.MISC)
				.noLootTable()
				.fireImmune()
				.sized(2.0f, 2.0f)
				.clientTrackingRange(16)
				.updateInterval(20)
				.build(crystalKey));

		ResourceKey<Item> trophyKey = ResourceKey.create(Registries.ITEM, CommonIds.id(CommonIds.BOSSES, "final_dragon_trophy"));
		TROPHY = Registry.register(BuiltInRegistries.ITEM, trophyKey,
				new Item(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant().setId(trophyKey)));

		FinalRaidEnd.init();
		RaidCommands.init();
	}
}
