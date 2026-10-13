package io.github.tmldude.blocksouls.mobs.registry;

import java.util.Set;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;

import io.github.tmldude.blocksouls.common.mob.Affixes;
import io.github.tmldude.blocksouls.common.mob.WaveContext;
import io.github.tmldude.blocksouls.common.mob.WaveMobSpec;
import io.github.tmldude.blocksouls.common.mob.WaveMobs;
import io.github.tmldude.blocksouls.mobs.BlockSoulsMobs;
import io.github.tmldude.blocksouls.mobs.MobsConfig;
import io.github.tmldude.blocksouls.mobs.affix.MobFloors;
import io.github.tmldude.blocksouls.mobs.entity.WaveMob;

/** Puts the eight wave mobs into the shared {@link WaveMobs} roster. */
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
		register(MobEntities.SPITTER, c.spitter, false);
		register(MobEntities.BULWARK, c.bulwark, false);
	}

	private static void register(EntityType<? extends WaveMob> type, MobsConfig.Stats stats, boolean elite) {
		Identifier id = EntityType.getKey(type);
		MobsConfig.RosterEntry roster = BlockSoulsMobs.CONFIG.roster.get(id.toString());
		int minFloor = roster == null ? 0 : roster.minFloor;
		Set<String> tags = roster == null || roster.tags == null ? Set.of() : Set.copyOf(roster.tags);
		WaveMobs.register(new WaveMobSpec(id, stats.minWave, stats.weight, elite, factory(type, elite), minFloor, tags));
	}

	/**
	 * Creates the mob at {@code pos} (not added to the level) with the wave's number and breaker flag, finalized
	 * with an event spawn reason. Night modifiers are applied by the game.
	 */
	public static WaveMobSpec.Factory factory(EntityType<? extends WaveMob> type) {
		return factory(type, false);
	}

	/** {@link #factory(EntityType)}; an elite may also roll an affix for the floor it spawns on. */
	public static WaveMobSpec.Factory factory(EntityType<? extends WaveMob> type, boolean elite) {
		return (level, pos, context) -> {
			WaveMob mob = create(type, level, pos, context);
			if (mob != null && elite) {
				MobFloors.Here here = MobFloors.at(level, pos);
				Affixes.roll(level.getRandom(), here.floor(), here.tags()).ifPresent(affix -> Affixes.apply(mob, affix));
			}
			return mob;
		};
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
