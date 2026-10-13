package io.github.tmldude.blocksouls.abilities.effect;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import io.github.tmldude.blocksouls.common.player.PlayerStatus;

/** Who an ability may hit or help. Friendly fire is off: hostiles are living non-players; allies are players. */
public final class Targets {
	private Targets() {
	}

	public static boolean hostile(Entity entity, Entity caster) {
		return entity instanceof LivingEntity living && living.isAlive() && !(entity instanceof Player) && entity != caster
				&& !entity.isSpectator() && entity.isPickable();
	}

	public static boolean ally(Entity entity) {
		return entity instanceof ServerPlayer player && player.isAlive() && !player.isSpectator();
	}

	/** Living non-players within {@code radius} of {@code centre} (sphere, by hitbox distance). */
	public static List<LivingEntity> hostiles(ServerLevel level, Vec3 centre, double radius, Entity caster) {
		List<LivingEntity> out = new ArrayList<>();
		AABB box = new AABB(centre, centre).inflate(radius);
		for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, box, e -> hostile(e, caster))) {
			if (entity.getBoundingBox().distanceToSqr(centre) <= radius * radius) {
				out.add(entity);
			}
		}
		return out;
	}

	/** Players within {@code radius} of {@code centre}, the caster included; {@code radius <= 0} means the whole level. */
	public static List<ServerPlayer> allies(ServerLevel level, Vec3 centre, double radius) {
		List<ServerPlayer> out = new ArrayList<>();
		for (ServerPlayer player : level.players()) {
			if (ally(player) && (radius <= 0 || player.distanceToSqr(centre) <= radius * radius)) {
				out.add(player);
			}
		}
		return out;
	}

	/** Players within {@code radius} who are down (revive targets); never the caster. */
	public static List<ServerPlayer> downedAllies(ServerLevel level, Vec3 centre, double radius, ServerPlayer caster) {
		List<ServerPlayer> out = new ArrayList<>();
		for (ServerPlayer player : level.players()) {
			if (player != caster && player.isAlive() && PlayerStatus.isDowned(player) && player.distanceToSqr(centre) <= radius * radius) {
				out.add(player);
			}
		}
		return out;
	}

	/** Hostiles within {@code range} of the caster inside a cone of {@code coneDegrees} around {@code dir}. */
	public static List<LivingEntity> cone(ServerLevel level, ServerPlayer caster, Vec3 dir, double range, double coneDegrees) {
		List<LivingEntity> out = new ArrayList<>();
		double cosHalf = Math.cos(Math.toRadians(coneDegrees / 2));
		Vec3 eye = caster.getEyePosition();
		for (LivingEntity entity : hostiles(level, eye, range, caster)) {
			Vec3 to = entity.getBoundingBox().getCenter().subtract(eye);
			if (to.lengthSqr() < 1e-6 || to.normalize().dot(dir) >= cosHalf) {
				out.add(entity);
			}
		}
		return out;
	}

	/** A ray's result: the first matching entity, or where it stopped at a block (or ran out of range). */
	public record Ray(@Nullable EntityHitResult entity, Vec3 end, boolean hitBlock) {
	}

	/** Casts a ray from {@code from} along {@code dir}: first hostile, else the block it stops at. */
	public static Ray ray(ServerLevel level, ServerPlayer caster, Vec3 from, Vec3 dir, double range, double margin) {
		Vec3 to = from.add(dir.scale(range));
		BlockHitResult block = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster));
		Vec3 end = block.getType() == HitResult.Type.MISS ? to : block.getLocation();
		EntityHitResult entity = ProjectileUtil.getEntityHitResult(level, caster, from, end, new AABB(from, end).inflate(margin + 1),
				e -> hostile(e, caster), (float) margin);
		return new Ray(entity, entity != null ? entity.getLocation() : end, block.getType() != HitResult.Type.MISS && entity == null);
	}

	/** Like {@link #ray} but looks for an ally (another player), for the healing dart. */
	public static @Nullable ServerPlayer allyRay(ServerLevel level, ServerPlayer caster, Vec3 from, Vec3 dir, double range, double margin) {
		Vec3 to = from.add(dir.scale(range));
		BlockHitResult block = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster));
		Vec3 end = block.getType() == HitResult.Type.MISS ? to : block.getLocation();
		EntityHitResult hit = ProjectileUtil.getEntityHitResult(level, caster, from, end, new AABB(from, end).inflate(margin + 1),
				e -> e != caster && ally(e), (float) margin);
		return hit != null && hit.getEntity() instanceof ServerPlayer player ? player : null;
	}

	/** Pushes an entity with a velocity and makes sure the client sees it. */
	public static void launch(Entity entity, Vec3 velocity) {
		entity.setDeltaMovement(velocity);
		entity.syncVelocity = true;
	}

	/** A random direction within {@code degrees} of {@code dir} (uniform over the cone's disk). */
	public static Vec3 spread(Vec3 dir, double degrees, RandomSource random) {
		if (degrees <= 0) {
			return dir;
		}
		double rad = Math.toRadians(degrees);
		Vec3 any = Math.abs(dir.y) < 0.9 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0);
		Vec3 u = dir.cross(any).normalize();
		Vec3 v = dir.cross(u).normalize();
		double angle = random.nextDouble() * Math.PI * 2;
		double r = Math.sqrt(random.nextDouble()) * rad;
		return dir.scale(Math.cos(r)).add(u.scale(Math.sin(r) * Math.cos(angle))).add(v.scale(Math.sin(r) * Math.sin(angle))).normalize();
	}
}
