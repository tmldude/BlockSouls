package io.github.tmldude.blocksouls.common.synergy;

import java.util.List;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

/**
 * Where other modules reach a player's build: curses (negative boons that greed objects price in) and trinkets
 * (the 3-slot collection that survives going down). The game module installs the provider; without it curses can't
 * be applied, nothing is owned and offers do nothing, so the economy objects keep working standalone.
 *
 * <p>Trinket offers are items: {@link #offerTrinkets} hands the player a Trinket Case
 * ({@code CommonIds.TRINKET_CASE}); using it shows a pick-1-of-3. A loot table can drop the case directly.
 */
public final class BuildHooks {
	private static final Provider NONE = new Provider() {
		@Override
		public @Nullable Identifier applyCurse(ServerPlayer player, @Nullable Identifier curse) {
			return null;
		}

		@Override
		public boolean purgeCurse(ServerPlayer player, @Nullable Identifier curse) {
			return false;
		}

		@Override
		public List<Identifier> curses(ServerPlayer player) {
			return List.of();
		}

		@Override
		public boolean offerTrinkets(ServerPlayer player) {
			return false;
		}

		@Override
		public List<Identifier> trinkets(ServerPlayer player) {
			return List.of();
		}
	};

	private static Provider provider = NONE;

	private BuildHooks() {
	}

	public interface Provider {
		/**
		 * Puts a curse on the player: {@code curse} by id, or a random one the player doesn't have yet when null.
		 * Returns the curse applied, or null if none could be (unknown id, already owned, no curses left).
		 */
		@Nullable Identifier applyCurse(ServerPlayer player, @Nullable Identifier curse);

		/** Removes {@code curse}, or the most recent curse when null. Returns false if there was nothing to remove. */
		boolean purgeCurse(ServerPlayer player, @Nullable Identifier curse);

		/** The player's curses, oldest first. */
		List<Identifier> curses(ServerPlayer player);

		/** Offers the player a pick-1-of-3 trinket (a Trinket Case item). Returns false if nothing was offered. */
		boolean offerTrinkets(ServerPlayer player);

		/** The player's trinkets, in slot order. */
		List<Identifier> trinkets(ServerPlayer player);
	}

	/** Called once by the game module. */
	public static void setProvider(Provider buildProvider) {
		provider = Objects.requireNonNull(buildProvider);
	}

	public static @Nullable Identifier applyCurse(ServerPlayer player, @Nullable Identifier curse) {
		return provider.applyCurse(player, curse);
	}

	/** A random curse the player doesn't have yet; null if none could be applied. */
	public static @Nullable Identifier applyRandomCurse(ServerPlayer player) {
		return provider.applyCurse(player, null);
	}

	public static boolean purgeCurse(ServerPlayer player, @Nullable Identifier curse) {
		return provider.purgeCurse(player, curse);
	}

	/** Removes the player's most recent curse; false if there was none. */
	public static boolean purgeCurse(ServerPlayer player) {
		return provider.purgeCurse(player, null);
	}

	public static List<Identifier> curses(ServerPlayer player) {
		return provider.curses(player);
	}

	public static boolean offerTrinkets(ServerPlayer player) {
		return provider.offerTrinkets(player);
	}

	public static List<Identifier> trinkets(ServerPlayer player) {
		return provider.trinkets(player);
	}
}
