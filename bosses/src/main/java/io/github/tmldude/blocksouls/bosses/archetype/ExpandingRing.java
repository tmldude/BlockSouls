package io.github.tmldude.blocksouls.bosses.archetype;

import org.jspecify.annotations.Nullable;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import io.github.tmldude.blocksouls.bosses.api.AttackRun;
import io.github.tmldude.blocksouls.bosses.api.Hitboxes;
import io.github.tmldude.blocksouls.bosses.api.Telegraphs;

/**
 * Expanding rings: shockwaves roll out from the boss's feet along the ground. Each ring is low enough to jump (or roll
 * through), and a ring hits each player once.
 */
public class ExpandingRing extends Archetype {
	protected final ArchetypeParams.Ring params;

	public ExpandingRing(String name, ArchetypeParams.Ring params, Theme theme, @Nullable String animation) {
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
		run.setOrigin(ground(run.level(), run.boss().position()));
	}

	@Override
	public void windup(AttackRun run) {
		if (run.tick() % 3 == 0) {
			Telegraphs.circleFilling(run.level(), theme.outline(), theme.fill(), run.origin(), 2.5, run.progress());
			Telegraphs.circle(run.level(), theme.outline(), run.origin(), params.maxRadius);
			run.telegraphed();
		}
	}

	/** Whether the wave covers the direction from the origin to {@code point} (rings: everywhere). */
	protected boolean covers(AttackRun run, Vec3 point) {
		return true;
	}

	@Override
	public void active(AttackRun run) {
		int elapsed = -run.ticksBeforeActive();
		if (run.first()) {
			run.sound(theme.impact(), 2.5f, 0.8f);
		}
		int gap = Math.max(1, params.ringGapTicks);
		boolean anyAlive = false;
		Vec3 origin = run.origin();
		for (int ring = 0; ring < Math.max(1, params.rings); ring++) {
			int t = elapsed - ring * gap;
			double radius = t * params.speed;
			if (t < 0) {
				anyAlive = true;
				continue;
			}
			if (radius > params.maxRadius) {
				continue;
			}
			anyAlive = true;
			if (run.tick() % 2 == 0) {
				drawRing(run, radius);
			}
			double outer = radius + params.thickness;
			double inner = Math.max(0, radius - params.thickness);
			for (LivingEntity victim : Hitboxes.inCircle(run.boss(), origin, outer, params.height)) {
				if (Hitboxes.horizontalDistance(victim, origin) >= inner && covers(run, victim.position())) {
					// One hit per ring: the interval is the gap between rings.
					run.hurtEvery(victim, params.stats.damage, gap);
				}
			}
		}
		if (!anyAlive) {
			run.endActive();
		}
	}

	protected void drawRing(AttackRun run, double radius) {
		int points = Math.max(8, (int) (radius * 2 * Math.PI * 1.2));
		Vec3 origin = run.origin();
		for (int i = 0; i < points; i++) {
			double a = i * 2 * Math.PI / points;
			Vec3 p = origin.add(Math.cos(a) * radius, 0.15, Math.sin(a) * radius);
			if (covers(run, p)) {
				run.level().sendParticles(theme.burst(), true, true, p.x, p.y, p.z, 1, 0.05, 0.1, 0.05, 0.01);
			}
		}
	}
}
