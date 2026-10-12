package io.github.tmldude.blocksouls.common.event;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;

import io.github.tmldude.blocksouls.common.floor.PlacedFloor;

/** Floor lifecycle and loot-cadence events, fired by the world module on the server thread (v2 floors run shape). */
public final class FloorEvents {
	/**
	 * A floor was placed and generated for an act ({@code Floors.open}). Fires before the party is sent
	 * there, so listeners can fill slots (events, mini-boss, camp objects) from {@code floor.slots()}.
	 */
	public static final Event<FloorEvent> FLOOR_OPENED = EventFactory.createArrayBacked(FloorEvent.class, callbacks -> (server, floor) -> {
		for (FloorEvent callback : callbacks) {
			callback.onFloor(server, floor);
		}
	});

	/** A player popped a resource node: its burst was dropped at {@code pos}. {@code node} is the node block id. */
	public static final Event<NodePopped> NODE_POPPED = EventFactory.createArrayBacked(NodePopped.class, callbacks -> (player, floor, pos, node) -> {
		for (NodePopped callback : callbacks) {
			callback.onNodePopped(player, floor, pos, node);
		}
	});

	/** A player opened a floor crate (a chest the floor placed) for the first time. {@code loot} is its loot table. */
	public static final Event<CrateOpened> CRATE_OPENED = EventFactory.createArrayBacked(CrateOpened.class, callbacks -> (player, floor, pos, loot) -> {
		for (CrateOpened callback : callbacks) {
			callback.onCrateOpened(player, floor, pos, loot);
		}
	});

	/**
	 * A player used the camp's exit portal frame while it was unlit: the game starts the act's portal
	 * charge (the siege). The world fires it; the game listens.
	 */
	public static final Event<PortalUsed> EXIT_PORTAL_USED = EventFactory.createArrayBacked(PortalUsed.class, callbacks -> (player, floor) -> {
		for (PortalUsed callback : callbacks) {
			callback.onPortalUsed(player, floor);
		}
	});

	/** The floor's exit portal lit (its charge completed): stepping through leads to the next floor. */
	public static final Event<FloorEvent> EXIT_PORTAL_LIT = EventFactory.createArrayBacked(FloorEvent.class, callbacks -> (server, floor) -> {
		for (FloorEvent callback : callbacks) {
			callback.onFloor(server, floor);
		}
	});

	private FloorEvents() {
	}

	@FunctionalInterface
	public interface FloorEvent {
		void onFloor(MinecraftServer server, PlacedFloor floor);
	}

	@FunctionalInterface
	public interface NodePopped {
		/** {@code floor} is null when the node was outside any placed floor (a debug-placed node). */
		void onNodePopped(ServerPlayer player, PlacedFloor floor, BlockPos pos, Identifier node);
	}

	@FunctionalInterface
	public interface CrateOpened {
		void onCrateOpened(ServerPlayer player, PlacedFloor floor, BlockPos pos, Identifier loot);
	}

	@FunctionalInterface
	public interface PortalUsed {
		void onPortalUsed(ServerPlayer player, PlacedFloor floor);
	}
}
