package io.github.tmldude.blocksouls.bosses.field;

import java.util.Optional;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;

import io.github.tmldude.blocksouls.bosses.api.BossEntity;

/**
 * Where and when one field boss appears. Register with {@link FieldBosses#register}. The spawner handles the
 * rest: run phase, the per-day roll, cooldowns, "one roaming boss at a time", the spawn-distance rule, killed-this-run
 * tracking, the arrival cue and the leave timeout.
 */
public interface FieldBossRule {
	enum Schedule {
		/** Rolled once per in-game day ({@link FieldBossSettings#chancePerDay}); on a hit, the spawner keeps looking for a spot that day. */
		DAILY,
		/** Checked on every scan with no roll (e.g. "a player came near a bastion"). */
		WATCH
	}

	/** Where the boss appears. {@code arenaRadius} 0 keeps the boss's default. */
	record Spot(ServerLevel level, BlockPos pos, float yaw, int arenaRadius) {
		public Spot(ServerLevel level, BlockPos pos, float yaw) {
			this(level, pos, yaw, 0);
		}
	}

	Schedule schedule();

	FieldBossSettings settings();

	/**
	 * Called on the server thread for each candidate player (in random order) when the boss may appear. Return a spot
	 * near them if the conditions hold (dimension, biome, time of day, structure...), else empty. Keep it cheap.
	 */
	Optional<Spot> find(ServerLevel level, ServerPlayer player, RandomSource random);

	/** A new, not yet added boss; the spawner positions it, marks it as a field boss and adds it. */
	BossEntity create(ServerLevel level);

	/**
	 * Roaming bosses count toward "at most one roaming field boss at a time". Guards tied to a place (the Furnace Knight
	 * at a bastion) return false.
	 */
	default boolean roaming() {
		return true;
	}
}
