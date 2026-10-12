package io.github.tmldude.blocksouls.common.player;

import java.util.Objects;

import net.minecraft.server.level.ServerPlayer;

/**
 * Where any module changes a player's stamina. The movement module owns stamina and installs the provider; without
 * it every call is a no-op that reports {@code false}, so modules keep working standalone.
 */
public final class Stamina {
	private static Provider provider = (player, amount) -> false;

	private Stamina() {
	}

	@FunctionalInterface
	public interface Provider {
		/** Adds {@code amount} stamina (negative drains), clamped to the player's range. Returns false if nothing changed. */
		boolean add(ServerPlayer player, double amount);
	}

	/** Called once by the movement module. */
	public static void setProvider(Provider staminaProvider) {
		provider = Objects.requireNonNull(staminaProvider);
	}

	public static boolean add(ServerPlayer player, double amount) {
		return provider.add(player, amount);
	}
}
