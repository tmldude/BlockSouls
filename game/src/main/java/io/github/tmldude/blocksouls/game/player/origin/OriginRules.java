package io.github.tmldude.blocksouls.game.player.origin;

/**
 * An origin's rule tweaks that other systems read ({@code "rules"} in the JSON). Every field has the neutral
 * default, so an origin only lists what it changes. Fields another module reads (relic shape, ammo drops, altar
 * cost) are data here and inert until that module consumes them through {@link PlayerOrigins#rules}.
 *
 * @param reviveSpeed          multiplies how fast this player revives others (2 = twice as fast)
 * @param healMultiplier       multiplies every heal on this player (0.5 = healing items half as good)
 * @param heartsPerBossKill    max health gained per boss kill, in hearts
 * @param rollMist             the dodge roll leaves a blinding blood mist
 * @param relicCards           cards per relic, 0 = the normal count
 * @param relicAlwaysWildcard  one card of every relic is a Wildcard
 * @param relicHidden          cards are hidden until taken
 * @param ammoDropMultiplier   multiplies ammo drops for this player
 * @param altarCostMultiplier  multiplies blood altar costs for this player
 */
public record OriginRules(double reviveSpeed, double healMultiplier, int heartsPerBossKill, boolean rollMist, int relicCards,
		boolean relicAlwaysWildcard, boolean relicHidden, double ammoDropMultiplier, double altarCostMultiplier) {
	public static final OriginRules NEUTRAL = new OriginRules(1.0, 1.0, 0, false, 0, false, false, 1.0, 1.0);
}
