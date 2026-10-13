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
 * Charge that sticks: a straight dash; if it slams into terrain, the boss is stuck there for 2 s. Bait it into a
 * wall, then punish.
 */
public class ChargeStick extends Archetype {
	private final ArchetypeParams.Charge params;

	public ChargeStick(String name, ArchetypeParams.Charge params, Theme theme, @Nullable String animation) {
		super(name, params.stats, theme, animation);
		this.params = params;
	}

	@Override
	public double trackFraction() {
		return 0.6;
	}

	@Override
	public void windup(AttackRun run) {
		if (run.tick() % 3 == 0) {
			Telegraphs.lineFilling(run.level(), theme.outline(), theme.fill(), run.boss().position(), run.direction(), params.distance,
					run.boss().getBbWidth() / 2 + 0.4, run.progress());
			run.telegraphed();
		}
	}

	/** Whether this run ended in a wall (tests and the stuck particles). */
	public static boolean stuck(AttackRun run) {
		return !run.points().isEmpty();
	}

	@Override
	public void active(AttackRun run) {
		BossEntity boss = run.boss();
		double speed = params.distance / Math.max(1, run.stageLength());
		Vec3 dir = run.direction();
		boss.face(dir);
		if (run.first()) {
			run.sound(theme.impact(), 2.0f, 0.8f);
		}
		if (run.tick() > 1 && boss.horizontalCollision) {
			run.points().add(boss.position());
			run.level().sendParticles(ParticleTypes.EXPLOSION, boss.getX(), boss.getY() + 1, boss.getZ(), 1, 0, 0, 0, 0);
			run.sound(theme.impact(), 2.0f, 0.6f);
			boss.setDeltaMovement(0, boss.getDeltaMovement().y, 0);
			run.endActive();
			run.extendRecovery(params.stuckTicks);
			return;
		}
		boss.setDeltaMovement(dir.x * speed, boss.getDeltaMovement().y, dir.z * speed);
		Telegraphs.at(run.level(), theme.burst(), boss.position().add(0, 0.3, 0), 3, 0.4, 0.02);
		for (LivingEntity victim : Hitboxes.inBox(boss, boss.getBoundingBox().inflate(0.6, 0, 0.6).expandTowards(dir.scale(speed)))) {
			if (run.hurt(victim)) {
				victim.knockback(params.knockback, -dir.x, -dir.z, run.level().damageSources().mobAttack(boss), 0);
				victim.needsSync = true;
			}
		}
	}

	@Override
	public void recovery(AttackRun run) {
		BossEntity boss = run.boss();
		boss.setDeltaMovement(0, boss.getDeltaMovement().y, 0);
		if (stuck(run) && run.tick() % 4 == 0) {
			// Wedged in: dust and debris off the wall.
			Telegraphs.at(run.level(), ParticleTypes.POOF, boss.position().add(run.direction().scale(boss.getBbWidth() / 2)).add(0, 1, 0), 3,
					0.4, 0.02);
		}
	}

	@Override
	public void end(AttackRun run) {
		BossEntity boss = run.boss();
		boss.setDeltaMovement(0, boss.getDeltaMovement().y, 0);
	}
}
