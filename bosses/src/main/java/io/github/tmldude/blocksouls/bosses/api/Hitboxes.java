package io.github.tmldude.blocksouls.bosses.api;

import java.util.List;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Who an attack area touches. Every query returns only what the boss may hurt ({@link BossEntity#canHarm}): players
 * that aren't downed or spectating, plus the boss's current target. Shapes are flat on the ground with a height, so
 * jumping over a low shockwave works when {@code height} is small.
 */
public final class Hitboxes {
	private Hitboxes() {
	}

	/** Victims whose hitbox overlaps {@code box}. */
	public static List<LivingEntity> inBox(BossEntity boss, AABB box) {
		return boss.level().getEntitiesOfClass(LivingEntity.class, box, boss::canHarm);
	}

	/** Victims within {@code radius} (horizontally) of {@code center}, between its y and y + height. */
	public static List<LivingEntity> inCircle(BossEntity boss, Vec3 center, double radius, double height) {
		AABB box = new AABB(center.x - radius, center.y, center.z - radius, center.x + radius, center.y + height, center.z + radius);
		return inBox(boss, box.inflate(1)).stream()
				.filter(e -> horizontalDistance(e, center) <= radius + e.getBbWidth() / 2 && overlapsY(e, center.y, height))
				.toList();
	}

	/**
	 * Victims in a strip from {@code from} along horizontal unit vector {@code dir}: {@code length} long,
	 * {@code halfWidth} to each side, {@code height} tall.
	 */
	public static List<LivingEntity> inLine(BossEntity boss, Vec3 from, Vec3 dir, double length, double halfWidth, double height) {
		Vec3 to = from.add(dir.scale(length));
		AABB box = new AABB(from, to).inflate(halfWidth + 1, 0, halfWidth + 1).expandTowards(0, height, 0);
		return inBox(boss, box).stream().filter(e -> {
			double ex = e.getX() - from.x;
			double ez = e.getZ() - from.z;
			double along = ex * dir.x + ez * dir.z;
			double side = Math.abs(ex * dir.z - ez * dir.x);
			double pad = e.getBbWidth() / 2;
			return along >= -pad && along <= length + pad && side <= halfWidth + pad && overlapsY(e, from.y, height);
		}).toList();
	}

	/**
	 * Victims within {@code radius} of {@code origin} and within {@code angleDegrees / 2} of {@code dir} (a 270° sweep
	 * passes 270). Also used for cones.
	 */
	public static List<LivingEntity> inArc(BossEntity boss, Vec3 origin, Vec3 dir, double radius, double angleDegrees, double height) {
		double halfCos = Math.cos(Math.toRadians(Math.min(360, angleDegrees) / 2));
		return inCircle(boss, origin, radius, height).stream().filter(e -> {
			double ex = e.getX() - origin.x;
			double ez = e.getZ() - origin.z;
			double length = Math.sqrt(ex * ex + ez * ez);
			if (length < 0.75 || angleDegrees >= 360) {
				// Standing inside the boss counts as in front.
				return true;
			}
			return (ex * dir.x + ez * dir.z) / length >= halfCos;
		}).toList();
	}

	public static double horizontalDistance(LivingEntity e, Vec3 point) {
		double dx = e.getX() - point.x;
		double dz = e.getZ() - point.z;
		return Math.sqrt(dx * dx + dz * dz);
	}

	private static boolean overlapsY(LivingEntity e, double bottom, double height) {
		return e.getY() < bottom + height && e.getY() + e.getBbHeight() > bottom;
	}
}
