package io.github.tmldude.blocksouls.world.loot;

import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.entries.NestedLootTable;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceWithEnchantedBonusCondition;
import net.minecraft.world.level.storage.loot.providers.number.ints.ContextIntProviders;

import net.fabricmc.fabric.api.loot.v3.FabricLootTableBuilder;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;

import io.github.tmldude.blocksouls.common.CommonIds;
import io.github.tmldude.blocksouls.world.BlockSoulsWorld;
import io.github.tmldude.blocksouls.world.WorldConfig;
import io.github.tmldude.blocksouls.world.gen.StructurePlacer;

/**
 * Adds our materials and books to other loot tables. The contents live in our own {@code inject/*} tables (data);
 * this only decides where they're rolled and how often (config).
 */
public final class WorldLoot {
	public static final ResourceKey<LootTable> INJECT_MATERIALS = table("inject/materials");
	public static final ResourceKey<LootTable> INJECT_BOOKS = table("inject/wacky_books");
	public static final ResourceKey<LootTable> INJECT_WAVE_COMMON = table("inject/wave_common");
	public static final ResourceKey<LootTable> INJECT_WAVE_RARE = table("inject/wave_rare");
	public static final ResourceKey<LootTable> INJECT_WAVE_LEGENDARY = table("inject/wave_legendary");
	public static final ResourceKey<LootTable> INJECT_WAVE_CLEAR = table("inject/wave_clear");
	private static final String ENDERMAN = "minecraft:entities/enderman";

	private WorldLoot() {
	}

	public static void init() {
		LootTableEvents.MODIFY.register((key, table, source, registries) -> {
			if (!source.isBuiltin()) {
				return; // a datapack replaced this table; leave its author in charge
			}
			WorldConfig.Loot cfg = BlockSoulsWorld.CONFIG.loot;
			String id = key.identifier().toString();

			if (cfg.materialChests.contains(id)) {
				for (int i = 0; i < cfg.materialRolls; i++) {
					nested(table, registries, INJECT_MATERIALS, (float) cfg.materialChance);
				}
			}
			if (cfg.bookChests.contains(id)) {
				nested(table, registries, INJECT_BOOKS, (float) cfg.bookChance);
			}

			Map<ResourceKey<LootTable>, ResourceKey<LootTable>> wave = Map.of(
					CommonIds.WAVE_DROPS_COMMON, INJECT_WAVE_COMMON,
					CommonIds.WAVE_DROPS_RARE, INJECT_WAVE_RARE,
					CommonIds.WAVE_DROPS_LEGENDARY, INJECT_WAVE_LEGENDARY,
					CommonIds.WAVE_CLEAR_REWARD, INJECT_WAVE_CLEAR);
			if (wave.containsKey(key)) {
				nested(table, registries, wave.get(key), waveChance(cfg, key));
			}

			if (key.equals(StructurePlacer.WRECK_LOOT)) {
				addGuns(table, registries, "common", (float) cfg.wreckGunChance);
			} else if (key.equals(StructurePlacer.CHECKPOINT_LOOT)) {
				addGuns(table, registries, "rare", (float) cfg.checkpointGunChance);
			}

			if (id.equals(ENDERMAN)) {
				if (cfg.endermanExtraPearls > 0) {
					table.withPool(LootPool.lootPool()
							.setRolls(ContextIntProviders.exactly(cfg.endermanExtraPearls))
							.add(LootItem.lootTableItem(Items.ENDER_PEARL)));
				}
				table.withPool(LootPool.lootPool()
						.add(LootItem.lootTableItem(Items.ENDER_EYE))
						.when(LootItemRandomChanceWithEnchantedBonusCondition.randomChanceAndLootingBoost(
								registries.lookupOrThrow(Registries.ENCHANTMENT),
								(float) cfg.endermanEyeChance, (float) cfg.endermanEyeLootingBonus)));
			}
		});
	}

	/**
	 * A gun from {@code #blocksouls_world:loot/guns}, with guns' {@code roll_perks} at {@code tier} when guns is
	 * installed. Built from JSON at load time because the function type only exists alongside guns.
	 */
	private static void addGuns(LootTable.Builder table, HolderLookup.Provider registries, String tier, float chance) {
		JsonObject entry = new JsonObject();
		entry.addProperty("type", "minecraft:tag");
		entry.addProperty("items", "#" + BlockSoulsWorld.MOD_ID + ":loot/guns");
		entry.addProperty("expand", true);
		if (BuiltInRegistries.LOOT_FUNCTION_TYPE.containsKey(CommonIds.ROLL_PERKS)) {
			JsonObject rollPerks = new JsonObject();
			rollPerks.addProperty("type", CommonIds.ROLL_PERKS.toString());
			rollPerks.addProperty("tier", tier);
			entry.add("modifier", rollPerks);
		}
		JsonArray entries = new JsonArray();
		entries.add(entry);
		JsonObject condition = new JsonObject();
		condition.addProperty("type", "minecraft:random_chance");
		condition.addProperty("chance", chance);
		JsonObject json = new JsonObject();
		json.addProperty("rolls", 1);
		json.add("entries", entries);
		json.add("condition", condition);
		LootPool.CODEC.parse(registries.createSerializationContext(JsonOps.INSTANCE), json)
				.resultOrPartial(error -> BlockSoulsWorld.LOGGER.error("Bad gun loot pool: {}", error))
				.ifPresent(pool -> ((FabricLootTableBuilder) table).pool(pool));
	}

	private static float waveChance(WorldConfig.Loot cfg, ResourceKey<LootTable> key) {
		if (key.equals(CommonIds.WAVE_DROPS_COMMON)) {
			return (float) cfg.waveCommonChance;
		} else if (key.equals(CommonIds.WAVE_DROPS_RARE)) {
			return (float) cfg.waveRareChance;
		} else if (key.equals(CommonIds.WAVE_DROPS_LEGENDARY)) {
			return (float) cfg.waveLegendaryChance;
		}
		return 1.0F;
	}

	private static void nested(LootTable.Builder table, HolderLookup.Provider registries, ResourceKey<LootTable> inject, float chance) {
		try {
			LootPool.Builder pool = LootPool.lootPool().add(NestedLootTable.lootTableReference(registries.getOrThrow(inject)));
			if (chance < 1.0F) {
				pool.when(LootItemRandomChanceCondition.randomChance(chance));
			}
			table.withPool(pool);
		} catch (IllegalStateException e) {
			BlockSoulsWorld.LOGGER.error("Missing loot table {}", inject.identifier(), e);
		}
	}

	private static ResourceKey<LootTable> table(String path) {
		return CommonIds.lootTable(BlockSoulsWorld.MOD_ID, path);
	}
}
