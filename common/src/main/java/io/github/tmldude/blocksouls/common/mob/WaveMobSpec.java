package io.github.tmldude.blocksouls.common.mob;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;

/**
 * A mob the game can put into waves (registered by mobs).
 *
 * @param minWave first wave it can appear in
 * @param weight  relative pick weight among eligible mobs
 * @param elite   elites are rarer, survive dawn and count as mini-bosses
 * @param factory creates the mob (not yet added to the level); the game adds it, applies difficulty
 *                multipliers and tracks it
 */
public record WaveMobSpec(Identifier id, int minWave, int weight, boolean elite, Factory factory) {
	@FunctionalInterface
	public interface Factory {
		@Nullable Mob create(ServerLevel level, BlockPos pos, WaveContext context);
	}
}
