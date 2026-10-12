package io.github.tmldude.blocksouls.common.ability;

import java.util.Set;

import net.minecraft.resources.Identifier;

/**
 * What the contract knows about an ability: which slot it fills, how it recharges and which synergy tags it carries.
 * The behavior itself lives in the abilities module; everything else (HUD, charge, boon weighting) only needs this.
 *
 * @param id            {@code blocksouls_abilities:<name>} (any namespace is allowed)
 * @param slot          the slot it can be equipped in
 * @param cooldownTicks ticks until ready again after a cast ({@link ChargeModel#COOLDOWN}); ignored for kill charge
 * @param charge        how it recharges
 * @param tags          {@code SynergyTags} ids this ability counts as (fire, explosion, on_kill, ...)
 */
public record AbilityDefinition(Identifier id, AbilitySlot slot, int cooldownTicks, ChargeModel charge, Set<Identifier> tags) {
	public AbilityDefinition {
		tags = Set.copyOf(tags);
		cooldownTicks = Math.max(0, cooldownTicks);
	}

	/** A cooldown ability. */
	public static AbilityDefinition cooldown(Identifier id, AbilitySlot slot, int cooldownTicks, Identifier... tags) {
		return new AbilityDefinition(id, slot, cooldownTicks, ChargeModel.COOLDOWN, Set.of(tags));
	}

	/** An ultimate: charged by kills, no cooldown. */
	public static AbilityDefinition ultimate(Identifier id, Identifier... tags) {
		return new AbilityDefinition(id, AbilitySlot.ULTIMATE, 0, ChargeModel.KILL_CHARGE, Set.of(tags));
	}

	/** The same ability with another cooldown (config overrides). */
	public AbilityDefinition withCooldown(int ticks) {
		return new AbilityDefinition(id, slot, ticks, charge, tags);
	}
}
