package io.github.tmldude.blocksouls.guns.loot;

import java.util.Map;
import java.util.Optional;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.functions.SetItemCountFunction;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;
import net.minecraft.world.level.storage.loot.providers.number.ints.ContextIntProviders;

import net.fabricmc.fabric.api.loot.v3.LootTableEvents;

import io.github.tmldude.blocksouls.common.CommonIds;
import io.github.tmldude.blocksouls.guns.BlockSoulsGuns;
import io.github.tmldude.blocksouls.guns.GunsConfig;
import io.github.tmldude.blocksouls.guns.item.ExoticGunItem;
import io.github.tmldude.blocksouls.guns.item.GunItem;
import io.github.tmldude.blocksouls.guns.item.GunsItems;

/** Registers {@code roll_perks} and adds gun and ammo pools to vanilla chests and the game's wave tables. */
public final class GunLoot {
	private GunLoot() {
	}

	public static void register() {
		Registry.register(BuiltInRegistries.LOOT_FUNCTION_TYPE, CommonIds.ROLL_PERKS, RollPerksFunction.MAP_CODEC);

		LootTableEvents.MODIFY.register((key, table, source, registries) -> {
			if (source.isBuiltin()) {
				modify(key, table);
			}
		});
	}

	private static void modify(ResourceKey<LootTable> key, LootTable.Builder table) {
		GunsConfig.Loot config = BlockSoulsGuns.CONFIG.loot;

		if (key.equals(CommonIds.WAVE_DROPS_COMMON)) {
			waveDrops(table, "common", config.waveCommonPrimaryChance, config.waveCommonSpecialChance, config.waveCommonGunChance);
		} else if (key.equals(CommonIds.WAVE_DROPS_RARE)) {
			waveDrops(table, "rare", config.waveRarePrimaryChance, config.waveRareSpecialChance, config.waveRareGunChance);
		} else if (key.equals(CommonIds.WAVE_DROPS_LEGENDARY)) {
			waveDrops(table, "legendary", config.waveLegendaryPrimaryChance, config.waveLegendarySpecialChance, config.waveLegendaryGunChance);
		} else if (key.equals(CommonIds.WAVE_CLEAR_REWARD)) {
			table.withPool(gunPool("rare", config.waveClearGunChance));
			table.withPool(ammoPool(GunsItems.PRIMARY_AMMO, 1.0, 16, 32));
			table.withPool(ammoPool(GunsItems.SPECIAL_AMMO, 1.0, 4, 10));
		}
		Double exoticChance = config.exoticTables.get(key.identifier().toString());
		if (exoticChance != null && exoticChance > 0) {
			table.withPool(LootPool.lootPool().when(LootItemRandomChanceCondition.randomChance(exoticChance.floatValue()))
					.add(LootItem.lootTableItem(GunsItems.STRAY_VERDICT).apply(RollPerksFunction.roll("exotic"))));
		}
		if (!key.equals(CommonIds.WAVE_DROPS_COMMON) && !key.equals(CommonIds.WAVE_DROPS_RARE) && !key.equals(CommonIds.WAVE_DROPS_LEGENDARY)
				&& !key.equals(CommonIds.WAVE_CLEAR_REWARD)) {
			GunsConfig.ChestLoot chest = config.chests.get(key.identifier().toString());
			if (chest != null) {
				table.withPool(gunPool(chest.tier(), chest.gunChance()));
				LootPool.Builder ammo = LootPool.lootPool()
						.add(LootItem.lootTableItem(GunsItems.PRIMARY_AMMO).setWeight(3).apply(SetItemCountFunction.setCount(ContextIntProviders.between(8, 24))))
						.add(LootItem.lootTableItem(GunsItems.SPECIAL_AMMO).setWeight(1).apply(SetItemCountFunction.setCount(ContextIntProviders.between(2, 8))))
						.when(LootItemRandomChanceCondition.randomChance((float) chest.ammoChance()));
				table.withPool(ammo);
			}
		}
	}

	private static void waveDrops(LootTable.Builder table, String tier, double primary, double special, double gun) {
		table.withPool(ammoPool(GunsItems.PRIMARY_AMMO, primary, 4, 12));
		table.withPool(ammoPool(GunsItems.SPECIAL_AMMO, special, 2, 4));
		table.withPool(gunPool(tier, gun));
	}

	private static LootPool.Builder ammoPool(Item ammo, double chance, int min, int max) {
		return LootPool.lootPool()
				.add(LootItem.lootTableItem(ammo).apply(SetItemCountFunction.setCount(ContextIntProviders.between(min, max))))
				.when(LootItemRandomChanceCondition.randomChance((float) chance));
	}

	/** One random droppable gun (weights from config), rolled at {@code tier}. */
	public static LootPool.Builder gunPool(String tier, double chance) {
		LootPool.Builder pool = LootPool.lootPool().when(LootItemRandomChanceCondition.randomChance((float) chance));
		for (Map.Entry<String, Integer> entry : BlockSoulsGuns.CONFIG.loot.gunWeights.entrySet()) {
			Item item = gunItem(entry.getKey());
			if (item != null && entry.getValue() > 0) {
				pool.add(LootItem.lootTableItem(item).setWeight(entry.getValue()).apply(RollPerksFunction.roll(tier)));
			}
		}
		return pool;
	}

	/** A random exotic item by the config weights; empty when every weight is 0. */
	public static Optional<Item> randomExotic(RandomSource random) {
		int total = 0;
		for (ExoticGunItem exotic : GunsItems.exotics()) {
			total += Math.max(0, BlockSoulsGuns.CONFIG.loot.exoticWeights.getOrDefault(exotic.weaponId(), 0));
		}
		if (total <= 0) {
			return Optional.empty();
		}
		int pick = random.nextInt(total);
		for (ExoticGunItem exotic : GunsItems.exotics()) {
			pick -= Math.max(0, BlockSoulsGuns.CONFIG.loot.exoticWeights.getOrDefault(exotic.weaponId(), 0));
			if (pick < 0) {
				return Optional.of(exotic);
			}
		}
		return Optional.empty();
	}

	private static Item gunItem(String weaponId) {
		for (GunItem gun : GunsItems.guns()) {
			if (gun.weaponId().equals(weaponId) && gun.stats().perks()) {
				return gun;
			}
		}
		return null;
	}
}
