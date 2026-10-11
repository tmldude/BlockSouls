package io.github.tmldude.blocksouls.bosses.raid;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Void orbs: slow homing spheres of violet dark that hum as they drift after a player and pop on contact. Slow
 * enough to outrun or lead into a pillar; they burst on their own after a while. Server-side only (drawn with
 * particles), so they can't be stuck in blocks or lost on a chunk boundary.
 */
final class VoidOrbs {
	private static final DustParticleOptions SHELL = new DustParticleOptions(0x7A2FD6, 2.2f);
	private static final DustParticleOptions CORE = new DustParticleOptions(0x12051F, 2.6f);

	private static final class Orb {
		Vec3 pos;
		final UUID target;
		final float damage;
		int age;

		Orb(Vec3 pos, UUID target, float damage) {
			this.pos = pos;
			this.target = target;
			this.damage = damage;
		}
	}

	private final FinalDragon dragon;
	private final List<Orb> orbs = new ArrayList<>();

	VoidOrbs(FinalDragon dragon) {
		this.dragon = dragon;
	}

	int count() {
		return orbs.size();
	}

	void spawn(Vec3 at, LivingEntity target, float damage) {
		orbs.add(new Orb(at, target.getUUID(), damage));
	}

	void clear() {
		orbs.clear();
	}

	void tick(ServerLevel level) {
		FinalRaidConfig config = FinalRaids.CONFIG;
		for (var it = orbs.iterator(); it.hasNext();) {
			Orb orb = it.next();
			orb.age++;
			if (level.getEntity(orb.target) instanceof LivingEntity target && target.isAlive() && dragon.canHarm(target)) {
				Vec3 aim = target.position().add(0, target.getBbHeight() * 0.5, 0);
				Vec3 delta = aim.subtract(orb.pos);
				double length = delta.length();
				if (length > 1e-3) {
					orb.pos = orb.pos.add(delta.scale(Math.min(config.voidOrbSpeed, length) / length));
				}
			} else {
				orb.pos = orb.pos.add(0, -0.03, 0);
			}
			draw(level, orb);
			LivingEntity hit = null;
			for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, new AABB(orb.pos, orb.pos).inflate(config.voidOrbRadius),
					dragon::canHarm)) {
				hit = victim;
				break;
			}
			if (hit != null) {
				hit.hurtServer(level, level.damageSources().indirectMagic(dragon, dragon), orb.damage);
				pop(level, orb.pos);
				it.remove();
			} else if (orb.age >= config.voidOrbLifetimeTicks) {
				pop(level, orb.pos);
				it.remove();
			}
		}
	}

	private static void draw(ServerLevel level, Orb orb) {
		Vec3 p = orb.pos;
		if (orb.age % 2 == 0) {
			level.sendParticles(CORE, true, true, p.x, p.y, p.z, 2, 0.08, 0.08, 0.08, 0);
			level.sendParticles(SHELL, true, true, p.x, p.y, p.z, 4, 0.3, 0.3, 0.3, 0);
			level.sendParticles(ParticleTypes.PORTAL, true, true, p.x, p.y, p.z, 2, 0.2, 0.2, 0.2, 0.3);
		}
		if (orb.age % 20 == 1) {
			level.playSound(null, p.x, p.y, p.z, FinalRaidSounds.ORB, SoundSource.HOSTILE, 1.2f, 0.7f);
		}
	}

	private static void pop(ServerLevel level, Vec3 p) {
		level.sendParticles(ParticleTypes.REVERSE_PORTAL, true, true, p.x, p.y, p.z, 30, 0.3, 0.3, 0.3, 0.15);
		level.sendParticles(SHELL, true, true, p.x, p.y, p.z, 12, 0.5, 0.5, 0.5, 0);
		level.playSound(null, p.x, p.y, p.z, FinalRaidSounds.ORB_POP, SoundSource.HOSTILE, 1.5f, 1.2f);
	}
}
