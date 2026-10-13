package io.github.tmldude.blocksouls.common.event;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;

import io.github.tmldude.blocksouls.common.economy.CampObject;

/** Economy objects being used. Fired by the world module (blocks, the shopkeeper entity) on the server thread. */
public final class EconomyEvents {
	/**
	 * A player used an economy object at {@code pos} ({@code sneaking}: the alternate use, e.g. the overclock
	 * horn or the soul wager). Returns true once a listener handled it; the world shows a "nothing happens"
	 * hint when none did (e.g. the game module is absent).
	 */
	public static final Event<CampObjectUsed> CAMP_OBJECT_USED = EventFactory.createArrayBacked(CampObjectUsed.class, callbacks -> (player, object, pos, sneaking) -> {
		for (CampObjectUsed callback : callbacks) {
			if (callback.onUsed(player, object, pos, sneaking)) {
				return true;
			}
		}
		return false;
	});

	private EconomyEvents() {
	}

	@FunctionalInterface
	public interface CampObjectUsed {
		boolean onUsed(ServerPlayer player, CampObject object, BlockPos pos, boolean sneaking);
	}
}
