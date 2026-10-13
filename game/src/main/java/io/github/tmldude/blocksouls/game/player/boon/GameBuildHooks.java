package io.github.tmldude.blocksouls.game.player.boon;

import java.util.ArrayList;
import java.util.List;
import java.util.random.RandomGenerator;

import org.jspecify.annotations.Nullable;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

import io.github.tmldude.blocksouls.common.synergy.BuildHooks;
import io.github.tmldude.blocksouls.game.player.trinket.TrinketOffers;

/** The game's {@link BuildHooks.Provider}: curses from the catalog, trinket offers as cases. */
public final class GameBuildHooks implements BuildHooks.Provider {
	private static final RandomGenerator RANDOM = new java.util.Random();

	private GameBuildHooks() {
	}

	public static void install() {
		BuildHooks.setProvider(new GameBuildHooks());
	}

	@Override
	public @Nullable Identifier applyCurse(ServerPlayer player, @Nullable Identifier curse) {
		Boon pick;
		if (curse != null) {
			pick = BoonCatalog.get(curse);
		} else {
			List<Boon> options = new ArrayList<>();
			for (Boon candidate : BoonCatalog.curses()) {
				if (!PlayerBoons.hasCurse(player, Identifier.parse(candidate.id()))) {
					options.add(candidate);
				}
			}
			pick = options.isEmpty() ? null : options.get(RANDOM.nextInt(options.size()));
		}
		if (pick == null || !PlayerBoons.applyCurse(player, pick)) {
			return null;
		}
		return Identifier.parse(pick.id());
	}

	@Override
	public boolean purgeCurse(ServerPlayer player, @Nullable Identifier curse) {
		return PlayerBoons.purgeCurse(player, curse);
	}

	@Override
	public List<Identifier> curses(ServerPlayer player) {
		return PlayerBoons.curses(player);
	}

	@Override
	public boolean offerTrinkets(ServerPlayer player) {
		return TrinketOffers.giveCase(player);
	}

	@Override
	public List<Identifier> trinkets(ServerPlayer player) {
		return PlayerBoons.trinkets(player);
	}
}
