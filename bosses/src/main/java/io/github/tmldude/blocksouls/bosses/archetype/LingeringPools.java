package io.github.tmldude.blocksouls.bosses.archetype;

import java.util.List;

import org.jspecify.annotations.Nullable;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import io.github.tmldude.blocksouls.bosses.api.AttackRun;
import io.github.tmldude.blocksouls.bosses.api.Hitboxes;
import io.github.tmldude.blocksouls.bosses.api.Telegraphs;

/**
 * Lingering pools: circles mark the floor under each player (plus a few strays), then turn into pools that hurt for
 * as long as you stand in them. Area denial: the fight moves.
 */
public class LingeringPools extends Archetype {
	private final ArchetypeParams.Pools params;

	public LingeringPools(String name, ArchetypeParams.Pools params, Theme theme, @Nullable String animation) {
		super(name, params.stats, theme, animation);
		this.params = params;
	}

	@Override
	public void start(AttackRun run) {
		super.start(run);
		List<LivingEntity> targets = fighters(run);
		for (LivingEntity target : targets) {
			if (run.points().size() < params.pools) {
				run.points().add(ground(run.level(), target.position()));
			}
		}
		int guard = 0;
		while (run.points().size() < params.pools && guard++ < 30) {
			Vec3 near = targets.isEmpty() ? run.boss().position() : targets.get(run.random().nextInt(targets.size())).position();
			double a = run.random().nextDouble() * Math.PI * 2;
			double r = params.radius * 1.5 + run.random().nextDouble() * Math.max(0.5, params.scatter - params.radius * 1.5);
			run.points().add(ground(run.level(), inArena(run.boss(), near.add(Math.cos(a) * r, 0, Math.sin(a) * r))));
		}
	}

	@Override
	public void windup(AttackRun run) {
		if (run.tick() % 3 == 0) {
			for (Vec3 point : run.points()) {
				Telegraphs.circleFilling(run.level(), theme.outline(), theme.fill(), point, params.radius, run.progress());
			}
			run.telegraphed();
		}
	}

	@Override
	public void active(AttackRun run) {
		if (run.first()) {
			for (Vec3 point : run.points()) {
				run.sound(theme.impact(), point, 1.5f, 0.8f + run.random().nextFloat() * 0.3f);
			}
		}
		for (Vec3 point : run.points()) {
			if (run.tick() % 4 == 0) {
				Telegraphs.circle(run.level(), theme.outline(), point, params.radius);
				run.level().sendParticles(theme.burst(), true, true, point.x, point.y + 0.2, point.z, (int) (params.radius * 4),
						params.radius * 0.5, 0.05, params.radius * 0.5, 0.01);
			}
			for (LivingEntity victim : Hitboxes.inCircle(run.boss(), point, params.radius, 1.2)) {
				run.hurtEvery(victim, params.stats.damage, params.hitIntervalTicks);
			}
		}
	}
}
