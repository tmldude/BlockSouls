package io.github.tmldude.blocksouls.mobs.ai;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import io.github.tmldude.blocksouls.common.CommonTags;

/** Area hits for slams and bursts. Never touches terrain; wave mobs don't hurt each other. */
public final class Shockwave {
	private Shockwave() {
	}

	/**
	 * Damages every living entity within {@code radius} of {@code center} (except the source and other wave mobs),
	 * pushing them away and up by {@code knockback}. Damage falls off to half at the edge.
	 *
	 * @return how many entities were hit
	 */
	public static int hit(ServerLevel level, Mob source, Vec3 center, double radius, double damage, double knockback) {
		DamageSource damageSource = level.damageSources().mobAttack(source);
		int hits = 0;
		for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, source.getBoundingBox().move(center.subtract(source.position())).inflate(radius),
				e -> e != source && e.isAlive() && !e.is(CommonTags.WAVE_MOBS) && !e.isSpectator())) {
			double dist = entity.position().distanceTo(center);
			if (dist > radius) {
				continue;
			}
			float scaled = (float) (damage * (1 - 0.5 * dist / radius));
			if (entity.hurtServer(level, damageSource, scaled)) {
				hits++;
			}
			if (knockback > 0) {
				Vec3 away = new Vec3(entity.getX() - center.x, 0, entity.getZ() - center.z);
				away = away.lengthSqr() < 1.0E-4 ? Vec3.ZERO : away.normalize();
				double strength = knockback * (1 - 0.5 * dist / radius);
				entity.push(away.x * strength, 0.35 + 0.25 * strength, away.z * strength);
				entity.syncVelocity = true;
			}
		}
		return hits;
	}

	/** Dust thrown up from the floor around {@code center}, in a ring of {@code radius}. */
	public static void floorDust(ServerLevel level, Vec3 center, double radius) {
		BlockState floor = level.getBlockState(BlockPos.containing(center.x, center.y - 0.5, center.z));
		if (floor.isAir()) {
			return;
		}
		BlockParticleOption particle = new BlockParticleOption(ParticleTypes.BLOCK, floor);
		for (int i = 0; i < 48; i++) {
			double angle = i * Math.PI * 2 / 48;
			level.sendParticles(particle, center.x + Math.cos(angle) * radius * 0.8, center.y + 0.1, center.z + Math.sin(angle) * radius * 0.8,
					3, 0.2, 0.1, 0.2, 0.15);
		}
		level.sendParticles(ParticleTypes.EXPLOSION, center.x, center.y + 0.3, center.z, 2, radius * 0.3, 0.1, radius * 0.3, 0);
	}
}
