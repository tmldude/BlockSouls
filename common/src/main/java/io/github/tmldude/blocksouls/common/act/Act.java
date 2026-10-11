package io.github.tmldude.blocksouls.common.act;

import net.minecraft.resources.Identifier;

/**
 * One act of a v2 Expedition run: a whole open dimension the party plays in until it charges the portal
 * to the next one. Acts are data-driven (the game module loads the list from config); {@code dimension}
 * may name a dimension that isn't registered yet — the act is still legal, travel to it just isn't
 * possible until the dimension exists.
 *
 * @param ordinal     1-based position in the run (act 1 is where the run starts)
 * @param id          the act's own id (e.g. {@code blocksouls_game:overworld})
 * @param dimension   the dimension the act plays in (e.g. {@code minecraft:the_nether})
 * @param displayName shown to players (HUD, announcements)
 */
public record Act(int ordinal, Identifier id, Identifier dimension, String displayName) {
}
