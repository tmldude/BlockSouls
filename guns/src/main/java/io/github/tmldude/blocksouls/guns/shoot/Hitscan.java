package io.github.tmldude.blocksouls.guns.shoot;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** The bullet ray: stops at the first block (or ricochets off it) and picks the first entity along the way. */
public final class Hitscan {
	private Hitscan() {
	}

	/**
	 * @param path     start, each ricochet point, end
	 * @param hit      the entity hit, or {@code null}
	 * @param bounces  ricochets before the hit (or before the bullet stopped)
	 * @param distance total distance travelled to the hit / end
	 */
	public record Trace(List<Vec3> path, @Nullable EntityHitResult hit, int bounces, double distance) {
	}

	/**
	 * Traces from {@code from} along unit vector {@code dir} for {@code range} blocks, ricocheting off up to
	 * {@code maxBounces} block faces.
	 */
	public static Trace trace(Level level, Entity shooter, Vec3 from, Vec3 dir, double range, int maxBounces, double margin) {
		List<Vec3> path = new ArrayList<>();
		path.add(from);
		double remaining = range;
		double travelled = 0;
		int bounces = 0;

		while (true) {
			Vec3 to = from.add(dir.scale(remaining));
			BlockHitResult block = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, shooter));
			Vec3 end = block.getType() == HitResult.Type.MISS ? to : block.getLocation();

			EntityHitResult entity = ProjectileUtil.getEntityHitResult(level, shooter, from, end,
					new AABB(from, end).inflate(margin + 1), target -> canHit(shooter, target), (float) margin);
			if (entity != null) {
				path.add(entity.getLocation());
				return new Trace(path, entity, bounces, travelled + from.distanceTo(entity.getLocation()));
			}

			path.add(end);
			double segment = from.distanceTo(end);
			travelled += segment;
			remaining -= segment;
			if (block.getType() == HitResult.Type.MISS || bounces >= maxBounces || remaining <= 0.1) {
				return new Trace(path, null, bounces, travelled);
			}

			dir = reflect(dir, block.getDirection());
			// Step off the surface so the next clip doesn't hit the same face.
			from = end.add(dir.scale(0.01));
			bounces++;
		}
	}

	/** Mirror a direction off a block face. */
	public static Vec3 reflect(Vec3 dir, Direction face) {
		Vec3 normal = face.getUnitVec3();
		return dir.subtract(normal.scale(2 * dir.dot(normal)));
	}

	private static boolean canHit(Entity shooter, Entity target) {
		return target != shooter && target.isAlive() && target.isPickable() && !target.isSpectator()
				&& !shooter.isPassengerOfSameVehicle(target);
	}
}
