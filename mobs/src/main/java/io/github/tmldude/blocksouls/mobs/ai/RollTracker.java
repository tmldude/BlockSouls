package io.github.tmldude.blocksouls.mobs.ai;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.world.entity.LivingEntity;

import io.github.tmldude.blocksouls.common.event.PlayerMoveEvents;

/** When each player last dodge-rolled (server game time), for attacks that punish a roll's recovery. */
public final class RollTracker {
	private static final Map<UUID, Long> LAST_ROLL = new ConcurrentHashMap<>();

	private RollTracker() {
	}

	public static void init() {
		PlayerMoveEvents.ROLLED.register(player -> {
			if (!player.level().isClientSide()) {
				LAST_ROLL.put(player.getUUID(), player.level().getGameTime());
			}
		});
	}

	/** Whether {@code entity} rolled within the last {@code ticks} ticks. */
	public static boolean rolledWithin(LivingEntity entity, int ticks) {
		Long at = LAST_ROLL.get(entity.getUUID());
		return at != null && entity.level().getGameTime() - at <= ticks;
	}

	/** Test hook: records a roll now. */
	public static void markRolled(LivingEntity entity) {
		LAST_ROLL.put(entity.getUUID(), entity.level().getGameTime());
	}
}
