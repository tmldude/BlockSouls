package io.github.tmldude.blocksouls.game.player.downed;

import java.util.Collection;
import java.util.function.Predicate;

/** Run over = every player who counts (online, survival/adventure, any dimension) is down at the same time. */
public final class TeamWipe {
	private TeamWipe() {
	}

	/**
	 * @param players  the online players
	 * @param counts   whether a player counts toward a wipe (in the run, not creative/spectator)
	 * @param isDowned whether a player is down
	 * @return true if at least one player counts and all who count are down
	 */
	public static <P> boolean isWipe(Collection<P> players, Predicate<P> counts, Predicate<P> isDowned) {
		boolean anyone = false;
		for (P player : players) {
			if (!counts.test(player)) {
				continue;
			}
			if (!isDowned.test(player)) {
				return false;
			}
			anyone = true;
		}
		return anyone;
	}
}
