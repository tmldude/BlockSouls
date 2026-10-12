package io.github.tmldude.blocksouls.common.event;

import net.minecraft.server.MinecraftServer;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;

import io.github.tmldude.blocksouls.common.act.Act;

/** Act lifecycle events, fired by the game module's act machine on the server thread (v2 Expedition runs). */
public final class ActEvents {
	/**
	 * The act became open and is now the run's frontier act: fires with act 1 at run start, and with act
	 * {@code N + 1} the moment act {@code N}'s portal charge completes.
	 */
	public static final Event<ActEvent> ACT_STARTED = event();

	/** A player started charging {@code act}'s exit portal (the defended ~3-minute charge). */
	public static final Event<ActEvent> PORTAL_CHARGE_STARTED = event();

	/**
	 * {@code act}'s exit portal charge completed: the next act's entry portal is permanently lit.
	 * {@link #ACT_STARTED} fires with the next act right after.
	 */
	public static final Event<ActEvent> PORTAL_CHARGE_COMPLETED = event();

	/** The party (most non-downed players) arrived in {@code act}'s dimension for the first time this run. */
	public static final Event<ActEvent> ACT_ENTERED = event();

	private ActEvents() {
	}

	@FunctionalInterface
	public interface ActEvent {
		void onAct(MinecraftServer server, Act act);
	}

	private static Event<ActEvent> event() {
		return EventFactory.createArrayBacked(ActEvent.class, callbacks -> (server, act) -> {
			for (ActEvent callback : callbacks) {
				callback.onAct(server, act);
			}
		});
	}
}
