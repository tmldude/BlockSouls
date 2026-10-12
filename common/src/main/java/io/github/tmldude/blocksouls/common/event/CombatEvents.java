package io.github.tmldude.blocksouls.common.event;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;

/**
 * Gun combat events, fired by the guns module on the server thread. The game module's build rules (keystones such
 * as "reloading releases a nova", "headshots refund stamina", cross-player "precision kills reload an ally") listen
 * here, so guns never import game and game never imports guns.
 */
public final class CombatEvents {
	/** One of a player's shots hit a living target. Fired once per hit, after damage was dealt. */
	public static final Event<Hit> HIT = EventFactory.createArrayBacked(Hit.class,
			callbacks -> (shooter, target, damage, precision, killed) -> {
				for (Hit callback : callbacks) {
					callback.onHit(shooter, target, damage, precision, killed);
				}
			});

	/** A player's gun finished reloading. */
	public static final Event<Reloaded> RELOADED = EventFactory.createArrayBacked(Reloaded.class,
			callbacks -> (player, roundsLoaded) -> {
				for (Reloaded callback : callbacks) {
					callback.onReloaded(player, roundsLoaded);
				}
			});

	private CombatEvents() {
	}

	@FunctionalInterface
	public interface Hit {
		/**
		 * @param damage    damage dealt by this hit, after multipliers
		 * @param precision the hit landed in the target's precision zone (a headshot)
		 * @param killed    the hit killed the target
		 */
		void onHit(ServerPlayer shooter, LivingEntity target, float damage, boolean precision, boolean killed);
	}

	@FunctionalInterface
	public interface Reloaded {
		/** @param roundsLoaded rounds put into the magazine by this reload */
		void onReloaded(ServerPlayer player, int roundsLoaded);
	}
}
