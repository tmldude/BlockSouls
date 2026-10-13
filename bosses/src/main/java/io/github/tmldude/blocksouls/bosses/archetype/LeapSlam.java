package io.github.tmldude.blocksouls.bosses.archetype;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import io.github.tmldude.blocksouls.bosses.api.AttackRun;
import io.github.tmldude.blocksouls.bosses.api.BossEntity;
import io.github.tmldude.blocksouls.bosses.api.Hitboxes;
import io.github.tmldude.blocksouls.bosses.api.Telegraphs;

/**
 * Leap: the boss crouches, jumps, and comes down on where its target stood at take-off. A shadow marks the landing
 * spot for the whole flight; the slam hits a circle around it.
 */
public class LeapSlam extends Archetype {
	private final ArchetypeParams.Leap params;

	public LeapSlam(String name, ArchetypeParams.Leap params, Theme theme, @Nullable String animation) {
		super(name, params.stats, theme, animation);
		this.params = params;
	}

	@Override
	public double trackFraction() {
		return 0;
	}

	@Override
	public boolean canUse(BossEntity boss, LivingEntity target) {
		return boss.onGround();
	}

	private Vec3 landing(AttackRun run) {
		BossEntity boss = run.boss();
		LivingEntity target = run.target();
		Vec3 want = target != null ? target.position() : boss.position().add(run.direction().scale(params.maxDistance / 2));
		Vec3 delta = want.subtract(boss.position());
		Vec3 flat = new Vec3(delta.x, 0, delta.z);
		if (flat.length() > params.maxDistance) {
			want = boss.position().add(flat.normalize().scale(params.maxDistance));
		}
		return ground(run.level(), inArena(boss, want));
	}

	@Override
	public void start(AttackRun run) {
		super.start(run);
		run.points().add(run.boss().position());
		run.setOrigin(landing(run));
	}

	@Override
	public void windup(AttackRun run) {
		BossEntity boss = run.boss();
		int crouch = Math.min(params.crouchTicks, run.stageLength() - 1);
		if (run.tick() < crouch) {
			run.setOrigin(landing(run));
			run.points().set(0, boss.position());
			boss.face(run.origin().subtract(boss.position()));
		} else {
			// Flight: a parabola from take-off to the landing spot, done when the windup runs out.
			double t = (run.tick() - crouch + 1) / (double) Math.max(1, run.stageLength() - crouch);
			Vec3 from = run.points().getFirst();
			Vec3 to = run.origin();
			Vec3 at = from.lerp(to, t).add(0, 4 * params.apexHeight * t * (1 - t), 0);
			boss.setDeltaMovement(Vec3.ZERO);
			boss.setPos(at);
			boss.resetFallDistance();
			if (run.tick() == crouch) {
				Telegraphs.at(run.level(), ParticleTypes.POOF, from, 16, 0.6, 0.05);
			}
		}
		if (run.tick() % 2 == 0) {
			// The shadow: a dark disc that fills toward impact.
			Telegraphs.circleFilling(run.level(), theme.outline(), ParticleTypes.LARGE_SMOKE, run.origin(), params.radius, run.progress());
			Telegraphs.at(run.level(), ParticleTypes.SMOKE, run.origin().add(0, 0.1, 0), 6, params.radius * 0.3, 0);
			run.telegraphed();
		}
	}

	@Override
	public void active(AttackRun run) {
		if (!run.first()) {
			return;
		}
		BossEntity boss = run.boss();
		boss.setPos(run.origin());
		boss.setDeltaMovement(Vec3.ZERO);
		run.sound(theme.impact(), 3.0f, 0.7f);
		run.level().sendParticles(ParticleTypes.EXPLOSION, run.origin().x, run.origin().y + 0.3, run.origin().z, 2, 0.5, 0, 0.5, 0);
		Telegraphs.circle(run.level(), theme.burst(), run.origin(), params.radius);
		for (LivingEntity victim : Hitboxes.inCircle(boss, run.origin(), params.radius, 2.5)) {
			if (run.hurt(victim)) {
				Vec3 away = AttackRun.flatDirection(run.origin(), victim.position(), run.direction());
				victim.push(away.x * 0.6, 0.5, away.z * 0.6);
				victim.needsSync = true;
			}
		}
	}

	@Override
	public void end(AttackRun run) {
		BossEntity boss = run.boss();
		// Cut short mid-air (reset, death): drop straight down rather than hanging.
		boss.setDeltaMovement(0, Math.min(0, boss.getDeltaMovement().y), 0);
		boss.resetFallDistance();
	}
}
