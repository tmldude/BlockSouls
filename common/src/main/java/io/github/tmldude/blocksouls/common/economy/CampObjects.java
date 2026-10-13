package io.github.tmldude.blocksouls.common.economy;

import java.util.Objects;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * Where the game asks for an economy object to be put in the world (camp at run start, a floor's event
 * slot). The world module installs the real placer; without it nothing is placed and {@link #place}
 * returns false, which is what a server without the world module sees.
 *
 * <p>Threading: server thread.
 */
public final class CampObjects {
	private static Placer placer = (level, pos, object) -> false;

	private CampObjects() {
	}

	/** Places {@code object} standing on {@code pos} (the block or entity at {@code pos}). True if placed. */
	public static boolean place(ServerLevel level, BlockPos pos, CampObject object) {
		return placer.place(level, pos, object);
	}

	/** Called once by the world module. */
	public static void setPlacer(Placer newPlacer) {
		placer = Objects.requireNonNull(newPlacer);
	}

	@FunctionalInterface
	public interface Placer {
		boolean place(ServerLevel level, BlockPos pos, CampObject object);
	}
}
