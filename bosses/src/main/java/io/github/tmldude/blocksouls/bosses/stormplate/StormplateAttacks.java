package io.github.tmldude.blocksouls.bosses.stormplate;

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
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import io.github.tmldude.blocksouls.bosses.api.Attack;
import io.github.tmldude.blocksouls.bosses.api.AttackRun;
import io.github.tmldude.blocksouls.bosses.api.BossEntity;
import io.github.tmldude.blocksouls.bosses.api.Hitboxes;
import io.github.tmldude.blocksouls.bosses.api.Telegraphs;

/** The Stormplate Knight's moves. Numbers come from {@link StormplateConfig}. */
final class StormplateAttacks {
	/** Storm-blue telegraph outline. */
	static final ParticleOptions OUTLINE = new DustParticleOptions(0x3E6AA8, 1.6f);
	/** Pale gold fill as the windup runs out. */
	static final ParticleOptions FILL = new DustParticleOptions(0xFFF1B0, 1.2f);
	/** The spear's gold, for the thrown spear and its trail. */
	static final ParticleOptions GOLD = new DustParticleOptions(0xD9B44A, 1.0f);

	private StormplateAttacks() {
	}

	private static StormplateConfig config() {
		return StormplateKnight.config();
	}

	/** Feet level in front of the knight, where ground telegraphs start. */
	private static Vec3 front(AttackRun run, double ahead) {
		return run.boss().position().add(run.direction().scale(ahead));
	}

	/** Spear thrust: a long, narrow lunge. Step to the side. */
	static final class Thrust extends Attack {
		Thrust() {
			super("thrust", config().thrust);
		}

		@Override
		public void start(AttackRun run) {
			run.sound(StormplateSounds.WINDUP, 1.4f, 1.3f);
		}

		@Override
		public void windup(AttackRun run) {
			if (run.tick() % 3 == 0) {
				Telegraphs.lineFilling(run.level(), OUTLINE, FILL, front(run, 0.6), run.direction(), config().thrustLength, config().thrustHalfWidth,
						run.progress());
			}
			if (run.progress() > 0.6f) {
				// The spearhead crackles just before it goes.
				Vec3 tip = run.boss().position().add(0, 2.6, 0).add(run.direction().scale(1.6));
				run.level().sendParticles(ParticleTypes.ELECTRIC_SPARK, tip.x, tip.y, tip.z, 2, 0.1, 0.1, 0.1, 0.05);
			}
		}

		@Override
		public void active(AttackRun run) {
			ServerLevel level = run.level();
			Vec3 start = front(run, 0.6);
			if (run.first()) {
				run.sound(StormplateSounds.THRUST, 2.0f, 1.0f);
				BossEntity boss = run.boss();
				boss.setDeltaMovement(run.direction().x * 0.45, boss.getDeltaMovement().y, run.direction().z * 0.45);
				for (double d = 0; d <= config().thrustLength; d += 0.5) {
					Vec3 p = start.add(run.direction().scale(d));
					level.sendParticles(ParticleTypes.ELECTRIC_SPARK, true, true, p.x, p.y + 1.4, p.z, 2, 0.1, 0.1, 0.1, 0.05);
					level.sendParticles(GOLD, true, true, p.x, p.y + 1.4, p.z, 1, 0.05, 0.05, 0.05, 0);
				}
			}
			for (LivingEntity victim : Hitboxes.inLine(run.boss(), start, run.direction(), config().thrustLength, config().thrustHalfWidth, 2.6)) {
				if (run.hurt(victim)) {
					victim.push(run.direction().x * 0.5, 0.2, run.direction().z * 0.5);
					victim.needsSync = true;
				}
			}
		}
	}

	/** Spear sweep: the shaft swings low and wide. Jump it or roll through. */
	static final class Sweep extends Attack {
		Sweep() {
			super("sweep", config().sweep);
		}

		@Override
		public void start(AttackRun run) {
			run.sound(StormplateSounds.WINDUP, 1.2f, 0.9f);
		}

		@Override
		public void windup(AttackRun run) {
			if (run.tick() % 3 == 0) {
				Telegraphs.arc(run.level(), OUTLINE, run.boss().position(), run.direction(), config().sweepRadius, config().sweepAngle);
				if (run.progress() > 0.4f) {
					Telegraphs.arc(run.level(), FILL, run.boss().position(), run.direction(), config().sweepRadius * run.progress(), config().sweepAngle);
				}
			}
		}

		@Override
		public void active(AttackRun run) {
			if (!run.first()) {
				return;
			}
			run.sound(StormplateSounds.SWING, 2.0f, 0.8f);
			Vec3 origin = run.boss().position();
			double half = Math.toRadians(config().sweepAngle / 2);
			double base = Math.atan2(run.direction().z, run.direction().x);
			for (int i = 0; i <= 20; i++) {
				double a = base - half + 2 * half * i / 20;
				double r = config().sweepRadius * 0.85;
				double x = origin.x + Math.cos(a) * r;
				double z = origin.z + Math.sin(a) * r;
				run.level().sendParticles(ParticleTypes.SWEEP_ATTACK, true, true, x, origin.y + 0.6, z, 1, 0, 0, 0, 0);
				run.level().sendParticles(ParticleTypes.ELECTRIC_SPARK, true, true, x, origin.y + 0.6, z, 2, 0.2, 0.1, 0.2, 0.05);
			}
			for (LivingEntity victim : Hitboxes.inArc(run.boss(), origin, run.direction(), config().sweepRadius, config().sweepAngle, config().sweepHeight)) {
				run.hurt(victim);
			}
		}
	}

	/**
	 * Thunder call: raises the spear to the sky; a circle opens under every player (two in phase 2, the second where
	 * they're heading), then lightning falls on each. Lightning damage, but no fire and no block damage. In phase 2 each
	 * strike leaves a charged puddle.
	 */
	static final class ThunderCall extends Attack {
		ThunderCall() {
			super("thunder", config().thunder);
		}

		@Override
		public double trackFraction() {
			return 0.3;
		}

		@Override
		public void start(AttackRun run) {
			run.sound(StormplateSounds.CHARGE, 2.5f, 0.8f);
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
			int extra = boss.phase() >= 1 ? config().thunderExtraPerPlayerPhase2 : 0;
			for (LivingEntity target : targets) {
				run.points().add(ground(run.level(), target.position()));
				for (int i = 0; i < extra; i++) {
					// Where they're heading, give or take: running in a straight line doesn't save you.
					Vec3 motion = target.getDeltaMovement();
					Vec3 ahead = new Vec3(motion.x, 0, motion.z).scale(config().thunder.windupTicks * 0.6);
					double a = run.random().nextDouble() * Math.PI * 2;
					double r = 2.5 + run.random().nextDouble() * 1.5;
					Vec3 spot = target.position().add(ahead).add(Math.cos(a) * r, 0, Math.sin(a) * r);
					run.points().add(ground(run.level(), spot));
				}
			}
		}

		@Override
		public void windup(AttackRun run) {
			if (run.tick() % 2 == 0) {
				for (Vec3 point : run.points()) {
					Telegraphs.circleFilling(run.level(), OUTLINE, FILL, point, config().thunderRadius, run.progress());
				}
			}
			if (run.progress() > 0.6f && run.tick() % 3 == 0) {
				for (Vec3 point : run.points()) {
					// Static rises out of the circle as the strike builds.
					run.level().sendParticles(ParticleTypes.ELECTRIC_SPARK, true, true, point.x, point.y + 0.3, point.z, 4,
							config().thunderRadius * 0.4, 0.2, config().thunderRadius * 0.4, 0.15);
				}
			}
			// The spear tip, held high, gathers the storm.
			Vec3 tip = run.boss().position().add(0, 5.0, 0);
			run.level().sendParticles(ParticleTypes.ELECTRIC_SPARK, tip.x, tip.y, tip.z, 2, 0.15, 0.3, 0.15, 0.1);
		}

		@Override
		public void active(AttackRun run) {
			ServerLevel level = run.level();
			if (run.first()) {
				run.sound(StormplateSounds.THUNDER, 3.0f, 1.0f);
				for (Vec3 point : run.points()) {
					StormplateKnight.flash(level, point);
					run.sound(StormplateSounds.THUNDER, point, 3.0f, 0.9f + run.random().nextFloat() * 0.2f);
					level.sendParticles(ParticleTypes.END_ROD, true, true, point.x, point.y + 0.5, point.z, 12, 0.3, 0.6, 0.3, 0.15);
					Telegraphs.column(level, ParticleTypes.ELECTRIC_SPARK, point, 5, 0.3, 4);
					if (run.boss() instanceof StormplateKnight knight && knight.phase() >= 1) {
						knight.puddles().add(point);
					}
				}
			}
			float damage = (float) run.scaled(run.attack().stats().damage);
			for (Vec3 point : run.points()) {
				for (LivingEntity victim : Hitboxes.inCircle(run.boss(), point, config().thunderRadius, 4)) {
					// Once per call even where circles overlap. Lightning damage (the dragonscale set shrugs it off), no fire.
					if (run.hitSoFar().add(victim)) {
						victim.hurtServer(level, level.damageSources().lightningBolt(), damage);
					}
				}
			}
		}
	}

	/**
	 * Spear throw: hurls the spear down a telegraphed line (stopping at a wall), lets it crackle there for a moment,
	 * then calls it back. It hits on the way out and again on the way back.
	 */
	static final class SpearThrow extends Attack {
		SpearThrow() {
			super("throw", config().spearThrow);
		}

		@Override
		public void start(AttackRun run) {
			run.sound(StormplateSounds.WINDUP, 1.5f, 1.1f);
		}

		/** Where the spear stops: the full throw length, or the first wall. */
		private static Vec3 reach(AttackRun run) {
			Vec3 from = run.boss().position().add(0, 1.4, 0);
			Vec3 to = from.add(run.direction().scale(config().throwLength));
			var hit = run.level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, run.boss()));
			Vec3 end = hit.getType() == HitResult.Type.MISS ? to : hit.getLocation().subtract(run.direction().scale(0.4));
			return new Vec3(end.x, run.boss().getY(), end.z);
		}

		@Override
		public void windup(AttackRun run) {
			if (run.tick() % 3 == 0) {
				Vec3 start = run.boss().position();
				double length = reach(run).subtract(start).horizontalDistance();
				Telegraphs.lineFilling(run.level(), OUTLINE, FILL, start, run.direction(), length, config().throwHalfWidth, run.progress());
			}
		}

		@Override
		public void active(AttackRun run) {
			ServerLevel level = run.level();
			if (run.first()) {
				run.points().clear();
				run.points().add(run.boss().position());
				run.points().add(reach(run));
				run.sound(StormplateSounds.THROW, 2.0f, 0.9f);
			}
			Vec3 from = run.points().get(0);
			Vec3 to = run.points().get(1);
			int flight = Math.max(1, Math.min(config().throwFlightTicks, run.stageLength() / 2));
			int t = run.tick();
			int back = run.stageLength() - flight;
			boolean returning = t >= back;
			Vec3 before = spear(from, to, t, flight, back);
			Vec3 now = spear(from, to, t + 1, flight, back);
			if (t + 1 == flight) {
				run.sound(StormplateSounds.THUNDER, to, 1.5f, 1.6f);
				level.sendParticles(ParticleTypes.ELECTRIC_SPARK, true, true, to.x, to.y + 1, to.z, 25, 0.4, 0.6, 0.4, 0.25);
			}
			if (t == back) {
				run.sound(StormplateSounds.THROW, to, 2.0f, 1.3f);
			}
			// The spear itself: a gold shaft with sparks streaming off it.
			Vec3 dir = run.direction().scale(returning ? -1 : 1);
			for (double d = -1.4; d <= 1.4; d += 0.2) {
				Vec3 p = now.add(dir.scale(d));
				level.sendParticles(GOLD, true, true, p.x, p.y + 1.4, p.z, 1, 0, 0, 0, 0);
			}
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK, true, true, now.x, now.y + 1.4, now.z, 3, 0.2, 0.2, 0.2, 0.08);
			// Who the spear passed through this tick.
			Vec3 delta = now.subtract(before);
			double length = delta.horizontalDistance();
			Vec3 segDir = length < 1e-3 ? run.direction() : new Vec3(delta.x / length, 0, delta.z / length);
			for (LivingEntity victim : Hitboxes.inLine(run.boss(), before.subtract(segDir.scale(0.7)), segDir, length + 1.4, config().throwHalfWidth, 2.6)) {
				if (returning) {
					// One more hit on the way back, whether or not the throw hit them.
					run.hurtEvery(victim, run.attack().stats().damage, 1000);
				} else {
					run.hurt(victim);
				}
			}
			if (returning && t + 1 == run.stageLength()) {
				run.sound(StormplateSounds.THRUST, 1.5f, 1.5f);
			}
		}

		/** Spear position (feet level) after {@code t} active ticks: out over {@code flight}, hang, back from {@code back}. */
		private static Vec3 spear(Vec3 from, Vec3 to, int t, int flight, int back) {
			return from.lerp(to, StormMath.spearFraction(t, flight, back));
		}
	}

	/** Leap slam (phase 2): crouches, jumps onto a player's position, and lands with a shockwave. */
	static final class Leap extends Attack {
		Leap() {
			super("leap", config().leap);
		}

		@Override
		public boolean canUse(BossEntity boss, LivingEntity target) {
			return boss.onGround();
		}

		@Override
		public void start(AttackRun run) {
			run.sound(StormplateSounds.WINDUP, 1.8f, 0.7f);
			LivingEntity target = run.target();
			run.points().add(landing(run, target != null ? target.position() : front(run, 6)));
		}

		@Override
		public void windup(AttackRun run) {
			LivingEntity target = run.target();
			if (target != null && target.isAlive() && run.tick() < run.stageLength() * trackFraction()) {
				run.points().set(0, landing(run, target.position()));
			}
			if (run.tick() % 2 == 0) {
				Telegraphs.circleFilling(run.level(), OUTLINE, FILL, run.points().get(0), config().leapRadius, run.progress());
			}
		}

		/** Ground under {@code pos}, kept a little inside the fog wall. */
		private static Vec3 landing(AttackRun run, Vec3 pos) {
			BossEntity boss = run.boss();
			BlockPos home = boss.home();
			double cx = home.getX() + 0.5;
			double cz = home.getZ() + 0.5;
			double dx = pos.x - cx;
			double dz = pos.z - cz;
			double max = Math.max(2, boss.arenaRadius() - 2.5);
			double dist = Math.sqrt(dx * dx + dz * dz);
			if (dist > max) {
				pos = new Vec3(cx + dx / dist * max, pos.y, cz + dz / dist * max);
			}
			return ground(run.level(), pos);
		}

		@Override
		public void active(AttackRun run) {
			BossEntity boss = run.boss();
			Vec3 land = run.points().get(0);
			if (run.first()) {
				run.points().add(boss.position());
				boss.setNoGravity(true);
				run.sound(StormplateSounds.THROW, 2.0f, 0.6f);
				run.level().sendParticles(ParticleTypes.CLOUD, boss.getX(), boss.getY() + 0.2, boss.getZ(), 15, 0.8, 0.1, 0.8, 0.05);
			}
			Vec3 from = run.points().get(1);
			int length = run.stageLength();
			double f = Math.min(1, (run.tick() + 1) / (double) length);
			Vec3 at = from.lerp(land, f).add(0, 4 * config().leapHeight * f * (1 - f), 0);
			Vec3 dir = land.subtract(from);
			if (dir.horizontalDistanceSqr() > 1e-4) {
				boss.face(dir);
			}
			boss.setDeltaMovement(Vec3.ZERO);
			boss.setPos(at.x, at.y, at.z);
			if (run.tick() % 2 == 0) {
				Telegraphs.circle(run.level(), FILL, land, config().leapRadius);
			}
			if (run.tick() + 1 >= length) {
				shockwave(run, land);
			}
		}

		private static void shockwave(AttackRun run, Vec3 land) {
			ServerLevel level = run.level();
			run.sound(StormplateSounds.SLAM, land, 3.0f, 0.8f);
			level.sendParticles(ParticleTypes.EXPLOSION, true, true, land.x, land.y + 0.5, land.z, 2, 0.5, 0.2, 0.5, 0);
			for (int ring = 1; ring <= 4; ring++) {
				double r = config().leapRadius * ring / 4;
				int n = (int) (r * 8);
				for (int i = 0; i < n; i++) {
					double a = i * Math.PI * 2 / n;
					level.sendParticles(ParticleTypes.ELECTRIC_SPARK, true, true, land.x + Math.cos(a) * r, land.y + 0.2, land.z + Math.sin(a) * r, 1, 0,
							0.1, 0, 0.1);
				}
			}
			level.sendParticles(ParticleTypes.CLOUD, true, true, land.x, land.y + 0.2, land.z, 30, config().leapRadius * 0.5, 0.1,
					config().leapRadius * 0.5, 0.08);
			for (LivingEntity victim : Hitboxes.inCircle(run.boss(), land, config().leapRadius, 2.5)) {
				if (run.hurt(victim)) {
					Vec3 away = victim.position().subtract(land);
					if (away.horizontalDistanceSqr() < 1e-4) {
						away = run.direction();
					}
					victim.knockback(config().leapKnockback, -away.x, -away.z, level.damageSources().mobAttack(run.boss()), 0);
					victim.push(0, 0.4, 0);
					victim.needsSync = true;
				}
			}
		}

		@Override
		public void end(AttackRun run) {
			BossEntity boss = run.boss();
			boss.setNoGravity(false);
			boss.setDeltaMovement(0, boss.getDeltaMovement().y, 0);
		}
	}

	/** The floor under {@code pos}: searches a few blocks down (or up out of a block) for a standable surface. */
	static Vec3 ground(ServerLevel level, Vec3 pos) {
		BlockPos start = BlockPos.containing(pos);
		for (int dy = 2; dy >= -8; dy--) {
			BlockPos at = start.offset(0, dy, 0);
			if (level.getBlockState(at.below()).isFaceSturdy(level, at.below(), Direction.UP) && level.getBlockState(at).getCollisionShape(level, at).isEmpty()) {
				return new Vec3(pos.x, at.getY(), pos.z);
			}
		}
		return pos;
	}
}
