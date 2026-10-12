package io.github.tmldude.blocksouls.guns.item;

import java.util.List;
import java.util.Map;

import io.github.tmldude.blocksouls.guns.weapon.Weapons;

/** Which guns each gun part is crafted into (the part tooltips list them). Keep in step with the recipes. */
public final class PartUses {
	public static final Map<String, List<String>> USES = Map.of(
			"gun_barrel", List.of(Weapons.HAND_CANNON, Weapons.AUTO_RIFLE, Weapons.SHOTGUN, Weapons.SNIPER_RIFLE),
			"gun_receiver", List.of(Weapons.HAND_CANNON, Weapons.AUTO_RIFLE, Weapons.SHOTGUN, Weapons.SNIPER_RIFLE),
			"gun_stock", List.of(Weapons.AUTO_RIFLE, Weapons.SHOTGUN, Weapons.SNIPER_RIFLE));

	private PartUses() {
	}

	/** Weapon ids that use this part (by item path), empty for anything else. */
	public static List<String> of(String partPath) {
		return USES.getOrDefault(partPath, List.of());
	}
}
