package io.github.tmldude.blocksouls.common.act;

import java.util.List;
import java.util.Objects;

import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;

/**
 * Where any module reads the run's act structure. The game module installs the real provider; without it
 * every server reports a single always-open overworld act ({@link #CLASSIC}), so modules keep working
 * standalone and in the classic run shape.
 */
public final class Acts {
	/** The one act every run has when no act machine is installed: the overworld, always open. */
	public static final Act CLASSIC = new Act(1, Identifier.fromNamespaceAndPath("blocksouls", "overworld"),
			Identifier.fromNamespaceAndPath("minecraft", "overworld"), "Overworld");

	private static Provider provider = new Provider() {
		@Override
		public Act current(MinecraftServer server) {
			return CLASSIC;
		}

		@Override
		public List<Act> all(MinecraftServer server) {
			return List.of(CLASSIC);
		}

		@Override
		public int highestOpen(MinecraftServer server) {
			return CLASSIC.ordinal();
		}
	};

	private Acts() {
	}

	/** The act the run is currently in (the party's act). */
	public static Act current(MinecraftServer server) {
		return provider.current(server);
	}

	/** Every act of the run, in order (ordinal 1 first). */
	public static List<Act> all(MinecraftServer server) {
		return provider.all(server);
	}

	/** The highest act ordinal whose entry portal is lit. Acts up to here can be traveled to (portals are two-way). */
	public static int highestOpen(MinecraftServer server) {
		return provider.highestOpen(server);
	}

	/** Whether the act with this ordinal is open (entry portal lit, or it's where the run started). */
	public static boolean isOpen(MinecraftServer server, int ordinal) {
		return ordinal >= 1 && ordinal <= highestOpen(server);
	}

	/** Called once by the game module. */
	public static void setProvider(Provider actsProvider) {
		provider = Objects.requireNonNull(actsProvider);
	}

	public interface Provider {
		Act current(MinecraftServer server);

		List<Act> all(MinecraftServer server);

		int highestOpen(MinecraftServer server);
	}
}
