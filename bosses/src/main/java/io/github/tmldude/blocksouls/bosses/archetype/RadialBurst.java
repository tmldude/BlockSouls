package io.github.tmldude.blocksouls.bosses.archetype;

import org.jspecify.annotations.Nullable;

import net.minecraft.world.phys.Vec3;

import io.github.tmldude.blocksouls.bosses.api.AttackRun;
import io.github.tmldude.blocksouls.bosses.api.Telegraphs;

/**
 * Radial burst with a gap: a ring too tall to jump, except for one safe lane the telegraph lights up. Read the lane,
 * stand in it.
 */
public class RadialBurst extends ExpandingRing {
	private final ArchetypeParams.Burst burst;

	public RadialBurst(String name, ArchetypeParams.Burst params, Theme theme, @Nullable String animation) {
		super(name, params, theme, animation);
		this.burst = params;
	}

	@Override
	public void start(AttackRun run) {
		super.start(run);
		// The safe lane points anywhere; the burst isn't aimed.
		run.setDirection(rotate(run.direction(), run.random().nextDouble() * 360));
	}

	@Override
	public void windup(AttackRun run) {
		super.windup(run);
		if (run.tick() % 3 == 0) {
			// Light the lane from the boss to the edge.
			for (double r = 3; r <= params.maxRadius; r += 2.5) {
				Telegraphs.arc(run.level(), theme.fill(), run.origin(), run.direction(), r, burst.gapDegrees);
			}
		}
	}

	@Override
	protected boolean covers(AttackRun run, Vec3 point) {
		Vec3 to = point.subtract(run.origin());
		double angle = Math.toDegrees(Math.atan2(to.z, to.x) - Math.atan2(run.direction().z, run.direction().x));
		angle = ((angle % 360) + 540) % 360 - 180;
		return Math.abs(angle) > burst.gapDegrees / 2;
	}
}
