package io.github.tmldude.blocksouls.common.player;

import java.util.Objects;

import net.minecraft.server.level.ServerPlayer;

/**
 * Where any module acts on a player's held gun. The guns module installs the provider; without it every call is a
 * no-op that reports {@code false}, so modules keep working standalone.
 */
public final class WeaponHooks {
	private static Provider provider = player -> false;

	private WeaponHooks() {
	}

	@FunctionalInterface
	public interface Provider {
		/** Instantly refills the magazine of the gun in the player's main hand. Returns false if there was none to fill. */
		boolean reload(ServerPlayer player);
	}

	/** Called once by the guns module. */
	public static void setProvider(Provider weaponProvider) {
		provider = Objects.requireNonNull(weaponProvider);
	}

	public static boolean reload(ServerPlayer player) {
		return provider.reload(player);
	}
}
