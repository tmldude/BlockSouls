package io.github.tmldude.blocksouls.bosses.archetype;

import org.jspecify.annotations.Nullable;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import io.github.tmldude.blocksouls.bosses.api.AttackRun;
import io.github.tmldude.blocksouls.bosses.api.Hitboxes;
import io.github.tmldude.blocksouls.bosses.api.Telegraphs;

/**
 * Line / cross AoE: two or four arms burst out from a point under the target. Step out of the lines, not away from
 * the center.
 */
public class LineCross extends Archetype {
	private final ArchetypeParams.Cross params;

	public LineCross(String name, ArchetypeParams.Cross params, Theme theme, @Nullable String animation) {
		super(name, params.stats, theme, animation);
		this.params = params;
	}

	@Override
	public double trackFraction() {
		return 0;
	}

	@Override
	public void start(AttackRun run) {
		super.start(run);
		LivingEntity target = run.target();
		Vec3 at = target != null ? target.position() : run.boss().position();
		run.setOrigin(ground(run.level(), at));
		// Cross on the cardinals or the diagonals, so it can't be pre-learned.
		run.setDirection(rotate(new Vec3(1, 0, 0), run.random().nextBoolean() ? 0 : 45));
	}

	private Vec3 arm(AttackRun run, int i) {
		int arms = Math.max(2, params.arms);
		return rotate(run.direction(), 360.0 * i / arms);
	}

	@Override
	public void windup(AttackRun run) {
		if (run.tick() % 3 == 0) {
			for (int i = 0; i < Math.max(2, params.arms); i++) {
				Telegraphs.lineFilling(run.level(), theme.outline(), theme.fill(), run.origin(), arm(run, i), params.length, params.halfWidth,
						run.progress());
			}
			run.telegraphed();
		}
	}

	@Override
	public void active(AttackRun run) {
		if (run.first()) {
			run.sound(theme.impact(), run.origin(), 2.5f, 0.9f);
		}
		for (int i = 0; i < Math.max(2, params.arms); i++) {
			Vec3 dir = arm(run, i);
			if (run.tick() % 2 == 0) {
				for (double d = 0; d <= params.length; d += 0.75) {
					Vec3 p = run.origin().add(dir.scale(d));
					run.level().sendParticles(theme.burst(), true, true, p.x, p.y + 0.5, p.z, 2, 0.2, 0.5, 0.2, 0.02);
				}
			}
			for (LivingEntity victim : Hitboxes.inLine(run.boss(), run.origin(), dir, params.length, params.halfWidth, params.height)) {
				run.hurt(victim);
			}
		}
	}
}
