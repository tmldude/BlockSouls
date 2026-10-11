package io.github.tmldude.blocksouls.bosses.raid;

import java.util.UUID;

/** What the final raid needs from vanilla's {@code EnderDragonFight} (implemented by {@code EnderDragonFightMixin}). */
public interface FinalRaidFight {
	/** Points the fight at our dragon, so vanilla's "is the dragon alive" bookkeeping and its kill hook accept it. */
	void blocksouls$setDragonUuid(UUID uuid);

	/** Our dragon is alive and ticking: don't go looking for (or spawning) another one. */
	void blocksouls$dragonSeen();
}
