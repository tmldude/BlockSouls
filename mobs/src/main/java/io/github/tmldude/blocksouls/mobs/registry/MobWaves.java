package io.github.tmldude.blocksouls.mobs.registry;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;

import io.github.tmldude.blocksouls.common.mob.WaveContext;
import io.github.tmldude.blocksouls.common.mob.WaveMobSpec;
import io.github.tmldude.blocksouls.common.mob.WaveMobs;
import io.github.tmldude.blocksouls.mobs.BlockSoulsMobs;
import io.github.tmldude.blocksouls.mobs.MobsConfig;
import io.github.tmldude.blocksouls.mobs.entity.WaveMob;

/** Puts the six wave mobs into the shared {@link WaveMobs} roster. */
public final class MobWaves {
	private MobWaves() {
	}

	public static void init() {
		MobsConfig c = BlockSoulsMobs.CONFIG;
		register(MobEntities.SHAMBLER, c.shambler, false);
		register(MobEntities.CRAWLER, c.crawler, false);
		register(MobEntities.SCREAMER, c.screamer, false);
		register(MobEntities.BLOATER, c.bloater, false);
		register(MobEntities.BRUTE, c.brute, false);
		register(MobEntities.MUTANT_BRUTE, c.mutantBrute, true);
	}

	private static void register(EntityType<? extends WaveMob> type, MobsConfig.Stats stats, boolean elite) {
		WaveMobs.register(new WaveMobSpec(EntityType.getKey(type), stats.minWave, stats.weight, elite, factory(type)));
	}

	/**
	 * Creates the mob at {@code pos} (not added to the level) with the wave's number and breaker flag, finalized
	 * with an event spawn reason. Night modifiers are applied by the game.
	 */
	public static WaveMobSpec.Factory factory(EntityType<? extends WaveMob> type) {
		return (level, pos, context) -> create(type, level, pos, context);
	}

	public static <T extends WaveMob> @Nullable T create(EntityType<T> type, ServerLevel level, BlockPos pos, WaveContext context) {
		T mob = type.create(level, EntitySpawnReason.EVENT);
		if (mob == null) {
			return null;
		}
		mob.snapTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, level.getRandom().nextFloat() * 360f, 0f);
		mob.setYHeadRot(mob.getYRot());
		mob.applyWaveContext(context);
		mob.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), EntitySpawnReason.EVENT, null);
		return mob;
	}
}
