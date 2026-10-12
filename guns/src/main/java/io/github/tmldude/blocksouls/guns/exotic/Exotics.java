package io.github.tmldude.blocksouls.guns.exotic;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import io.github.tmldude.blocksouls.guns.BlockSoulsGuns;
import io.github.tmldude.blocksouls.guns.item.GunItem;
import io.github.tmldude.blocksouls.guns.weapon.Weapons;

/** Weapon id → exotic behavior, plus the Lantern Eye's marks (a server-wide table any gun reads). */
public final class Exotics {
	private static final Map<String, ExoticBehavior> BEHAVIORS = Map.of(
			Weapons.STRAY_VERDICT, new StrayVerdict(),
			Weapons.LANTERN_EYE, new LanternEye(),
			Weapons.BACKDRAFT, new Backdraft(),
			Weapons.KINDLING_ENGINE, new KindlingEngine(),
			Weapons.TETHER_LANCE, new TetherLance());

	/** Marked entities → game time the mark ends. */
	private static final Map<UUID, Long> MARKS = new HashMap<>();

	private Exotics() {
	}

	public static @Nullable ExoticBehavior of(String weaponId) {
		return BEHAVIORS.get(weaponId);
	}

	public static @Nullable ExoticBehavior of(ItemStack stack) {
		GunItem gun = GunItem.of(stack);
		return gun == null ? null : of(gun.weaponId());
	}

	public static boolean isExotic(String weaponId) {
		return BEHAVIORS.containsKey(weaponId);
	}

	// ---- Lantern Eye marks --------------------------------------------------------------------------------------

	public static void mark(LivingEntity target, long until) {
		MARKS.merge(target.getUUID(), until, Math::max);
		target.setGlowingTag(true);
	}

	public static boolean isMarked(LivingEntity target, long now) {
		Long until = MARKS.get(target.getUUID());
		if (until == null) {
			return false;
		}
		if (now >= until || !target.isAlive()) {
			MARKS.remove(target.getUUID());
			target.setGlowingTag(false);
			return false;
		}
		return true;
	}

	/** Damage multiplier any gun gets against a marked target. */
	public static double markedMultiplier(LivingEntity target, long now) {
		return isMarked(target, now) ? BlockSoulsGuns.CONFIG.exotics.lanternEyeMarkedDamageMult : 1.0;
	}

	/** Clears expired marks (and their glow). Called every server tick. */
	public static void tick(ServerLevel level) {
		if (MARKS.isEmpty()) {
			return;
		}
		long now = level.getGameTime();
		MARKS.entrySet().removeIf(entry -> {
			if (now < entry.getValue()) {
				return false;
			}
			var entity = level.getEntity(entry.getKey());
			if (entity != null) {
				entity.setGlowingTag(false);
			}
			return true;
		});
	}

	public static void clear() {
		MARKS.clear();
	}
}
