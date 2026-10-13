package io.github.tmldude.blocksouls.bosses.archetype;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import io.github.tmldude.blocksouls.bosses.api.AttackRun;
import io.github.tmldude.blocksouls.bosses.api.BossEntity;
import io.github.tmldude.blocksouls.bosses.api.Hitboxes;
import io.github.tmldude.blocksouls.bosses.api.Telegraphs;
import io.github.tmldude.blocksouls.bosses.logic.Fairness;

/**
 * Burrow: the boss dives into the ground, a plume of dirt tracks its target under the floor, then stops and the
 * eruption circle fills; it bursts out there. Untouchable while under. Keep moving, then get out of the circle.
 */
public class Burrow extends Archetype {
	private final ArchetypeParams.Burrow params;

	public Burrow(String name, ArchetypeParams.Burrow params, Theme theme, @Nullable String animation) {
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

	private int warn(AttackRun run) {
		return Math.min(run.stageLength() - 1, Math.max(Fairness.BIG_HIT_MIN_WINDUP, params.warnTicks));
	}

	@Override
	public void start(AttackRun run) {
		super.start(run);
		BossEntity boss = run.boss();
		Vec3 at = ground(run.level(), boss.position());
		run.setOrigin(at);
		dirt(run, at, 40);
		boss.setBurrowed(true, at);
	}

	@Override
	public void windup(AttackRun run) {
		BossEntity boss = run.boss();
		Vec3 at = run.origin();
		boolean tracking = run.ticksBeforeActive() > warn(run);
		if (tracking) {
			LivingEntity target = run.target();
			if (target != null) {
				Vec3 to = new Vec3(target.getX() - at.x, 0, target.getZ() - at.z);
				double d = to.length();
				if (d > 1e-3) {
					at = at.add(to.scale(Math.min(d, params.tunnelSpeed) / d));
				}
				at = ground(run.level(), inArena(boss, at));
				run.setOrigin(at);
			}
			if (run.tick() % 2 == 0) {
				dirt(run, at, 6);
			}
			if (run.tick() % 8 == 0) {
				run.sound(net.minecraft.sounds.SoundEvents.GRAVEL_BREAK, at, 1.2f, 0.6f);
			}
		} else {
			if (run.tick() % 2 == 0) {
				Telegraphs.circleFilling(run.level(), theme.outline(), theme.fill(), at, params.radius, 1 - run.ticksBeforeActive() / (float) warn(run));
				dirt(run, at, 3);
			}
			run.telegraphed();
		}
		// Keep the hidden body under the plume.
		boss.setBurrowed(true, at);
	}

	@Override
	public void active(AttackRun run) {
		if (!run.first()) {
			return;
		}
		BossEntity boss = run.boss();
		Vec3 at = run.origin();
		boss.setBurrowed(false, at);
		boss.setDeltaMovement(0, params.launch, 0);
		run.sound(theme.impact(), at, 3.0f, 0.7f);
		dirt(run, at, 60);
		run.level().sendParticles(ParticleTypes.EXPLOSION, at.x, at.y + 0.5, at.z, 2, 0.6, 0.2, 0.6, 0);
		for (LivingEntity victim : Hitboxes.inCircle(boss, at, params.radius, 3)) {
			if (run.hurt(victim)) {
				victim.push(0, 0.8, 0);
				victim.needsSync = true;
			}
		}
	}

	@Override
	public void end(AttackRun run) {
		BossEntity boss = run.boss();
		if (boss.isBurrowed()) {
			// Cut short underground: surface where the plume was.
			boss.setBurrowed(false, run.origin());
		}
	}

	private static void dirt(AttackRun run, Vec3 at, int count) {
		BlockPos below = BlockPos.containing(at).below();
		BlockState state = run.level().getBlockState(below);
		if (state.isAir()) {
			state = Blocks.DIRT.defaultBlockState();
		}
		run.level().sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), true, true, at.x, at.y + 0.3, at.z, count, 0.5, 0.4,
				0.5, 0.15);
	}
}
