package io.github.tmldude.blocksouls.guns;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import io.github.tmldude.blocksouls.guns.weapon.GunStats;
import io.github.tmldude.blocksouls.guns.weapon.Weapons;

class WeaponsTest {
	@AfterEach
	void reset() {
		Weapons.apply(Map.of());
	}

	@Test
	void defaultsAreValidAndMatchTheBrief() {
		for (GunStats stats : Weapons.defaults().values()) {
			assertTrue(stats.isValid(), stats.toString());
		}
		GunStats pistol = Weapons.get(Weapons.STARTER_PISTOL);
		assertTrue(pistol.infiniteAmmo());
		assertFalse(pistol.perks());
		assertEquals(8, pistol.magSize());
		assertTrue(Weapons.get(Weapons.AUTO_RIFLE).automatic());
		assertEquals(8, Weapons.get(Weapons.SHOTGUN).pellets());
		assertTrue(Weapons.get(Weapons.SNIPER_RIFLE).scope());
	}

	@Test
	void overridesReplaceValidEntriesOnly() {
		GunStats base = Weapons.defaults().get(Weapons.HAND_CANNON);
		GunStats buffed = new GunStats(base.ammo(), base.mode(), 12, 1, base.rpm(), base.magSize(), base.reloadSeconds(),
				base.precisionMult(), base.range(), base.hipSpread(), base.adsSpread(), base.adsZoom(), base.recoil(),
				base.falloffStart(), base.falloffMinMult(), base.knockback(), base.scope(), base.perks());
		GunStats broken = new GunStats(null, "semi", 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, false, false);

		List<String> rejected = Weapons.apply(Map.of(Weapons.HAND_CANNON, buffed, Weapons.SNIPER_RIFLE, broken, "unknown_gun", buffed));

		assertEquals(12, Weapons.get(Weapons.HAND_CANNON).damage());
		assertEquals(Weapons.defaults().get(Weapons.SNIPER_RIFLE), Weapons.get(Weapons.SNIPER_RIFLE));
		assertEquals(2, rejected.size());
	}
}
