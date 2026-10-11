package io.github.tmldude.blocksouls.guns.client;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import io.github.tmldude.blocksouls.guns.net.ShotFxPayload;
import io.github.tmldude.blocksouls.guns.weapon.GunStats;

/** Muzzle flashes and tracers, drawn with particles. */
public final class GunFx {
	private static final DustParticleOptions TRACER = new DustParticleOptions(0xFFE9A8, 0.45F);
	private static final double TRACER_STEP = 0.8;
	/** Tracers start this far along the path so they don't fill the shooter's own view. */
	private static final double TRACER_SKIP = 1.5;

	private GunFx() {
	}

	/** Where the barrel ends: in front of the eye, a little right and down of center (right hand). */
	public static Vec3 muzzle(Player player) {
		Vec3 look = player.getLookAngle();
		Vec3 right = look.cross(new Vec3(0, 1, 0)).normalize();
		if (right.lengthSqr() < 1e-6) {
			right = new Vec3(1, 0, 0);
		}
		return player.getEyePosition().add(look.scale(0.9)).add(right.scale(0.28)).add(0, -0.2, 0);
	}

	public static void muzzleFlash(Player player, GunStats stats) {
		ClientLevel level = Minecraft.getInstance().level;
		if (level == null) {
			return;
		}
		Vec3 at = muzzle(player);
		Vec3 look = player.getLookAngle();
		int flames = stats.pellets() > 1 ? 4 : 2;
		for (int i = 0; i < flames; i++) {
			level.addParticle(ParticleTypes.SMALL_FLAME, at.x, at.y, at.z, look.x * 0.05, look.y * 0.05, look.z * 0.05);
		}
		level.addParticle(ParticleTypes.SMOKE, at.x, at.y, at.z, look.x * 0.02, look.y * 0.02 + 0.01, look.z * 0.02);
	}

	public static void onShot(ShotFxPayload payload) {
		Minecraft minecraft = Minecraft.getInstance();
		ClientLevel level = minecraft.level;
		if (level == null) {
			return;
		}
		Entity shooter = level.getEntity(payload.shooterId());
		boolean self = minecraft.player != null && shooter == minecraft.player;
		if (shooter instanceof Player player && !self) {
			// The local player already drew their own flash when they pulled the trigger.
			Vec3 at = muzzle(player);
			level.addParticle(ParticleTypes.SMALL_FLAME, at.x, at.y, at.z, 0, 0, 0);
			level.addParticle(ParticleTypes.SMOKE, at.x, at.y, at.z, 0, 0.01, 0);
		}

		for (List<Vec3> path : payload.tracers()) {
			double skip = self ? TRACER_SKIP : 0.3;
			for (int i = 0; i + 1 < path.size(); i++) {
				Vec3 from = path.get(i);
				Vec3 to = path.get(i + 1);
				double length = from.distanceTo(to);
				Vec3 step = to.subtract(from).normalize();
				for (double d = skip; d < length; d += TRACER_STEP) {
					Vec3 p = from.add(step.scale(d));
					level.addParticle(TRACER, true, false, p.x, p.y, p.z, 0, 0, 0);
				}
				skip = 0;
			}
			Vec3 end = path.getLast();
			level.addParticle(ParticleTypes.SMOKE, end.x, end.y, end.z, 0, 0.01, 0);
		}
	}
}
