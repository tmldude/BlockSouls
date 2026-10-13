package io.github.tmldude.blocksouls.common.mob;

import java.util.Set;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;

/**
 * A mob the game can put into waves (registered by mobs).
 *
 * @param minWave  first wave it can appear in
 * @param weight   relative pick weight among eligible mobs
 * @param elite    elites are rarer, survive dawn and count as mini-bosses
 * @param factory  creates the mob (not yet added to the level); the game adds it, applies difficulty
 *                 multipliers and tracks it
 * @param minFloor first Expedition floor it can appear on (1-based; 0 or 1 = every floor). Ignored by
 *                 {@link WaveMobs#eligible(int)} (the classic roster)
 * @param tags     floor tags it needs: it is only eligible on a floor whose tags contain all of them (empty = any
 *                 floor). Lets per-floor rosters (blight, sky) slot in as data
 */
public record WaveMobSpec(Identifier id, int minWave, int weight, boolean elite, Factory factory, int minFloor, Set<String> tags) {
	public WaveMobSpec {
		tags = Set.copyOf(tags);
	}

	/** A spec eligible on every floor (the pre-v2 shape). */
	public WaveMobSpec(Identifier id, int minWave, int weight, boolean elite, Factory factory) {
		this(id, minWave, weight, elite, factory, 0, Set.of());
	}

	@FunctionalInterface
	public interface Factory {
		@Nullable Mob create(ServerLevel level, BlockPos pos, WaveContext context);
	}
}
