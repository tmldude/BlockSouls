package io.github.tmldude.blocksouls.common.floor;

/**
 * What a placement slot on a floor is for (PLAN2 §1). The world module fills the slot's terrain and marks
 * its position; the logic that lives in it belongs to whoever owns that feature: the camp objects
 * (shopkeeper, horn, altar, forge) and the event / mini-boss logic to the Director session, the wreck's
 * repair to vehicles, the siege rings to the wave spawner.
 */
public enum FloorSlotKind {
	/** The floor's centre: the camp platform the siege converges on. Exactly one per floor. */
	CAMP,
	/** The exit portal frame at the camp (its charge is the spine's portal charge). Exactly one per floor. */
	EXIT_PORTAL,
	/** A resource node (scrap, gunmetal, the floor's rare). {@code tag} is the node block id. */
	NODE,
	/** The floor's structure (ruin / wreck yard / outpost) and its chest. {@code tag} is the structure kind. */
	STRUCTURE,
	/** A chest the floor placed (inside the structure, the cave, the secret). {@code tag} is the loot table. */
	CRATE,
	/** A flattened arena for the rotating mini-boss. Logic: Director. */
	MINI_BOSS,
	/** Where the floor's secret is. {@code tag} is the secret kind (cracked wall, hidden drop). */
	SECRET,
	/** The random-event spot (the StS "?"). Logic: Director. */
	EVENT,
	/** The cave dungeon entrance. */
	CAVE,
	/** The wreck with scrap scattered around it. Repair: vehicles. */
	WRECK,
	/** One point of the siege spawn ring around the camp. {@code tag} is the ring index. */
	SIEGE_RING
}
