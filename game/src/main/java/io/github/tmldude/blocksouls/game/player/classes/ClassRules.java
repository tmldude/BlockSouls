package io.github.tmldude.blocksouls.game.player.classes;

/**
 * A class's rule tweaks that other systems read ({@code "rules"} in the JSON). Every field has the neutral
 * default, so a class only lists what it changes. Fields another module reads (relic shape, ammo drops, altar
 * cost) are data here and inert until that module consumes them through {@link PlayerClasses#rules}.
 *
 * <p>Signature movements that live game-side: {@code slamMinFall} > 0 turns landings from at least that many
 * blocks into a slam (knockback and damage in {@code slamRadius}, the fall damage forgiven); {@code hoverTicks} > 0
 * lets the player hover that long per airborne stint by holding jump while falling.
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
 * @param slamMinFall          blocks of fall that make a landing a slam (0 = no slam)
 * @param slamRadius           slam radius (blocks)
 * @param slamKnockback        slam knockback strength
 * @param slamDamage           slam damage to mobs in radius
 * @param hoverTicks           hover budget per airborne stint (0 = no hover)
 */
public record ClassRules(double reviveSpeed, double healMultiplier, int heartsPerBossKill, boolean rollMist, int relicCards,
		boolean relicAlwaysWildcard, boolean relicHidden, double ammoDropMultiplier, double altarCostMultiplier,
		double slamMinFall, double slamRadius, double slamKnockback, double slamDamage, int hoverTicks) {
	public static final ClassRules NEUTRAL = new ClassRules(1.0, 1.0, 0, false, 0, false, false, 1.0, 1.0, 0, 0, 0, 0, 0);

	public boolean slams() {
		return slamMinFall > 0;
	}

	public boolean hovers() {
		return hoverTicks > 0;
	}
}
