package io.github.tmldude.blocksouls.bosses.api;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

/**
 * Particle shapes for telegraphs (and for showing where an attack hit). Particles are forced, so they show at any
 * distance and with reduced particle settings: a telegraph players can't see isn't fair. Draw them every few ticks
 * during the windup; the {@code progress} variants fill in as the windup runs out, so the moment of impact reads.
 */
public final class Telegraphs {
	private Telegraphs() {
	}

	public static void at(ServerLevel level, ParticleOptions particle, Vec3 pos, int count, double spread, double speed) {
		level.sendParticles(particle, true, true, pos.x, pos.y, pos.z, count, spread, spread, spread, speed);
	}

	/** A ring on the ground ({@code center.y} should be the floor; particles sit 0.1 above it). */
	public static void circle(ServerLevel level, ParticleOptions particle, Vec3 center, double radius) {
		int points = Math.max(8, (int) (radius * 2 * Math.PI * 1.5));
		for (int i = 0; i < points; i++) {
			double a = i * 2 * Math.PI / points;
			level.sendParticles(particle, true, true, center.x + Math.cos(a) * radius, center.y + 0.1, center.z + Math.sin(a) * radius,
					1, 0, 0, 0, 0);
		}
	}

	/** Outer ring plus an inner ring growing to the edge as {@code progress} goes 0 → 1. */
	public static void circleFilling(ServerLevel level, ParticleOptions outline, ParticleOptions fill, Vec3 center, double radius, float progress) {
		circle(level, outline, center, radius);
		if (progress > 0.05f) {
			circle(level, fill, center, radius * Math.min(1f, progress));
		}
	}

	/** A strip along horizontal unit vector {@code dir}: two edge lines, plus a center line filling toward the far end. */
	public static void line(ServerLevel level, ParticleOptions particle, Vec3 from, Vec3 dir, double length, double halfWidth) {
		Vec3 side = new Vec3(-dir.z, 0, dir.x).scale(halfWidth);
		int points = Math.max(2, (int) (length * 2));
		for (int i = 0; i <= points; i++) {
			Vec3 p = from.add(dir.scale(length * i / points));
			level.sendParticles(particle, true, true, p.x + side.x, p.y + 0.1, p.z + side.z, 1, 0, 0, 0, 0);
			level.sendParticles(particle, true, true, p.x - side.x, p.y + 0.1, p.z - side.z, 1, 0, 0, 0, 0);
		}
	}

	public static void lineFilling(ServerLevel level, ParticleOptions outline, ParticleOptions fill, Vec3 from, Vec3 dir, double length,
			double halfWidth, float progress) {
		line(level, outline, from, dir, length, halfWidth);
		int points = Math.max(1, (int) (length * 2 * progress));
		for (int i = 0; i <= points; i++) {
			Vec3 p = from.add(dir.scale(length * progress * i / points));
			level.sendParticles(fill, true, true, p.x, p.y + 0.1, p.z, 1, 0, 0, 0, 0);
		}
	}

	/** The edge of a sector: an arc at {@code radius} plus its two side lines. Covers cones and sweeps. */
	public static void arc(ServerLevel level, ParticleOptions particle, Vec3 origin, Vec3 dir, double radius, double angleDegrees) {
		double half = Math.toRadians(Math.min(360, angleDegrees) / 2);
		double base = Math.atan2(dir.z, dir.x);
		int points = Math.max(6, (int) (radius * Math.toRadians(angleDegrees) * 1.5));
		for (int i = 0; i <= points; i++) {
			double a = base - half + 2 * half * i / points;
			level.sendParticles(particle, true, true, origin.x + Math.cos(a) * radius, origin.y + 0.1, origin.z + Math.sin(a) * radius,
					1, 0, 0, 0, 0);
		}
		if (angleDegrees < 360) {
			for (double sign : new double[] {-1, 1}) {
				double a = base + sign * half;
				Vec3 edge = new Vec3(Math.cos(a), 0, Math.sin(a));
				int n = Math.max(2, (int) radius * 2);
				for (int i = 1; i < n; i++) {
					Vec3 p = origin.add(edge.scale(radius * i / n));
					level.sendParticles(particle, true, true, p.x, p.y + 0.1, p.z, 1, 0, 0, 0, 0);
				}
			}
		}
	}

	/** A rising column (pillars, the field-boss beacon). */
	public static void column(ServerLevel level, ParticleOptions particle, Vec3 base, double height, double spread, int perBlock) {
		for (int y = 0; y < height; y++) {
			level.sendParticles(particle, true, true, base.x, base.y + y, base.z, perBlock, spread, 0.4, spread, 0.01);
		}
	}
}
