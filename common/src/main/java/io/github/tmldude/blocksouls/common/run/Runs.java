package io.github.tmldude.blocksouls.common.run;

import java.util.Objects;
import java.util.function.Function;

import net.minecraft.server.MinecraftServer;

/**
 * Where any module reads the run state. The game module installs the real provider; without it every
 * server reports {@link RunState#NOT_STARTED}, so modules keep working standalone.
 */
public final class Runs {
	private static Function<MinecraftServer, RunState> provider = server -> RunState.NOT_STARTED;

	private Runs() {
	}

	public static RunState get(MinecraftServer server) {
		return provider.apply(server);
	}

	/** Called once by the game module. */
	public static void setProvider(Function<MinecraftServer, RunState> runStateProvider) {
		provider = Objects.requireNonNull(runStateProvider);
	}
}
