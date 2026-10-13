package io.github.tmldude.blocksouls.bosses.archetype;

import java.util.Iterator;
import java.util.List;

import org.jspecify.annotations.Nullable;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import io.github.tmldude.blocksouls.bosses.api.AttackRun;
import io.github.tmldude.blocksouls.bosses.api.Hitboxes;
import io.github.tmldude.blocksouls.bosses.api.Telegraphs;

/**
 * Spiral (flight phase): bolts stream out along rotating arms on the floor under the boss. The arms turn slowly, so
 * you walk around with them; the telegraph shows the arms' starting lines.
 */
public class Spiral extends Archetype {
	private final ArchetypeParams.Spiral params;

	public Spiral(String name, ArchetypeParams.Spiral params, Theme theme, @Nullable String animation) {
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
		run.setOrigin(ground(run.level(), run.boss().position(), params.floorSearch));
	}

	private Vec3 arm(AttackRun run, int i, int tick) {
		return rotate(run.direction(), 360.0 * i / Math.max(1, params.arms) + params.degreesPerTick * tick);
	}

	@Override
	public void windup(AttackRun run) {
		if (run.tick() % 3 == 0) {
			Telegraphs.circleFilling(run.level(), theme.outline(), theme.fill(), run.origin(), 2.0, run.progress());
			for (int i = 0; i < Math.max(1, params.arms); i++) {
				Telegraphs.line(run.level(), theme.outline(), run.origin(), arm(run, i, 0), params.length, 0.4);
			}
			run.telegraphed();
		}
	}

	@Override
	public void active(AttackRun run) {
		if (run.first()) {
			run.sound(theme.impact(), run.origin(), 2.5f, 0.8f);
		}
		List<Vec3> bolts = run.points();
		// Emit (bolts: position, velocity pairs) until the last stretch, so the final bolts still reach the edge.
		int stopEmitting = run.activeTicks() - (int) Math.ceil(params.length / Math.max(0.05, params.boltSpeed));
		if (run.tick() % Math.max(1, params.emitEveryTicks) == 0 && run.tick() <= Math.max(1, stopEmitting)) {
			for (int i = 0; i < Math.max(1, params.arms); i++) {
				Vec3 dir = arm(run, i, run.tick());
				bolts.add(run.origin().add(dir.scale(1.2)));
				bolts.add(dir.scale(params.boltSpeed));
			}
		}
		Iterator<Vec3> it = bolts.iterator();
		java.util.ArrayList<Vec3> kept = new java.util.ArrayList<>();
		while (it.hasNext()) {
			Vec3 pos = it.next();
			Vec3 vel = it.next();
			pos = pos.add(vel);
			if (pos.distanceToSqr(run.origin()) > params.length * params.length) {
				continue;
			}
			run.level().sendParticles(theme.burst(), true, true, pos.x, pos.y + 0.4, pos.z, 1, 0.05, 0.05, 0.05, 0);
			boolean hit = false;
			for (LivingEntity victim : Hitboxes.inCircle(run.boss(), pos, params.boltRadius, 1.6)) {
				hit |= run.hurtEvery(victim, params.stats.damage, 10);
			}
			if (!hit) {
				kept.add(pos);
				kept.add(vel);
			}
		}
		bolts.clear();
		bolts.addAll(kept);
	}
}
