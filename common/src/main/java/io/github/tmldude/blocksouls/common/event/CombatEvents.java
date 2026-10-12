package io.github.tmldude.blocksouls.common.event;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;

/**
 * Player combat events other modules can't get from vanilla, fired on the server thread. Kills themselves are
 * Fabric's {@code ServerEntityCombatEvents.AFTER_KILLED_OTHER_ENTITY}; this adds what only the shooting code knows.
 */
public final class CombatEvents {
	/** A player's gun landed a precision hit (fired by guns, after damage). {@code killed}: the hit was lethal. */
	public static final Event<PrecisionHit> PRECISION_HIT = EventFactory.createArrayBacked(PrecisionHit.class,
			callbacks -> (player, target, killed) -> {
				for (PrecisionHit callback : callbacks) {
					callback.onPrecisionHit(player, target, killed);
				}
			});

	private CombatEvents() {
	}

	@FunctionalInterface
	public interface PrecisionHit {
		void onPrecisionHit(ServerPlayer player, LivingEntity target, boolean killed);
	}
}
