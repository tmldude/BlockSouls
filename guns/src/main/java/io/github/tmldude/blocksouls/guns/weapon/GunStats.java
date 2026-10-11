package io.github.tmldude.blocksouls.guns.weapon;

/**
 * One weapon's numbers. Defaults are in {@link Weapons}; each can be overridden whole in
 * {@code config/blocksouls/guns.json} ({@code weapons.<id>}).
 *
 * @param ammo          {@code none} (infinite), {@code primary} or {@code special}
 * @param mode          {@code semi} (one shot per click) or {@code auto} (hold to fire)
 * @param damage        per bullet, or per pellet for {@code pellets > 1}
 * @param reloadSeconds full reload time before reload-speed bonuses
 * @param range         blocks
 * @param hipSpread     max deflection in degrees when firing from the hip
 * @param adsSpread     max deflection in degrees while aiming down sights
 * @param adsZoom       FOV divisor while aiming (1 = no zoom)
 * @param recoil        camera kick in degrees of pitch per shot
 * @param falloffStart  damage starts dropping past this distance, down to {@code falloffMinMult} at {@code range}
 * @param knockback     knockback strength per hit (bullets skip vanilla knockback)
 * @param scope         show a scope overlay while aiming
 * @param perks         whether the gun can roll rarity and perks (the Starter Pistol can't)
 */
public record GunStats(String ammo, String mode, double damage, int pellets, double rpm, int magSize, double reloadSeconds,
		double precisionMult, double range, double hipSpread, double adsSpread, double adsZoom, double recoil,
		double falloffStart, double falloffMinMult, double knockback, boolean scope, boolean perks) {

	public boolean automatic() {
		return "auto".equals(mode);
	}

	public boolean infiniteAmmo() {
		return "none".equals(ammo);
	}

	/** False for a hand-edited entry that is missing required numbers. */
	public boolean isValid() {
		return ammo != null && ("none".equals(ammo) || "primary".equals(ammo) || "special".equals(ammo))
				&& mode != null && ("semi".equals(mode) || "auto".equals(mode))
				&& damage > 0 && pellets > 0 && rpm > 0 && magSize > 0 && reloadSeconds > 0 && precisionMult > 0
				&& range > 0 && hipSpread >= 0 && adsSpread >= 0 && adsZoom >= 1 && falloffMinMult >= 0;
	}
}
