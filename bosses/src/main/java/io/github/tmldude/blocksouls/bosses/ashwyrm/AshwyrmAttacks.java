package io.github.tmldude.blocksouls.bosses.ashwyrm;

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
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import io.github.tmldude.blocksouls.bosses.api.Attack;
import io.github.tmldude.blocksouls.bosses.api.AttackRun;
import io.github.tmldude.blocksouls.bosses.api.BossEntity;
import io.github.tmldude.blocksouls.bosses.api.Hitboxes;
import io.github.tmldude.blocksouls.bosses.api.Telegraphs;

/** The Ashwyrm's moves. Numbers come from {@link AshwyrmConfig}. */
final class AshwyrmAttacks {
	/** Ember telegraph outline. */
	static final ParticleOptions OUTLINE = new DustParticleOptions(0xFF5A1A, 1.6f);
	/** Brighter fill as the windup runs out. */
	static final ParticleOptions FILL = new DustParticleOptions(0xFFC25A, 1.2f);
	/** Ash-grey outline for the wind (buffet) ring. */
	static final ParticleOptions ASH_OUTLINE = new DustParticleOptions(0xB8B0AA, 1.6f);

	private AshwyrmAttacks() {
	}

	private static AshwyrmConfig config() {
		return Ashwyrm.config();
	}

	private static Ashwyrm wyrm(AttackRun run) {
		return (Ashwyrm) run.boss();
	}

	/** Feet level in front of the boss. */
	private static Vec3 front(AttackRun run, double ahead) {
		return run.boss().position().add(run.direction().scale(ahead));
	}

	private static void pushAway(LivingEntity victim, Vec3 from, double horizontal, double up) {
		Vec3 away = new Vec3(victim.getX() - from.x, 0, victim.getZ() - from.z);
		away = away.lengthSqr() < 1e-4 ? new Vec3(1, 0, 0) : away.normalize();
		victim.push(away.x * horizontal, up, away.z * horizontal);
		victim.needsSync = true;
	}

	/** Sets {@code victim} burning for at least {@code ticks}. */
	private static void ignite(LivingEntity victim, int ticks) {
		victim.setRemainingFireTicks(Math.max(victim.getRemainingFireTicks(), ticks));
	}

	/** With {@code griefing} on, a real fire block where something burning landed. Off by default: no block is touched. */
	private static void fireBlock(ServerLevel level, Vec3 at) {
		if (!config().griefing) {
			return;
		}
		BlockPos pos = BlockPos.containing(at);
		if (level.getBlockState(pos).isAir() && BaseFireBlock.canBePlacedAt(level, pos, Direction.UP)) {
			level.setBlockAndUpdate(pos, BaseFireBlock.getState(level, pos));
		}
	}

	/** The floor under {@code pos}: searches down (or up out of a block) for a standable surface. */
	static Vec3 ground(ServerLevel level, Vec3 pos) {
		BlockPos start = BlockPos.containing(pos);
		for (int dy = 0; dy >= -24; dy--) {
			if (standable(level, start.offset(0, dy, 0))) {
				return new Vec3(pos.x, start.getY() + dy, pos.z);
			}
		}
		for (int dy = 1; dy <= 6; dy++) {
			if (standable(level, start.offset(0, dy, 0))) {
				return new Vec3(pos.x, start.getY() + dy, pos.z);
			}
		}
		int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, start.getX(), start.getZ());
		return new Vec3(pos.x, top, pos.z);
	}

	private static boolean standable(ServerLevel level, BlockPos at) {
		return level.getBlockState(at.below()).isFaceSturdy(level, at.below(), Direction.UP) && level.getBlockState(at).getCollisionShape(level, at).isEmpty();
	}

	private static Vec3 lerp(Vec3 a, Vec3 b, double t) {
		return a.add(b.subtract(a).scale(Math.clamp(t, 0, 1)));
	}

	private static Vec3 rotate(Vec3 dir, double radians) {
		double cos = Math.cos(radians);
		double sin = Math.sin(radians);
		return new Vec3(dir.x * cos - dir.z * sin, 0, dir.x * sin + dir.z * cos);
	}

	private static void fireball(ServerLevel level, Vec3 p) {
		level.sendParticles(ParticleTypes.FLAME, true, true, p.x, p.y, p.z, 8, 0.3, 0.3, 0.3, 0.02);
		level.sendParticles(ParticleTypes.LARGE_SMOKE, true, true, p.x, p.y, p.z, 2, 0.2, 0.2, 0.2, 0.01);
		level.sendParticles(ParticleTypes.LAVA, true, true, p.x, p.y, p.z, 1, 0.2, 0.2, 0.2, 0);
	}

	private static void burst(ServerLevel level, Vec3 p, double radius) {
		level.sendParticles(ParticleTypes.EXPLOSION, true, true, p.x, p.y + 0.5, p.z, 2, radius * 0.3, 0.2, radius * 0.3, 0);
		level.sendParticles(ParticleTypes.FLAME, true, true, p.x, p.y + 0.3, p.z, 30, radius * 0.5, 0.3, radius * 0.5, 0.08);
		level.sendParticles(ParticleTypes.LAVA, true, true, p.x, p.y + 0.3, p.z, 6, radius * 0.4, 0.1, radius * 0.4, 0);
	}

	// ---------------------------------------------------------------------------------------------------------
	// Phase 1 and 3: ground

	/** Bite: rears its head back, then lunges with the jaws along a short strip in front. */
	static final class Bite extends Attack {
		Bite() {
			super("bite", config().bite);
		}

		@Override
		public void start(AttackRun run) {
			run.sound(AshwyrmSounds.BITE, 1.5f, 0.6f);
		}

		@Override
		public void windup(AttackRun run) {
			if (run.tick() % 2 == 0) {
				Telegraphs.lineFilling(run.level(), OUTLINE, FILL, front(run, 1.0), run.direction(), config().biteReach, config().biteHalfWidth,
						run.progress());
			}
		}

		@Override
		public void active(AttackRun run) {
			BossEntity boss = run.boss();
			if (run.first()) {
				run.sound(AshwyrmSounds.BITE, 2.5f, 0.9f);
				boss.setDeltaMovement(run.direction().x * 0.5, boss.getDeltaMovement().y, run.direction().z * 0.5);
			}
			for (LivingEntity victim : Hitboxes.inLine(boss, front(run, 1.0), run.direction(), config().biteReach, config().biteHalfWidth, 3.5)) {
				run.hurt(victim);
			}
		}

		@Override
		public void end(AttackRun run) {
			BossEntity boss = run.boss();
			boss.setDeltaMovement(0, boss.getDeltaMovement().y, 0);
		}
	}

	/** Tail sweep: coils, then spins once; the tail sweeps the full circle from behind. Low, so jump it or roll. */
	static final class TailSweep extends Attack {
		TailSweep() {
			super("tail_sweep", config().tailSweep);
		}

		@Override
		public void start(AttackRun run) {
			run.sound(AshwyrmSounds.ROAR, 1.5f, 1.3f);
		}

		@Override
		public void windup(AttackRun run) {
			if (run.tick() % 3 == 0) {
				Telegraphs.circleFilling(run.level(), OUTLINE, FILL, run.boss().position(), config().tailRadius, run.progress());
			}
		}

		@Override
		public void active(AttackRun run) {
			ServerLevel level = run.level();
			BossEntity boss = run.boss();
			Vec3 origin = boss.position();
			double radius = config().tailRadius;
			int ticks = Math.max(1, run.stageLength());
			double segment = 360.0 / ticks;
			if (run.first()) {
				run.sound(AshwyrmSounds.TAIL, 3.0f, 0.5f);
			}
			// The tail starts behind and goes round once; each tick covers the next slice (plus a little overlap).
			Vec3 dir = rotate(run.direction().scale(-1), Math.toRadians(-segment * (run.tick() + 0.5)));
			Vec3 tip = origin.add(dir.scale(radius * 0.85));
			level.sendParticles(ParticleTypes.SWEEP_ATTACK, true, true, tip.x, tip.y + 0.6, tip.z, 2, 0.6, 0.1, 0.6, 0);
			level.sendParticles(ParticleTypes.ASH, true, true, tip.x, tip.y + 0.4, tip.z, 20, 1.2, 0.2, 1.2, 0.02);
			for (LivingEntity victim : Hitboxes.inArc(boss, origin, dir, radius, segment + 20, config().tailHeight)) {
				if (run.hurt(victim)) {
					pushAway(victim, origin, 0.6, 0.3);
				}
			}
		}
	}

	/** Fire breath: rears up, then pours a cone of fire for 3 s that slowly turns after its target. */
	static final class Breath extends Attack {
		Breath() {
			super("breath", config().breath);
		}

		private static Vec3 coneOrigin(AttackRun run) {
			return run.boss().position().add(run.direction().scale(2.5));
		}

		private static Vec3 jaws(AttackRun run) {
			return run.boss().position().add(run.direction().scale(6.0)).add(0, 2.8, 0);
		}

		@Override
		public void start(AttackRun run) {
			run.sound(AshwyrmSounds.ROAR, 2.0f, 1.1f);
		}

		@Override
		public void windup(AttackRun run) {
			if (run.tick() % 3 == 0) {
				Telegraphs.arc(run.level(), OUTLINE, coneOrigin(run), run.direction(), config().breathLength, config().breathAngle);
			}
			if (run.progress() > 0.4f) {
				Vec3 jaws = jaws(run);
				run.level().sendParticles(ParticleTypes.SMALL_FLAME, true, true, jaws.x, jaws.y, jaws.z, 3, 0.25, 0.15, 0.25, 0.01);
			}
		}

		@Override
		public void active(AttackRun run) {
			ServerLevel level = run.level();
			AshwyrmConfig c = config();
			LivingEntity target = run.target();
			if (target != null && target.isAlive()) {
				run.setDirection(turnToward(run.direction(), target.position().subtract(run.boss().position()), c.breathTurnRate));
				run.boss().face(run.direction());
			}
			if (run.tick() % 6 == 0) {
				run.sound(AshwyrmSounds.BREATH, 2.5f, 0.6f + run.random().nextFloat() * 0.2f);
			}
			Vec3 jaws = jaws(run);
			for (int i = 0; i < 8; i++) {
				double spread = Math.toRadians(c.breathAngle / 2) * (run.random().nextDouble() * 2 - 1);
				Vec3 dir = rotate(run.direction(), spread);
				double speed = 0.45 + run.random().nextDouble() * 0.3;
				level.sendParticles(ParticleTypes.FLAME, true, true, jaws.x, jaws.y, jaws.z, 0, dir.x, -0.22, dir.z, speed);
			}
			if (run.tick() % 2 == 0) {
				level.sendParticles(ParticleTypes.LARGE_SMOKE, true, true, jaws.x, jaws.y + 0.3, jaws.z, 0, run.direction().x, 0.1, run.direction().z, 0.3);
			}
			for (LivingEntity victim : Hitboxes.inArc(run.boss(), coneOrigin(run), run.direction(), c.breathLength, c.breathAngle, 3.5)) {
				if (run.hurtEvery(victim, c.breath.damage, c.breathHitIntervalTicks)) {
					ignite(victim, c.breathFireTicks);
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
	}

	/** Wing buffet: raises both wings and brings them down; a gust ring hurts and throws everyone near back. */
	static final class WingBuffet extends Attack {
		WingBuffet() {
			super("wing_buffet", config().wingBuffet);
		}

		@Override
		public void start(AttackRun run) {
			run.sound(AshwyrmSounds.FLAP, 3.0f, 0.5f);
		}

		@Override
		public void windup(AttackRun run) {
			if (run.tick() % 3 == 0) {
				Telegraphs.circleFilling(run.level(), ASH_OUTLINE, FILL, run.boss().position(), config().buffetRadius, run.progress());
			}
		}

		@Override
		public void active(AttackRun run) {
			if (!run.first()) {
				return;
			}
			ServerLevel level = run.level();
			BossEntity boss = run.boss();
			Vec3 origin = boss.position();
			double radius = config().buffetRadius;
			run.sound(AshwyrmSounds.FLAP, 4.0f, 0.4f);
			for (int i = 0; i < 32; i++) {
				double a = i * Math.PI * 2 / 32;
				Vec3 dir = new Vec3(Math.cos(a), 0, Math.sin(a));
				Vec3 p = origin.add(dir.scale(radius * 0.6));
				level.sendParticles(ParticleTypes.CLOUD, true, true, p.x, p.y + 0.4, p.z, 0, dir.x, 0.05, dir.z, 0.6);
				level.sendParticles(ParticleTypes.ASH, true, true, p.x, p.y + 0.6, p.z, 4, 0.4, 0.3, 0.4, 0.02);
			}
			for (LivingEntity victim : Hitboxes.inCircle(boss, origin, radius, 4)) {
				if (run.hurt(victim)) {
					pushAway(victim, origin, config().buffetKnockback, 0.5);
				}
			}
		}
	}

	// ---------------------------------------------------------------------------------------------------------
	// Phase 2: air

	/**
	 * Fireball run: picks a line through its target, telegraphs it (a strip plus a circle at each impact), flies to its
	 * start and along it, dropping one fireball per circle. Each impact leaves burning ground for a few seconds. Points:
	 * 0 = flight start, 1 = flight end, 2.. = impacts, then each fireball's launch point as it fires.
	 */
	static final class FireballRun extends Attack {
		FireballRun() {
			super("fireball_run", config().fireballRun);
		}

		@Override
		public boolean canUse(BossEntity boss, LivingEntity target) {
			return boss instanceof Ashwyrm wyrm && wyrm.isFlying() && !wyrm.wantsToLand()
					&& boss.getY() >= boss.home().getY() + config().flightHeight * 0.5;
		}

		@Override
		public double trackFraction() {
			return 0;
		}

		private static int count() {
			return Math.max(1, config().fireballs);
		}

		/** Tick in the active stage when fireball {@code i} leaves the jaws. */
		private static int launchTick(AttackRun run, int i) {
			int flight = config().fireballFlightTicks;
			int gap = count() > 1 ? Math.max(1, (run.attack().stats().activeTicks - flight - 2) / (count() - 1)) : 0;
			return i * gap;
		}

		@Override
		public void start(AttackRun run) {
			AshwyrmConfig c = config();
			ServerLevel level = run.level();
			Vec3 dir = run.direction();
			LivingEntity target = run.target();
			Vec3 aim = target != null ? target.position() : run.boss().position().add(dir.scale(10));
			Vec3 center = ground(level, aim);
			int n = count();
			double first = -c.fireballSpacing * (n - 1) / 2;
			double half = -first + 5;
			double altitude = Math.max(center.y, run.boss().home().getY()) + c.flightHeight * 0.75;
			Vec3 start = center.subtract(dir.scale(half));
			Vec3 end = center.add(dir.scale(half));
			run.points().add(new Vec3(start.x, altitude, start.z));
			run.points().add(new Vec3(end.x, altitude, end.z));
			for (int i = 0; i < n; i++) {
				run.points().add(ground(level, center.add(dir.scale(first + c.fireballSpacing * i))));
			}
			run.sound(AshwyrmSounds.ROAR, 4.0f, 1.2f);
		}

		private static Vec3 impact(AttackRun run, int i) {
			return run.points().get(2 + i);
		}

		@Override
		public void windup(AttackRun run) {
			Ashwyrm wyrm = wyrm(run);
			Vec3 start = run.points().get(0);
			int remaining = Math.max(1, run.stageLength() - run.tick());
			wyrm.flyToward(start, Math.min(1.6, Math.max(config().flySpeed, wyrm.position().distanceTo(start) / remaining)));
			if (run.tick() % 3 == 0) {
				int n = count();
				Vec3 a = impact(run, 0).subtract(run.direction().scale(2));
				double length = config().fireballSpacing * (n - 1) + 4;
				Telegraphs.line(run.level(), OUTLINE, a, run.direction(), length, config().fireballRadius * 0.4);
				for (int i = 0; i < n; i++) {
					Telegraphs.circleFilling(run.level(), OUTLINE, FILL, impact(run, i), config().fireballRadius, run.progress());
				}
			}
			if (run.tick() % 14 == 0) {
				run.sound(AshwyrmSounds.FLAP, 3.0f, 0.8f);
			}
		}

		@Override
		public void active(AttackRun run) {
			AshwyrmConfig c = config();
			ServerLevel level = run.level();
			Ashwyrm wyrm = wyrm(run);
			int ticks = Math.max(1, run.stageLength());
			wyrm.flyToward(lerp(run.points().get(0), run.points().get(1), (run.tick() + 1.0) / ticks), 2.0);
			int n = count();
			for (int i = 0; i < n; i++) {
				int launch = launchTick(run, i);
				int since = run.tick() - launch;
				if (since == 0) {
					run.points().add(wyrm.mouth());
					run.sound(AshwyrmSounds.FIREBALL, 3.0f, 0.7f);
				}
				if (since < 0 || 2 + n + i >= run.points().size()) {
					continue;
				}
				Vec3 from = run.points().get(2 + n + i);
				Vec3 to = impact(run, i);
				if (since < c.fireballFlightTicks) {
					fireball(level, lerp(from, to, (since + 1.0) / c.fireballFlightTicks));
				} else if (since == c.fireballFlightTicks) {
					land(run, to);
				}
			}
			burning(run);
		}

		private static void land(AttackRun run, Vec3 at) {
			AshwyrmConfig c = config();
			burst(run.level(), at, c.fireballRadius);
			run.sound(AshwyrmSounds.IMPACT, at, 3.0f, 0.8f);
			for (LivingEntity victim : Hitboxes.inCircle(run.boss(), at, c.fireballRadius, 3)) {
				if (run.hurt(victim)) {
					ignite(victim, c.fireballFireTicks);
					pushAway(victim, at, 0.4, 0.35);
				}
			}
			fireBlock(run.level(), at);
		}

		@Override
		public void recovery(AttackRun run) {
			Ashwyrm wyrm = wyrm(run);
			Vec3 end = run.points().get(1);
			wyrm.flyToward(end.add(run.direction().scale(8)).add(0, 3, 0), config().flySpeed);
			burning(run);
		}

		/** Burning ground at each landed impact, until {@code fireTicks} after it hit. */
		private static void burning(AttackRun run) {
			AshwyrmConfig c = config();
			ServerLevel level = run.level();
			int windup = Math.max(1, run.attack().stats().windupTicks);
			for (int i = 0; i < count(); i++) {
				int hitAt = windup + launchTick(run, i) + c.fireballFlightTicks;
				int age = run.totalTicks() - hitAt;
				if (age <= 0 || age > c.fireTicks) {
					continue;
				}
				Vec3 at = impact(run, i);
				if (run.tick() % 3 == 0) {
					level.sendParticles(ParticleTypes.FLAME, true, true, at.x, at.y + 0.2, at.z, 6, c.fireballRadius * 0.45, 0.1, c.fireballRadius * 0.45, 0.01);
					level.sendParticles(ParticleTypes.SMOKE, true, true, at.x, at.y + 0.5, at.z, 2, c.fireballRadius * 0.4, 0.2, c.fireballRadius * 0.4, 0.01);
				}
				for (LivingEntity victim : Hitboxes.inCircle(run.boss(), at, c.fireballRadius * 0.8, 2)) {
					if (run.hurtEvery(victim, c.fireDamage, c.fireHitIntervalTicks)) {
						ignite(victim, c.fireballFireTicks);
					}
				}
			}
		}
	}

	/**
	 * Landing (phase 2, every {@code landIntervalTicks} in the air): glides down onto a telegraphed circle near its
	 * target, shockwave on impact, then lies stunned for the recovery (the vulnerability window).
	 */
	static final class Land extends Attack {
		Land() {
			super("land", config().land);
		}

		@Override
		public boolean canUse(BossEntity boss, LivingEntity target) {
			return boss instanceof Ashwyrm wyrm && wyrm.wantsToLand();
		}

		@Override
		public double trackFraction() {
			return 0;
		}

		@Override
		public void start(AttackRun run) {
			BossEntity boss = run.boss();
			LivingEntity target = run.target();
			Vec3 home = Vec3.atBottomCenterOf(boss.home());
			Vec3 spot = home;
			if (target != null) {
				Vec3 toBoss = new Vec3(boss.getX() - target.getX(), 0, boss.getZ() - target.getZ());
				spot = target.position().add(toBoss.lengthSqr() < 1e-4 ? Vec3.ZERO : toBoss.normalize().scale(6));
			}
			// Stay well inside the arena.
			Vec3 fromHome = new Vec3(spot.x - home.x, 0, spot.z - home.z);
			double max = Math.max(0, boss.arenaRadius() - 4);
			if (fromHome.length() > max) {
				spot = home.add(fromHome.normalize().scale(max));
			}
			run.points().add(ground(run.level(), new Vec3(spot.x, Math.max(spot.y, home.y), spot.z)));
			run.sound(AshwyrmSounds.ROAR, 4.0f, 0.9f);
		}

		@Override
		public void windup(AttackRun run) {
			Ashwyrm wyrm = wyrm(run);
			Vec3 spot = run.points().get(0);
			int remaining = Math.max(1, run.stageLength() - run.tick());
			if (wyrm.isFlying()) {
				wyrm.flyToward(spot, Math.min(1.6, Math.max(0.3, wyrm.position().distanceTo(spot) / remaining)));
			}
			if (run.tick() % 3 == 0) {
				Telegraphs.circleFilling(run.level(), OUTLINE, FILL, spot, config().landShockwaveRadius, run.progress());
			}
			if (run.tick() % 10 == 0) {
				run.sound(AshwyrmSounds.FLAP, 3.5f, 0.6f);
			}
		}

		@Override
		public void active(AttackRun run) {
			if (!run.first()) {
				return;
			}
			Ashwyrm wyrm = wyrm(run);
			ServerLevel level = run.level();
			wyrm.setFlying(false);
			wyrm.setDeltaMovement(0, -1.0, 0);
			wyrm.onLanded();
			Vec3 at = wyrm.position();
			run.sound(AshwyrmSounds.LAND, 4.0f, 0.7f);
			level.sendParticles(ParticleTypes.EXPLOSION, true, true, at.x, at.y + 0.5, at.z, 4, 2, 0.3, 2, 0);
			level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, true, true, at.x, at.y + 0.3, at.z, 50, 3, 0.2, 3, 0.04);
			for (LivingEntity victim : Hitboxes.inCircle(wyrm, at, config().landShockwaveRadius, 2.5)) {
				if (run.hurt(victim)) {
					pushAway(victim, at, 0.8, 0.4);
				}
			}
		}

		@Override
		public void recovery(AttackRun run) {
			Ashwyrm wyrm = wyrm(run);
			wyrm.setDeltaMovement(0, wyrm.getDeltaMovement().y, 0);
			if (run.first()) {
				run.sound(AshwyrmSounds.HURT, 3.0f, 0.5f);
			}
			if (run.tick() % 10 == 0) {
				Vec3 m = wyrm.mouth();
				run.level().sendParticles(ParticleTypes.LARGE_SMOKE, true, true, m.x, m.y - 1.5, m.z, 4, 0.4, 0.2, 0.4, 0.01);
			}
		}
	}

	// ---------------------------------------------------------------------------------------------------------
	// Phase 3: enraged

	/** Firestorm: a circle under every player (and a few strays); meteors fall into them as the windup runs out. */
	static final class Firestorm extends Attack {
		/** The meteors are visible for this many ticks before they hit. */
		private static final int FALL_TICKS = 15;

		Firestorm() {
			super("firestorm", config().firestorm);
		}

		@Override
		public void start(AttackRun run) {
			AshwyrmConfig c = config();
			BossEntity boss = run.boss();
			run.sound(AshwyrmSounds.ROAR, 4.0f, 1.0f);
			List<LivingEntity> targets = new ArrayList<>();
			for (ServerPlayer player : boss.participants()) {
				if (boss.inFight(player) && !player.isCreative()) {
					targets.add(player);
				}
			}
			if (targets.isEmpty() && run.target() != null) {
				targets.add(run.target());
			}
			for (LivingEntity target : targets) {
				run.points().add(ground(run.level(), target.position()));
			}
			for (int i = 0; i < c.meteorStrays && !targets.isEmpty(); i++) {
				LivingEntity near = targets.get(run.random().nextInt(targets.size()));
				double a = run.random().nextDouble() * Math.PI * 2;
				double r = 2 + run.random().nextDouble() * Math.max(0, c.meteorScatter - 2);
				run.points().add(ground(run.level(), near.position().add(Math.cos(a) * r, 0, Math.sin(a) * r)));
			}
		}

		/** Where meteor {@code i} falls from: high up and off to one side. */
		private static Vec3 sky(Vec3 point, int i) {
			double a = i * 2.4;
			return point.add(Math.cos(a) * 8, 26, Math.sin(a) * 8);
		}

		@Override
		public void windup(AttackRun run) {
			AshwyrmConfig c = config();
			List<Vec3> points = run.points();
			if (run.tick() % 3 == 0) {
				for (Vec3 point : points) {
					Telegraphs.circleFilling(run.level(), OUTLINE, FILL, point, c.meteorRadius, run.progress());
				}
			}
			int fallStart = run.stageLength() - FALL_TICKS;
			if (run.tick() == Math.max(0, fallStart)) {
				for (Vec3 point : points) {
					run.sound(AshwyrmSounds.METEOR, point, 3.0f, 0.6f);
				}
			}
			if (run.tick() >= fallStart) {
				double t = (run.tick() - fallStart + 1.0) / FALL_TICKS;
				for (int i = 0; i < points.size(); i++) {
					Vec3 p = lerp(sky(points.get(i), i), points.get(i), t);
					fireball(run.level(), p);
					run.level().sendParticles(ParticleTypes.FLAME, true, true, p.x, p.y, p.z, 6, 0.6, 0.6, 0.6, 0.03);
				}
			}
		}

		@Override
		public void active(AttackRun run) {
			AshwyrmConfig c = config();
			ServerLevel level = run.level();
			for (Vec3 point : run.points()) {
				if (run.first()) {
					burst(level, point, c.meteorRadius);
					run.sound(AshwyrmSounds.IMPACT, point, 3.0f, 0.6f);
					fireBlock(level, point);
				} else if (run.tick() % 2 == 0) {
					level.sendParticles(ParticleTypes.FLAME, true, true, point.x, point.y + 0.2, point.z, 4, c.meteorRadius * 0.4, 0.1, c.meteorRadius * 0.4, 0.01);
				}
				for (LivingEntity victim : Hitboxes.inCircle(run.boss(), point, c.meteorRadius, 3)) {
					if (run.hurt(victim)) {
						ignite(victim, c.meteorFireTicks);
						victim.push(0, 0.4, 0);
						victim.needsSync = true;
					}
				}
			}
		}
	}
}
