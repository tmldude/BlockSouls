package io.github.tmldude.blocksouls.bosses.furnace;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import io.github.tmldude.blocksouls.bosses.api.Attack;
import io.github.tmldude.blocksouls.bosses.api.AttackRun;
import io.github.tmldude.blocksouls.bosses.api.BossEntity;
import io.github.tmldude.blocksouls.bosses.api.Hitboxes;
import io.github.tmldude.blocksouls.bosses.api.Telegraphs;

/** The Furnace Knight's moves. Numbers come from {@link FurnaceKnightConfig}. */
final class FurnaceKnightAttacks {
	/** Ember-orange telegraph outline. */
	static final ParticleOptions OUTLINE = new DustParticleOptions(0xFF7A1A, 1.6f);
	/** Brighter fill as the windup runs out. */
	static final ParticleOptions FILL = new DustParticleOptions(0xFFD24A, 1.2f);

	private FurnaceKnightAttacks() {
	}

	private static FurnaceKnightConfig config() {
		return FurnaceKnight.config();
	}

	/** Feet level in front of the knight, where ground telegraphs start. */
	private static Vec3 front(AttackRun run, double ahead) {
		return run.boss().position().add(run.direction().scale(ahead));
	}

	/** Overhead slam: the cleaver comes down and a line of fire races 8 blocks forward. */
	static final class Slam extends Attack {
		Slam() {
			super("slam", config().slam);
		}

		@Override
		public void start(AttackRun run) {
			run.sound(FurnaceKnightSounds.WINDUP, 1.5f, 0.8f);
		}

		@Override
		public void windup(AttackRun run) {
			if (run.tick() % 3 == 0) {
				Telegraphs.lineFilling(run.level(), OUTLINE, FILL, front(run, 0.8), run.direction(), config().slamLength, config().slamHalfWidth,
						run.progress());
			}
		}

		@Override
		public void active(AttackRun run) {
			ServerLevel level = run.level();
			double length = config().slamLength;
			int ticks = Math.max(1, run.stageLength());
			// The wave travels: each active tick covers the next segment of the line.
			double from = length * run.tick() / ticks;
			double to = length * (run.tick() + 1) / ticks;
			Vec3 start = front(run, 0.8);
			if (run.first()) {
				run.sound(FurnaceKnightSounds.SLAM, 2.5f, 0.8f);
				level.sendParticles(ParticleTypes.EXPLOSION, start.x, start.y + 0.3, start.z, 1, 0, 0, 0, 0);
			}
			for (double d = from; d <= to; d += 0.5) {
				Vec3 p = start.add(run.direction().scale(d));
				level.sendParticles(ParticleTypes.FLAME, true, true, p.x, p.y + 0.2, p.z, 6, 0.4, 0.3, 0.4, 0.04);
				level.sendParticles(ParticleTypes.LAVA, true, true, p.x, p.y + 0.2, p.z, 1, 0.3, 0.1, 0.3, 0);
			}
			Vec3 segment = start.add(run.direction().scale(from));
			for (LivingEntity victim : Hitboxes.inLine(run.boss(), segment, run.direction(), to - from, config().slamHalfWidth, 1.5)) {
				if (run.hurt(victim)) {
					victim.push(run.direction().x * 0.4, 0.45, run.direction().z * 0.4);
					victim.needsSync = true;
				}
			}
		}
	}

	/** Sweeping cut: a wide, low swing around the knight. Jump it or roll through it. */
	static final class Sweep extends Attack {
		Sweep() {
			super("sweep", config().sweep);
		}

		@Override
		public void start(AttackRun run) {
			run.sound(FurnaceKnightSounds.WINDUP, 1.2f, 1.1f);
		}

		@Override
		public void windup(AttackRun run) {
			if (run.tick() % 3 == 0) {
				Telegraphs.arc(run.level(), OUTLINE, run.boss().position(), run.direction(), config().sweepRadius, config().sweepAngle);
				if (run.progress() > 0.5f) {
					Telegraphs.arc(run.level(), FILL, run.boss().position(), run.direction(), config().sweepRadius * run.progress(),
							config().sweepAngle);
				}
			}
		}

		@Override
		public void active(AttackRun run) {
			if (!run.first()) {
				return;
			}
			run.sound(FurnaceKnightSounds.SWING, 2.0f, 0.7f);
			Vec3 origin = run.boss().position();
			double half = Math.toRadians(config().sweepAngle / 2);
			double base = Math.atan2(run.direction().z, run.direction().x);
			for (int i = 0; i <= 18; i++) {
				double a = base - half + 2 * half * i / 18;
				double r = config().sweepRadius * 0.8;
				run.level().sendParticles(ParticleTypes.SWEEP_ATTACK, true, true, origin.x + Math.cos(a) * r, origin.y + 0.6,
						origin.z + Math.sin(a) * r, 1, 0, 0, 0, 0);
			}
			for (LivingEntity victim : Hitboxes.inArc(run.boss(), origin, run.direction(), config().sweepRadius, config().sweepAngle,
					config().sweepHeight)) {
				run.hurt(victim);
			}
		}
	}

	/** Furnace breath: the visor opens and a cone of fire pours out for two seconds, slowly following its target. */
	static final class Breath extends Attack {
		Breath() {
			super("breath", config().breath);
		}

		@Override
		public void start(AttackRun run) {
			run.sound(FurnaceKnightSounds.WINDUP, 1.2f, 0.6f);
		}

		private static Vec3 mouth(AttackRun run) {
			return run.boss().position().add(run.direction().scale(0.8));
		}

		@Override
		public void windup(AttackRun run) {
			if (run.tick() % 3 == 0) {
				Telegraphs.arc(run.level(), OUTLINE, mouth(run), run.direction(), config().breathLength, config().breathAngle);
			}
			if (run.progress() > 0.5f) {
				// The grate glows brighter: sparks from the visor.
				Vec3 head = run.boss().position().add(0, 2.9, 0).add(run.direction().scale(0.5));
				run.level().sendParticles(ParticleTypes.SMALL_FLAME, head.x, head.y, head.z, 2, 0.15, 0.1, 0.15, 0.01);
			}
		}

		@Override
		public void active(AttackRun run) {
			ServerLevel level = run.level();
			LivingEntity target = run.target();
			if (target != null && target.isAlive()) {
				run.setDirection(turnToward(run.direction(), target.position().subtract(run.boss().position()), config().breathTurnRate));
				run.boss().face(run.direction());
			}
			if (run.tick() % 5 == 0) {
				run.sound(FurnaceKnightSounds.BREATH, 1.6f, 0.7f + run.random().nextFloat() * 0.2f);
			}
			Vec3 head = run.boss().position().add(0, 2.7, 0).add(run.direction().scale(0.7));
			for (int i = 0; i < 6; i++) {
				double spread = Math.toRadians(config().breathAngle / 2) * (run.random().nextDouble() * 2 - 1);
				Vec3 dir = rotate(run.direction(), spread);
				double speed = 0.35 + run.random().nextDouble() * 0.2;
				level.sendParticles(ParticleTypes.FLAME, true, true, head.x, head.y, head.z, 0, dir.x, -0.35, dir.z, speed);
			}
			for (LivingEntity victim : Hitboxes.inArc(run.boss(), mouth(run), run.direction(), config().breathLength, config().breathAngle, 3.0)) {
				if (run.hurtEvery(victim, config().breath.damage, config().breathHitIntervalTicks)) {
					victim.setRemainingFireTicks(Math.max(victim.getRemainingFireTicks(), config().breathFireTicks));
				}
			}
		}

		private static Vec3 turnToward(Vec3 current, Vec3 wanted, double maxDegrees) {
			double a = Math.atan2(current.z, current.x);
			double b = Math.atan2(wanted.z, wanted.x);
			double delta = Math.atan2(Math.sin(b - a), Math.cos(b - a));
			double max = Math.toRadians(maxDegrees);
			double turned = a + Math.clamp(delta, -max, max);
			return new Vec3(Math.cos(turned), 0, Math.sin(turned));
		}

		private static Vec3 rotate(Vec3 dir, double radians) {
			double cos = Math.cos(radians);
			double sin = Math.sin(radians);
			return new Vec3(dir.x * cos - dir.z * sin, 0, dir.x * sin + dir.z * cos);
		}
	}

	/** Charge: drops its shoulder, then dashes in a straight line, ramming whoever is in the way. */
	static final class Charge extends Attack {
		Charge() {
			super("charge", config().charge);
		}

		@Override
		public double trackFraction() {
			return 0.6;
		}

		@Override
		public void start(AttackRun run) {
			run.sound(FurnaceKnightSounds.CHARGE, 2.0f, 0.8f);
		}

		@Override
		public void windup(AttackRun run) {
			if (run.tick() % 3 == 0) {
				Telegraphs.lineFilling(run.level(), OUTLINE, FILL, run.boss().position(), run.direction(), config().chargeDistance,
						run.boss().getBbWidth() / 2 + 0.4, run.progress());
			}
		}

		@Override
		public void active(AttackRun run) {
			BossEntity boss = run.boss();
			double speed = config().chargeDistance / Math.max(1, run.stageLength());
			Vec3 dir = run.direction();
			boss.face(dir);
			boss.setDeltaMovement(dir.x * speed, boss.getDeltaMovement().y, dir.z * speed);
			if (run.tick() > 1 && boss.horizontalCollision) {
				// Hit a wall: stop and stagger in recovery.
				run.level().sendParticles(ParticleTypes.EXPLOSION, boss.getX(), boss.getY() + 1, boss.getZ(), 1, 0, 0, 0, 0);
				run.sound(FurnaceKnightSounds.SLAM, 1.5f, 1.2f);
				run.endActive();
			}
			run.level().sendParticles(ParticleTypes.FLAME, boss.getX(), boss.getY() + 0.3, boss.getZ(), 4, 0.5, 0.2, 0.5, 0.02);
			for (LivingEntity victim : Hitboxes.inBox(boss, boss.getBoundingBox().inflate(0.6, 0, 0.6).expandTowards(dir.scale(speed)))) {
				if (run.hurt(victim)) {
					victim.knockback(config().chargeKnockback, -dir.x, -dir.z, run.level().damageSources().mobAttack(boss), 0);
					victim.needsSync = true;
				}
			}
		}

		@Override
		public void end(AttackRun run) {
			BossEntity boss = run.boss();
			boss.setDeltaMovement(0, boss.getDeltaMovement().y, 0);
		}
	}

	/** Fire pillars (phase 2): a circle under every player plus a few strays; they erupt after 1.5 s. */
	static final class Pillars extends Attack {
		Pillars() {
			super("pillars", config().pillars);
		}

		@Override
		public void start(AttackRun run) {
			run.sound(FurnaceKnightSounds.ROAR, 2.5f, 1.2f);
			BossEntity boss = run.boss();
			List<LivingEntity> targets = new ArrayList<>();
			for (ServerPlayer player : boss.participants()) {
				if (boss.inFight(player) && !player.isCreative()) {
					targets.add(player);
				}
			}
			if (targets.isEmpty() && run.target() != null) {
				targets.add(run.target());
			}
			int count = config().pillarsMin + run.random().nextInt(Math.max(1, config().pillarsMax - config().pillarsMin + 1));
			for (LivingEntity target : targets) {
				if (run.points().size() < count) {
					run.points().add(ground(run.level(), target.position()));
				}
			}
			int guard = 0;
			while (run.points().size() < count && !targets.isEmpty() && guard++ < 20) {
				LivingEntity near = targets.get(run.random().nextInt(targets.size()));
				double a = run.random().nextDouble() * Math.PI * 2;
				double r = 2 + run.random().nextDouble() * (config().pillarScatter - 2);
				run.points().add(ground(run.level(), near.position().add(Math.cos(a) * r, 0, Math.sin(a) * r)));
			}
		}

		@Override
		public void windup(AttackRun run) {
			if (run.tick() % 3 == 0) {
				for (Vec3 point : run.points()) {
					Telegraphs.circleFilling(run.level(), OUTLINE, FILL, point, config().pillarRadius, run.progress());
				}
			}
		}

		@Override
		public void active(AttackRun run) {
			if (run.first()) {
				for (Vec3 point : run.points()) {
					run.sound(FurnaceKnightSounds.PILLAR, point, 2.0f, 0.8f + run.random().nextFloat() * 0.3f);
				}
			}
			for (Vec3 point : run.points()) {
				if (run.tick() % 2 == 0) {
					Telegraphs.column(run.level(), ParticleTypes.FLAME, point, config().pillarHeight, config().pillarRadius * 0.4, 3);
					run.level().sendParticles(ParticleTypes.LAVA, true, true, point.x, point.y + 0.3, point.z, 2, 0.4, 0.1, 0.4, 0);
				}
				for (LivingEntity victim : Hitboxes.inCircle(run.boss(), point, config().pillarRadius, config().pillarHeight)) {
					if (run.hurt(victim)) {
						victim.setRemainingFireTicks(Math.max(victim.getRemainingFireTicks(), config().pillarFireTicks));
						victim.push(0, 0.5, 0);
						victim.needsSync = true;
					}
				}
			}
		}

		/** The floor under {@code pos}: searches a few blocks down (or up out of a block) for a standable surface. */
		private static Vec3 ground(ServerLevel level, Vec3 pos) {
			BlockPos start = BlockPos.containing(pos);
			for (int dy = 1; dy >= -6; dy--) {
				BlockPos at = start.offset(0, dy, 0);
				if (level.getBlockState(at.below()).isFaceSturdy(level, at.below(), Direction.UP) && level.getBlockState(at).getCollisionShape(level, at).isEmpty()) {
					return new Vec3(pos.x, at.getY(), pos.z);
				}
			}
			return pos;
		}
	}
}
