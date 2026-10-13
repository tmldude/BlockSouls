package io.github.tmldude.blocksouls.game.run;

import java.util.List;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import io.github.tmldude.blocksouls.common.act.Acts;
import io.github.tmldude.blocksouls.common.floor.Floors;
import io.github.tmldude.blocksouls.common.mob.WaveMobSpec;
import io.github.tmldude.blocksouls.common.mob.WaveMobs;

/**
 * Which roster a spawn draws from: the Expedition floor under the spawn position (its number and tags), or the
 * classic roster. Resolved once per spawn so the shuffle, the Director's preference and the fallback all agree.
 *
 * @param floor 1-based floor number; 0 = classic (every spec; the floor fields are ignored)
 * @param tags  the floor's tags, escalation tags included; empty in classic
 */
record FloorRoster(int floor, Set<String> tags) {
	static final FloorRoster CLASSIC = new FloorRoster(0, Set.of());

	/**
	 * The roster at {@code pos}: the placed floor there; else, in a multi-act run, the current act's number with no
	 * tags (same fallback as the Director's affix roll); else classic.
	 */
	static FloorRoster at(MinecraftServer server, ServerLevel level, BlockPos pos) {
		return Floors.at(server, level.dimension().identifier(), pos)
				.map(floor -> new FloorRoster(floor.floorNumber(), floor.tags()))
				.orElseGet(() -> Acts.all(server).size() <= 1
						? CLASSIC
						: new FloorRoster(Math.max(1, Acts.current(server).ordinal()), Set.of()));
	}

	/** Specs that can appear in {@code wave} on this roster. */
	List<WaveMobSpec> eligible(int wave) {
		return floor <= 0 ? WaveMobs.eligible(wave) : WaveMobs.eligible(wave, floor, tags);
	}

	/** Whether the spec may skip its {@code minWave} here: elites under the more-elites escalation. */
	boolean skipsMinWave(WaveMobSpec spec) {
		return floor > 0 && spec.elite() && tags.contains(WaveMobs.MORE_ELITES);
	}
}
