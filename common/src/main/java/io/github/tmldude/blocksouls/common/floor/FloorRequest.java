package io.github.tmldude.blocksouls.common.floor;

import net.minecraft.resources.Identifier;

/**
 * What the game asks the world for when an act opens in the floors run shape: a floor of this number from
 * this template pool, for this act. The world picks and varies a template and returns the
 * {@link PlacedFloor}.
 *
 * @param actOrdinal  the act the floor is for (1-based; one floor per act per run)
 * @param pool        the template pool to draw from (e.g. {@code blocksouls_world:surface})
 * @param floorNumber the floor's position in the run (1 = the first floor); drives escalation tags
 * @param seed        the run's seed (0 = pick one), so a run's floors reproduce
 * @param players     players in the party when the floor opens (size and node counts scale through {@code PartyScaling})
 */
public record FloorRequest(int actOrdinal, Identifier pool, int floorNumber, long seed, int players) {
}
