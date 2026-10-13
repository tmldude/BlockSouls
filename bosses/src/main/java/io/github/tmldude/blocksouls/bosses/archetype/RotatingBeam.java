package io.github.tmldude.blocksouls.bosses.archetype;

import org.jspecify.annotations.Nullable;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import io.github.tmldude.blocksouls.bosses.api.AttackRun;
import io.github.tmldude.blocksouls.bosses.api.Hitboxes;
import io.github.tmldude.blocksouls.bosses.api.Telegraphs;

/**
 * Rotating beam: a beam from the boss sweeps around it. The telegraph shows the start line and the whole swept arc;
 * outrun it, or jump-roll through it.
 */
public class RotatingBeam extends Archetype {
	private final ArchetypeParams.Beam params;

	public RotatingBeam(String name, ArchetypeParams.Beam params, Theme theme, @Nullable String animation) {
		super(name, params.stats, theme, animation);
		this.params = params;
	}

	@Override
	public double trackFraction() {
		return 0.4;
	}

	@Override
	public void start(AttackRun run) {
		super.start(run);
		// Sweep direction, stored as the first point's x: +1 or -1.
		run.points().add(new Vec3(run.random().nextBoolean() ? 1 : -1, 0, 0));
	}

	private double sign(AttackRun run) {
		return run.points().isEmpty() ? 1 : run.points().getFirst().x;
	}

	private double sweep() {
		return Math.min(360, params.degreesPerTick * params.stats.activeTicks);
	}

	@Override
	public void windup(AttackRun run) {
		if (run.tick() % 3 == 0) {
			Vec3 from = run.boss().position();
			Telegraphs.lineFilling(run.level(), theme.outline(), theme.fill(), from, run.direction(), params.length, params.halfWidth,
					run.progress());
			// The swept sector, centered halfway through the turn.
			Vec3 mid = rotate(run.direction(), sign(run) * sweep() / 2);
			Telegraphs.arc(run.level(), theme.outline(), from, mid, params.length, sweep());
			run.telegraphed();
		}
	}

	@Override
	public void active(AttackRun run) {
		if (run.first()) {
			run.sound(theme.impact(), 2.5f, 0.7f);
		}
		Vec3 dir = rotate(run.direction(), sign(run) * params.degreesPerTick * run.tick());
		run.boss().face(dir);
		Vec3 from = run.boss().position().add(0, 0.2, 0);
		for (double d = 1; d <= params.length; d += 0.6) {
			Vec3 p = from.add(dir.scale(d));
			run.level().sendParticles(theme.burst(), true, true, p.x, p.y + 0.8, p.z, 1, 0.08, 0.3, 0.08, 0);
		}
		for (LivingEntity victim : Hitboxes.inLine(run.boss(), from, dir, params.length, params.halfWidth, params.height)) {
			run.hurtEvery(victim, params.stats.damage, params.hitIntervalTicks);
		}
	}
}
