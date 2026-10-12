package io.github.tmldude.blocksouls.common.floor;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;

/**
 * Where any module reads the run's placed floors and where the game asks for a new one. The world module
 * installs the real provider; without it no floor exists and {@link #open} fails, which is what the classic
 * run shape and a server without the world module see.
 *
 * <p>Threading: everything here runs on the server thread.
 */
public final class Floors {
	private static Provider provider = new Provider() {
		@Override
		public Optional<PlacedFloor> open(MinecraftServer server, FloorRequest request) {
			return Optional.empty();
		}

		@Override
		public List<PlacedFloor> all(MinecraftServer server) {
			return List.of();
		}
	};

	private Floors() {
	}

	/**
	 * Places a floor for the request (generating it in the floor dimension) and returns it, or empty when
	 * no provider is installed or the pool is unknown. Asking twice for the same act returns the floor
	 * already placed for it in this run.
	 */
	public static Optional<PlacedFloor> open(MinecraftServer server, FloorRequest request) {
		return provider.open(server, request);
	}

	/** Every floor placed for the current run, act 1 first. */
	public static List<PlacedFloor> all(MinecraftServer server) {
		return provider.all(server);
	}

	/** The floor placed for this act in the current run, if any. */
	public static Optional<PlacedFloor> forAct(MinecraftServer server, int actOrdinal) {
		for (PlacedFloor floor : all(server)) {
			if (floor.actOrdinal() == actOrdinal) {
				return Optional.of(floor);
			}
		}
		return Optional.empty();
	}

	/** The floor whose island {@code pos} in {@code dimension} is over, if any. */
	public static Optional<PlacedFloor> at(MinecraftServer server, Identifier dimension, BlockPos pos) {
		for (PlacedFloor floor : all(server)) {
			if (floor.dimension().equals(dimension) && floor.contains(pos)) {
				return Optional.of(floor);
			}
		}
		return Optional.empty();
	}

	/** Called once by the world module. */
	public static void setProvider(Provider floorsProvider) {
		provider = Objects.requireNonNull(floorsProvider);
	}

	public interface Provider {
		Optional<PlacedFloor> open(MinecraftServer server, FloorRequest request);

		List<PlacedFloor> all(MinecraftServer server);
	}
}
