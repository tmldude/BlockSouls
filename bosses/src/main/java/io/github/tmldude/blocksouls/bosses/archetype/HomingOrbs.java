package io.github.tmldude.blocksouls.bosses.archetype;

import java.util.List;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import io.github.tmldude.blocksouls.bosses.api.AttackRun;
import io.github.tmldude.blocksouls.bosses.api.Telegraphs;

/**
 * Delayed-homing orbs: orbs bloom around the boss and hang there for the windup (the 1 s "delay"), then chase the
 * nearest player with a limited turn rate. Strafe, then cut sideways late: they can't turn that fast.
 */
public class HomingOrbs extends Archetype {
	private final ArchetypeParams.Orbs params;

	public HomingOrbs(String name, ArchetypeParams.Orbs params, Theme theme, @Nullable String animation) {
		super(name, params.stats, theme, animation);
		this.params = params;
	}

	@Override
	public void start(AttackRun run) {
		super.start(run);
		// points: position, velocity, position, velocity...; a spent orb's position is null.
		Vec3 center = run.boss().position().add(0, run.boss().getBbHeight() * 0.75, 0);
		int n = Math.max(1, params.orbs);
		for (int i = 0; i < n; i++) {
			double a = i * 2 * Math.PI / n;
			run.points().add(center.add(Math.cos(a) * params.spawnRadius, 0.3, Math.sin(a) * params.spawnRadius));
			run.points().add(new Vec3(Math.cos(a), 0.3, Math.sin(a)).normalize().scale(params.speed * 0.5));
		}
	}

	@Override
	public void windup(AttackRun run) {
		List<Vec3> points = run.points();
		for (int i = 0; i < points.size(); i += 2) {
			Vec3 p = points.get(i);
			// Hover, bobbing: the orbs themselves are the telegraph.
			Vec3 bob = p.add(0, Math.sin((run.tick() + i * 7) * 0.3) * 0.05, 0);
			points.set(i, bob);
			drawOrb(run, bob, run.progress());
		}
		run.telegraphed();
	}

	private void drawOrb(AttackRun run, Vec3 p, float charge) {
		Telegraphs.at(run.level(), theme.burst(), p, 3, 0.12 + 0.1 * charge, 0.0);
		if (run.tick() % 2 == 0) {
			Telegraphs.at(run.level(), theme.fill(), p, 1, 0.05, 0.0);
		}
	}

	@Override
	public void active(AttackRun run) {
		if (run.first()) {
			run.sound(theme.impact(), 2.0f, 1.2f);
		}
		List<Vec3> points = run.points();
		List<LivingEntity> targets = fighters(run);
		boolean any = false;
		for (int i = 0; i < points.size(); i += 2) {
			Vec3 pos = points.get(i);
			if (pos == null) {
				continue;
			}
			Vec3 vel = points.get(i + 1);
			LivingEntity nearest = null;
			double best = Double.MAX_VALUE;
			for (LivingEntity target : targets) {
				double d = target.position().distanceToSqr(pos);
				if (d < best) {
					best = d;
					nearest = target;
				}
			}
			if (nearest != null) {
				Vec3 want = nearest.getBoundingBox().getCenter().subtract(pos).normalize().scale(params.speed);
				vel = turn(vel.lengthSqr() < 1e-6 ? want : vel.normalize().scale(params.speed), want, params.turnDegreesPerTick);
			}
			pos = pos.add(vel);
			if (!run.level().getBlockState(net.minecraft.core.BlockPos.containing(pos)).getCollisionShape(run.level(),
					net.minecraft.core.BlockPos.containing(pos)).isEmpty()) {
				pop(run, pos);
				points.set(i, null);
				continue;
			}
			AABB box = new AABB(pos, pos).inflate(params.hitRadius);
			boolean popped = false;
			for (LivingEntity victim : run.level().getEntitiesOfClass(LivingEntity.class, box, e -> e.isAlive() && run.boss().canHarm(e))) {
				run.hurtEvery(victim, params.stats.damage, 1);
				popped = true;
			}
			if (popped) {
				pop(run, pos);
				points.set(i, null);
				continue;
			}
			points.set(i, pos);
			points.set(i + 1, vel);
			drawOrb(run, pos, 1);
			run.level().sendParticles(ParticleTypes.END_ROD, true, true, pos.x, pos.y, pos.z, 1, 0, 0, 0, 0);
			any = true;
		}
		if (!any) {
			run.endActive();
		}
	}

	private void pop(AttackRun run, Vec3 pos) {
		Telegraphs.at(run.level(), theme.burst(), pos, 12, 0.3, 0.05);
		run.sound(theme.impact(), pos, 1.0f, 1.5f);
	}

	/** Turns {@code from} toward {@code to} by at most {@code degrees}, keeping {@code to}'s length. */
	static Vec3 turn(Vec3 from, Vec3 to, double degrees) {
		Vec3 a = from.normalize();
		Vec3 b = to.normalize();
		double cos = Math.clamp(a.dot(b), -1, 1);
		double angle = Math.acos(cos);
		double max = Math.toRadians(degrees);
		if (angle <= max || angle < 1e-6) {
			return to;
		}
		// Slerp a fraction of the way.
		double t = max / angle;
		double sin = Math.sin(angle);
		Vec3 dir = a.scale(Math.sin((1 - t) * angle) / sin).add(b.scale(Math.sin(t * angle) / sin));
		return dir.normalize().scale(to.length());
	}

	@Override
	public void end(AttackRun run) {
		for (int i = 0; i < run.points().size(); i += 2) {
			Vec3 pos = run.points().get(i);
			if (pos != null && run.stage() != io.github.tmldude.blocksouls.bosses.api.Stage.WINDUP) {
				pop(run, pos);
			}
		}
	}
}
