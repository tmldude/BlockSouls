package io.github.tmldude.blocksouls.guns.client;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import io.github.tmldude.blocksouls.guns.net.ShotFxPayload;
import io.github.tmldude.blocksouls.guns.weapon.GunStats;

/** Muzzle flashes and tracers, drawn with particles. */
public final class GunFx {
	private static final DustParticleOptions TRACER = new DustParticleOptions(0xFFE9A8, 0.45F);
	private static final DustParticleOptions FLASH = new DustParticleOptions(0xFFF2B0, 1.6F);
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
		// Feel pass: a bright core, a cone of sparks and a smoke puff; heavier guns throw more of each.
		int flames = stats.pellets() > 1 ? 5 : stats.recoil() >= 5 ? 4 : 2;
		for (int i = 0; i < flames; i++) {
			level.addParticle(ParticleTypes.SMALL_FLAME, at.x, at.y, at.z, look.x * 0.06, look.y * 0.06, look.z * 0.06);
		}
		level.addParticle(FLASH, true, false, at.x, at.y, at.z, 0, 0, 0);
		int sparks = stats.pellets() > 1 ? 6 : 3;
		for (int i = 0; i < sparks; i++) {
			double s = 0.12 + level.getRandom().nextDouble() * 0.2;
			level.addParticle(ParticleTypes.CRIT, at.x, at.y, at.z, look.x * s + (level.getRandom().nextDouble() - 0.5) * 0.08,
					look.y * s + (level.getRandom().nextDouble() - 0.5) * 0.08, look.z * s + (level.getRandom().nextDouble() - 0.5) * 0.08);
		}
		level.addParticle(ParticleTypes.SMOKE, at.x, at.y, at.z, look.x * 0.03, look.y * 0.03 + 0.02, look.z * 0.03);
	}

	/** Impact: block chips off the block a bullet stopped at, or a spark burst where it met flesh. */
	public static void impact(ClientLevel level, Vec3 from, Vec3 end) {
		Vec3 dir = end.subtract(from);
		if (dir.lengthSqr() < 1e-6) {
			return;
		}
		dir = dir.normalize();
		BlockPos pos = BlockPos.containing(end.add(dir.scale(0.05)));
		BlockState state = level.getBlockState(pos);
		if (!state.isAir() && !state.getCollisionShape(level, pos).isEmpty()) {
			BlockParticleOption chips = new BlockParticleOption(ParticleTypes.BLOCK, state);
			for (int i = 0; i < 6; i++) {
				level.addParticle(chips, end.x, end.y, end.z, -dir.x * 0.15 + (level.getRandom().nextDouble() - 0.5) * 0.2,
						0.1 + level.getRandom().nextDouble() * 0.15, -dir.z * 0.15 + (level.getRandom().nextDouble() - 0.5) * 0.2);
			}
			level.addParticle(ParticleTypes.SMOKE, end.x, end.y, end.z, 0, 0.02, 0);
		} else {
			for (int i = 0; i < 4; i++) {
				level.addParticle(ParticleTypes.CRIT, end.x, end.y, end.z, (level.getRandom().nextDouble() - 0.5) * 0.25,
						level.getRandom().nextDouble() * 0.2, (level.getRandom().nextDouble() - 0.5) * 0.25);
			}
		}
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
			if (path.size() >= 2) {
				impact(level, path.get(path.size() - 2), path.getLast());
			}
		}
	}
}
