package io.github.tmldude.blocksouls.world.gen.layout;

/** What goes in one cell of a structure layout. The world-side placer maps each part to a block state. */
public enum Part {
	/** Vehicle shell. */
	HULL,
	/** Chassis, tyres, scorch marks. */
	CHASSIS,
	GRILLE,
	/** Smashed windscreen: a pane that survived. */
	GLASS,
	WALL,
	WALL_CRACKED,
	WALL_MOSSY,
	CONCRETE,
	SANDBAG,
	SANDBAG_MUD,
	TOWER_POST,
	TOWER_FLOOR,
	FENCE,
	/** Loot chest. */
	CHEST,
	/** Forces air (clears terrain or grass inside the structure). */
	AIR
}
