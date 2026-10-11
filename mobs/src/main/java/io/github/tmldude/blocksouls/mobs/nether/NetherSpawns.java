package io.github.tmldude.blocksouls.mobs.nether;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.util.random.Weighted;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.biome.MobSpawnSettings;

import net.fabricmc.fabric.api.biome.v1.BiomeModificationContext;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.biome.v1.ModificationPhase;

import io.github.tmldude.blocksouls.common.CommonIds;
import io.github.tmldude.blocksouls.mobs.BlockSoulsMobs;
import io.github.tmldude.blocksouls.mobs.MobsConfig;
import io.github.tmldude.blocksouls.mobs.registry.MobEntities;

/**
 * Infected piglins in every Nether biome. Where piglins or zombified piglins spawn, they take
 * {@code replaceShare} of those entries' weight (the originals keep the rest), so they replace a share of them;
 * elsewhere they get {@code baseWeight}.
 */
public final class NetherSpawns {
	private NetherSpawns() {
	}

	public static void init() {
		BiomeModifications.create(CommonIds.id(CommonIds.MOBS, "infected_piglins"))
				.add(ModificationPhase.REPLACEMENTS, BiomeSelectors.foundInTheNether(), context -> modify(context.getMobSpawnSettings()));
	}

	private static boolean isPiglin(EntityType<?> type) {
		return type == EntityTypes.PIGLIN || type == EntityTypes.ZOMBIFIED_PIGLIN;
	}

	private static void modify(BiomeModificationContext.MobSpawnSettingsContext spawns) {
		MobsConfig.InfectedPiglin config = BlockSoulsMobs.CONFIG.infectedPiglin;
		List<Weighted<MobSpawnSettings.SpawnerData>> piglins = new ArrayList<>();
		for (Weighted<MobSpawnSettings.SpawnerData> entry : spawns.getMobs(MobCategory.MONSTER)) {
			if (isPiglin(entry.value().type())) {
				piglins.add(entry);
			}
		}

		if (piglins.isEmpty()) {
			if (config.baseWeight > 0) {
				spawns.addSpawn(MobEntities.INFECTED_PIGLIN, config.baseWeight, 1, 3);
			}
			return;
		}

		double share = Math.clamp(config.replaceShare, 0.0, 1.0);
		spawns.removeSpawns((category, data) -> category == MobCategory.MONSTER && isPiglin(data.type()));
		for (Weighted<MobSpawnSettings.SpawnerData> entry : piglins) {
			int infected = (int) Math.round(entry.weight() * share);
			int kept = entry.weight() - infected;
			if (kept > 0) {
				spawns.addSpawn(MobCategory.MONSTER, entry.value(), kept);
			}
			if (infected > 0) {
				spawns.addSpawn(MobCategory.MONSTER, new MobSpawnSettings.SpawnerData(MobEntities.INFECTED_PIGLIN, entry.value().count()), infected);
			}
		}
	}
}
