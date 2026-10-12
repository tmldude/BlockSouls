package io.github.tmldude.blocksouls.guns.exotic;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

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
	/** Tether Lance tethers in flight. */
	private static final List<Tether> TETHERS = new ArrayList<>();

	private record Tether(LivingEntity a, LivingEntity b, long until) {
	}

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

	// ---- Tether Lance tethers ---------------------------------------------------------------------------------

	public static void tether(LivingEntity a, LivingEntity b, long until) {
		TETHERS.add(new Tether(a, b, until));
	}

	public static int tethers() {
		return TETHERS.size();
	}

	/** Pulls each tethered pair together and draws the web between them. */
	private static void tickTethers(ServerLevel level, long now) {
		double pull = BlockSoulsGuns.CONFIG.exotics.tetherPull;
		TETHERS.removeIf(tether -> {
			LivingEntity a = tether.a();
			LivingEntity b = tether.b();
			if (now >= tether.until() || !a.isAlive() || !b.isAlive() || a.level() != level || b.level() != level) {
				return true;
			}
			Vec3 mid = a.position().add(b.position()).scale(0.5);
			for (LivingEntity entity : List.of(a, b)) {
				Vec3 to = mid.subtract(entity.position());
				if (to.lengthSqr() > 1.0) {
					Vec3 velocity = entity.getDeltaMovement().scale(0.6).add(to.normalize().scale(pull));
					entity.setDeltaMovement(velocity.x, Math.min(velocity.y, 0.1), velocity.z);
					entity.syncVelocity = true;
				}
			}
			if (now % 4 == 0) {
				Vec3 from = a.getBoundingBox().getCenter();
				Vec3 step = b.getBoundingBox().getCenter().subtract(from);
				int points = Math.max(1, (int) (step.length() / 0.6));
				for (int i = 0; i <= points; i++) {
					Vec3 p = from.add(step.scale(i / (double) points));
					level.sendParticles(ParticleTypes.ITEM_COBWEB, p.x, p.y, p.z, 1, 0, 0, 0, 0);
				}
			}
			return false;
		});
	}

	/** Clears expired marks (and their glow) and runs the tethers. Called every server tick per level. */
	public static void tick(ServerLevel level) {
		long now = level.getGameTime();
		if (!TETHERS.isEmpty()) {
			tickTethers(level, now);
		}
		if (MARKS.isEmpty()) {
			return;
		}
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
		TETHERS.clear();
	}
}
