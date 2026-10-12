package io.github.tmldude.blocksouls.common.player;

import org.jspecify.annotations.Nullable;

import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;

/**
 * Which origin (v2 run-start class) a player picked, as an id like {@code blocksouls_game:drifter}. The game module
 * installs the provider once origins exist; until then every player has none and modules fall back to their config
 * (the abilities module picks its signature ability that way). Read on whichever side the provider supports.
 */
public final class Origins {
	private static Provider provider = player -> null;

	private Origins() {
	}

	@FunctionalInterface
	public interface Provider {
		@Nullable Identifier originOf(Player player);
	}

	/** Installed by the module that owns origins (one provider; the last call wins). */
	public static void setProvider(Provider newProvider) {
		provider = newProvider;
	}

	/** The player's origin id, or {@code null} when none is set (classic run shape, or no origins module). */
	public static @Nullable Identifier of(Player player) {
		return provider.originOf(player);
	}
}
