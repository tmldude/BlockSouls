package io.github.tmldude.blocksouls.common.economy;

import net.minecraft.resources.Identifier;

import io.github.tmldude.blocksouls.common.CommonIds;

/**
 * The run's economy objects (PLAN2 §3c/§3d): world objects, no menus. The world module owns the block or
 * entity (model, particles, placement); the game owns what using one does, through
 * {@code EconomyEvents.CAMP_OBJECT_USED}.
 */
public enum CampObject {
	/** Pay max hearts or a random boon for a legendary roll, or take a curse instead of the hearts. Single use. */
	BLOOD_ALTAR(CommonIds.BLOOD_ALTAR),
	/** Great loot, the next siege's modifier goes up a tier, and a curse. Single use. */
	CURSED_CHEST(CommonIds.CURSED_CHEST),
	/** Survive in a shrinking circle for a legendary or trinket offer. Loud: it calls the horde. */
	CHALLENGE_PYLON(CommonIds.CHALLENGE_PYLON),
	/** The camp horn: ends the day early (sneak-use: the overclock horn). */
	CAMP_HORN(CommonIds.CAMP_HORN),
	/** The camp shopkeeper (an entity): the trade board; sneak-use at siege start is the soul wager. */
	SHOPKEEPER(CommonIds.SHOPKEEPER);

	private final Identifier id;

	CampObject(Identifier id) {
		this.id = id;
	}

	/** The block id, or the entity type id for {@link #SHOPKEEPER}. */
	public Identifier id() {
		return id;
	}
}
