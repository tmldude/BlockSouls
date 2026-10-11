package io.github.tmldude.blocksouls.bosses.raid;

import java.util.ArrayList;
import java.util.List;
import java.util.function.DoubleSupplier;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.PowerParticleOption;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import io.github.tmldude.blocksouls.bosses.api.Attack;
import io.github.tmldude.blocksouls.bosses.api.AttackRun;
import io.github.tmldude.blocksouls.bosses.api.AttackStats;
import io.github.tmldude.blocksouls.bosses.api.BossEntity;
import io.github.tmldude.blocksouls.bosses.api.Hitboxes;
import io.github.tmldude.blocksouls.bosses.api.Telegraphs;

/**
 * The final dragon's moves. Telegraphs are violet outlines that fill white as the windup runs out; the dive's shadow
 * is dark. Flying moves only start while it flies, perched moves only on the perch. Numbers: {@link FinalRaidConfig}.
 */
final class FinalDragonAttacks {
	static final ParticleOptions OUTLINE = new DustParticleOptions(0x9B4DFF, 1.8f);
	static final ParticleOptions FILL = new DustParticleOptions(0xF2ECFF, 1.4f);
	static final ParticleOptions SHADOW = new DustParticleOptions(0x16081F, 2.6f);
	static final ParticleOptions VOID = new DustParticleOptions(0x6A2BC4, 2.0f);

	private FinalDragonAttacks() {
	}

	private static FinalRaidConfig config() {
		return FinalDragon.config();
	}

	private static FinalDragon dragon(AttackRun run) {
		return (FinalDragon) run.boss();
	}

	private static boolean flying(BossEntity boss) {
		return boss instanceof FinalDragon dragon && dragon.mode() == FinalDragon.Mode.FLYING;
	}

	private static boolean perched(BossEntity boss) {
		return boss instanceof FinalDragon dragon && dragon.mode() == FinalDragon.Mode.PERCHED;
	}

	/** Where {@code target} stands, or the island center without one. */
	private static Vec3 groundAt(AttackRun run, LivingEntity target) {
		return target != null ? target.position() : dragon(run).arena().centerVec();
	}

	/** Moves the dragon at exactly the speed that reaches {@code to} in {@code ticksLeft}. */
	private static void moveTo(FinalDragon dragon, Vec3 to, int ticksLeft) {
		dragon.setDeltaMovement(to.subtract(dragon.position()).scale(1.0 / Math.max(1, ticksLeft)));
	}

	/**
	 * Breath line (and the P3 breath sweep, a longer, wider one): a lane through the target is marked on the ground, the
	 * dragon swings around to its start, then flies low along it pouring void fire. Get out of the lane.
	 */
	static final class BreathLine extends Attack {
		private final DoubleSupplier length;
		private final DoubleSupplier halfWidth;

		BreathLine(String name, AttackStats stats, DoubleSupplier length, DoubleSupplier halfWidth) {
			super(name, stats);
			this.length = length;
			this.halfWidth = halfWidth;
		}

		@Override
		public boolean canUse(BossEntity boss, LivingEntity target) {
			return flying(boss);
		}

		@Override
		public double trackFraction() {
			return 0;
		}

		private double len(AttackRun run) {
			return dragon(run).arena().scaled(length.getAsDouble());
		}

		private Vec3 laneStart(AttackRun run) {
			return run.points().get(0);
		}

		@Override
		public void start(AttackRun run) {
			FinalDragon dragon = dragon(run);
			Vec3 center = groundAt(run, run.target());
			Vec3 dir = run.direction();
			if (dir.lengthSqr() < 1e-4) {
				dir = new Vec3(1, 0, 0);
			}
			run.points().add(center.subtract(dir.scale(len(run) / 2)));
			run.setDirection(dir);
			run.sound(FinalRaidSounds.BREATH, 4f, 0.6f);
			dragon.playSound(FinalRaidSounds.ROAR, 4f, 1.2f);
		}

		@Override
		public void windup(AttackRun run) {
			FinalDragon dragon = dragon(run);
			Vec3 start = laneStart(run);
			// Swing around to a point behind the lane's start, low.
			double k = dragon.arena().scale();
			Vec3 approach = start.subtract(run.direction().scale(6 * k)).add(0, 7 * k, 0);
			int left = run.stageLength() - run.tick();
			dragon.steer(approach, Math.max(1.0, approach.distanceTo(dragon.position()) / Math.max(1, left)), 0.35);
			dragon.faceVelocity();
			if (run.tick() % 3 == 0) {
				Telegraphs.lineFilling(run.level(), OUTLINE, FILL, start, run.direction(), len(run), halfWidth.getAsDouble(), run.progress());
			}
		}

		@Override
		public void active(AttackRun run) {
			FinalDragon dragon = dragon(run);
			Vec3 start = laneStart(run);
			Vec3 dir = run.direction();
			double len = len(run);
			double covered = len * run.progress();
			moveTo(dragon, start.add(dir.scale(covered)).add(0, dragon.arena().scaled(6), 0), 1);
			dragon.face(dir);
			dragon.setXRot(10);
			ServerLevel level = run.level();
			if (run.tick() % 6 == 0) {
				run.sound(FinalRaidSounds.BREATH, 3f, 0.7f + run.random().nextFloat() * 0.2f);
			}
			// Breath pours down under it and lingers along the lane behind it.
			Vec3 mouth = dragon.position().add(dir.scale(4)).add(0, 1.5, 0);
			level.sendParticles(PowerParticleOption.create(ParticleTypes.DRAGON_BREATH, 1f), true, true, mouth.x, mouth.y - 3, mouth.z, 20, 1.0,
					2.0, 1.0, 0.02);
			for (double d = 0; d <= covered; d += 1.5) {
				if (run.random().nextFloat() < 0.35f) {
					Vec3 p = start.add(dir.scale(d));
					level.sendParticles(VOID, true, true, p.x, p.y + 0.3, p.z, 1, halfWidth.getAsDouble() * 0.5, 0.2, halfWidth.getAsDouble() * 0.5, 0);
				}
			}
			breathDamage(run, covered);
		}

		@Override
		public void recovery(AttackRun run) {
			FinalDragon dragon = dragon(run);
			// The lane keeps burning a moment while it climbs away.
			breathDamage(run, len(run));
			dragon.steer(dragon.position().add(run.direction().scale(8)).add(0, 4, 0), 1.0, 0.25);
			dragon.faceVelocity();
		}

		private void breathDamage(AttackRun run, double covered) {
			Vec3 from = laneStart(run).add(0, -1.5, 0);
			for (LivingEntity victim : Hitboxes.inLine(run.boss(), from, run.direction(), covered, halfWidth.getAsDouble(), 4.5)) {
				run.hurtEvery(victim, stats().damage, config().breathHitIntervalTicks);
			}
		}
	}

	/** Dive: a screech and a dark shadow under its target for 1.2 s, then it drops through the shadow and on. */
	static final class Dive extends Attack {
		Dive() {
			super("dive", config().dive);
		}

		@Override
		public boolean canUse(BossEntity boss, LivingEntity target) {
			return flying(boss);
		}

		@Override
		public double trackFraction() {
			// The shadow follows the target for most of the warning, then locks: move once it stops.
			return 0.75;
		}

		@Override
		public void start(AttackRun run) {
			run.points().add(groundAt(run, run.target()));
			run.sound(FinalRaidSounds.SCREECH, 6f, 0.8f);
		}

		@Override
		public void windup(AttackRun run) {
			FinalDragon dragon = dragon(run);
			LivingEntity target = run.target();
			if (target != null && target.isAlive() && run.tick() < run.stageLength() * trackFraction()) {
				run.points().set(0, target.position());
			}
			Vec3 shadow = run.points().get(0);
			// Rear up high behind the shadow.
			double back = dragon.arena().scaled(14);
			Vec3 perch = shadow.subtract(run.direction().scale(back)).add(0, back, 0);
			dragon.steer(perch, 1.6, 0.3);
			dragon.face(run.direction());
			dragon.setXRot(-20);
			if (run.tick() % 2 == 0) {
				Telegraphs.circleFilling(run.level(), SHADOW, SHADOW, shadow, config().diveRadius, run.progress());
				Telegraphs.line(run.level(), OUTLINE, shadow, run.direction(), config().diveLength, config().diveRadius * 0.6);
				run.level().sendParticles(SHADOW, true, true, shadow.x, shadow.y + 0.1, shadow.z, 6, config().diveRadius * 0.4, 0, config().diveRadius * 0.4, 0);
			}
			if (run.tick() == run.stageLength() / 2) {
				run.sound(FinalRaidSounds.SCREECH, 6f, 1.1f);
			}
		}

		@Override
		public void active(AttackRun run) {
			FinalDragon dragon = dragon(run);
			Vec3 shadow = run.points().get(0);
			Vec3 end = shadow.add(run.direction().scale(dragon.arena().scaled(config().diveLength)));
			int half = Math.max(1, run.stageLength() / 2);
			int left;
			Vec3 goal;
			if (run.tick() < half) {
				goal = shadow.add(0, 1, 0);
				left = half - run.tick();
			} else {
				goal = end.add(0, 1.5, 0);
				left = run.stageLength() - run.tick();
			}
			moveTo(dragon, goal, left);
			dragon.faceVelocity();
			if (run.first()) {
				run.sound(FinalRaidSounds.SCREECH, 6f, 1.3f);
			}
			ServerLevel level = run.level();
			level.sendParticles(ParticleTypes.CLOUD, true, true, dragon.getX(), dragon.getY() + 1, dragon.getZ(), 6, 1.5, 0.5, 1.5, 0.05);
			Vec3 body = dragon.position().add(0, -2, 0);
			for (LivingEntity victim : Hitboxes.inCircle(dragon, body, config().diveRadius, 6)) {
				if (run.hurt(victim)) {
					Vec3 dir = run.direction();
					victim.knockback(config().diveKnockback, -dir.x, -dir.z, run.level().damageSources().mobAttack(dragon), 0);
					victim.needsSync = true;
				}
			}
		}

		@Override
		public void recovery(AttackRun run) {
			FinalDragon dragon = dragon(run);
			dragon.steer(dragon.position().add(run.direction().scale(10)).add(0, 6, 0), 1.2, 0.25);
			dragon.faceVelocity();
		}
	}

	/** Void orbs: violet light gathers in its jaws, then it spits slow homing orbs, one per player and a spare. */
	static final class VoidOrbBarrage extends Attack {
		VoidOrbBarrage() {
			super("void_orbs", config().voidOrbs);
		}

		@Override
		public boolean canUse(BossEntity boss, LivingEntity target) {
			return flying(boss) || perched(boss);
		}

		@Override
		public double trackFraction() {
			return 0;
		}

		@Override
		public void start(AttackRun run) {
			run.sound(FinalRaidSounds.ORB, 4f, 0.5f);
			if (perched(run.boss())) {
				run.boss().face(dragon(run).perchFacing());
			}
		}

		@Override
		public void windup(AttackRun run) {
			FinalDragon dragon = dragon(run);
			if (dragon.mode() == FinalDragon.Mode.FLYING) {
				dragon.setDeltaMovement(dragon.getDeltaMovement().scale(0.85));
			}
			if (run.tick() % 2 == 0) {
				Vec3 mouth = dragon.mouth();
				run.level().sendParticles(ParticleTypes.PORTAL, true, true, mouth.x, mouth.y, mouth.z, 10, 1.2, 1.2, 1.2, -0.6);
				run.level().sendParticles(VOID, true, true, mouth.x, mouth.y, mouth.z, 2, 0.2 * run.progress(), 0.2, 0.2 * run.progress(), 0);
			}
		}

		@Override
		public void active(AttackRun run) {
			if (!run.first()) {
				return;
			}
			FinalDragon dragon = dragon(run);
			List<LivingEntity> targets = new ArrayList<>();
			for (ServerPlayer player : dragon.participants()) {
				if (dragon.inFight(player) && !player.isCreative()) {
					targets.add(player);
				}
			}
			if (targets.isEmpty() && run.target() != null) {
				targets.add(run.target());
			}
			if (targets.isEmpty()) {
				return;
			}
			int count = config().voidOrbsBase + targets.size() - 1;
			Vec3 mouth = dragon.mouth();
			float damage = (float) run.scaled(stats().damage);
			for (int i = 0; i < count; i++) {
				Vec3 at = mouth.add((run.random().nextDouble() - 0.5) * 2, (run.random().nextDouble() - 0.5), (run.random().nextDouble() - 0.5) * 2);
				dragon.orbs().spawn(at, targets.get(i % targets.size()), damage);
			}
			run.sound(FinalRaidSounds.ORB_POP, 4f, 0.5f);
		}
	}

	/**
	 * Tail sweep (perched): the tail drags a wide low arc around everything but its head. Jump it, or stand in front of
	 * the head (where the wing gust lands instead).
	 */
	static final class TailSweep extends Attack {
		TailSweep() {
			super("tail_sweep", config().tailSweep);
		}

		@Override
		public boolean canUse(BossEntity boss, LivingEntity target) {
			return perched(boss);
		}

		@Override
		public double trackFraction() {
			return 0;
		}

		private static Vec3 back(AttackRun run) {
			return dragon(run).perchFacing().scale(-1);
		}

		@Override
		public void start(AttackRun run) {
			run.boss().face(dragon(run).perchFacing());
			run.setDirection(back(run));
			run.sound(FinalRaidSounds.TAIL, 4f, 0.7f);
		}

		@Override
		public void windup(AttackRun run) {
			if (run.tick() % 3 == 0) {
				Vec3 origin = run.boss().position();
				Telegraphs.arc(run.level(), OUTLINE, origin, back(run), config().tailSweepRadius, config().tailSweepAngle);
				if (run.progress() > 0.3f) {
					Telegraphs.arc(run.level(), FILL, origin, back(run), config().tailSweepRadius * run.progress(), config().tailSweepAngle);
				}
			}
		}

		@Override
		public void active(AttackRun run) {
			if (!run.first()) {
				return;
			}
			run.sound(FinalRaidSounds.TAIL, 5f, 1.2f);
			Vec3 origin = run.boss().position();
			double half = Math.toRadians(config().tailSweepAngle / 2);
			Vec3 back = back(run);
			double base = Math.atan2(back.z, back.x);
			for (int i = 0; i <= 24; i++) {
				double a = base - half + 2 * half * i / 24;
				double r = config().tailSweepRadius * 0.75;
				run.level().sendParticles(ParticleTypes.SWEEP_ATTACK, true, true, origin.x + Math.cos(a) * r, origin.y + 0.6, origin.z + Math.sin(a) * r, 1, 0,
						0, 0, 0);
			}
			for (LivingEntity victim : Hitboxes.inArc(run.boss(), origin, back, config().tailSweepRadius, config().tailSweepAngle, config().tailSweepHeight)) {
				if (run.hurt(victim)) {
					Vec3 away = new Vec3(victim.getX() - origin.x, 0, victim.getZ() - origin.z);
					if (away.lengthSqr() > 1e-4) {
						away = away.normalize();
						victim.push(away.x * 0.8, 0.35, away.z * 0.8);
						victim.needsSync = true;
					}
				}
			}
		}
	}

	/**
	 * Wing gust (perched): the wings rise and draw air in (white streams flow toward it), then one beat throws everyone
	 * in front of it toward the void edge. Telegraphed by the cone outline; stand beside or behind it.
	 */
	static final class WingGust extends Attack {
		WingGust() {
			super("wing_gust", config().wingGust);
		}

		@Override
		public boolean canUse(BossEntity boss, LivingEntity target) {
			return perched(boss);
		}

		@Override
		public double trackFraction() {
			return 0;
		}

		@Override
		public void start(AttackRun run) {
			run.boss().face(dragon(run).perchFacing());
			run.setDirection(dragon(run).perchFacing());
			run.sound(FinalRaidSounds.GUST, 4f, 0.6f);
		}

		@Override
		public void windup(AttackRun run) {
			ServerLevel level = run.level();
			Vec3 origin = run.boss().position();
			if (run.tick() % 3 == 0) {
				Telegraphs.arc(level, FILL, origin, run.direction(), config().wingGustRadius, config().wingGustAngle);
			}
			// Air rushing in toward the wings.
			for (int i = 0; i < 3; i++) {
				double spread = Math.toRadians(config().wingGustAngle / 2) * (run.random().nextDouble() * 2 - 1);
				Vec3 dir = rotate(run.direction(), spread);
				double r = config().wingGustRadius * (0.5 + run.random().nextDouble() * 0.5);
				Vec3 p = origin.add(dir.scale(r)).add(0, 0.5 + run.random().nextDouble() * 2, 0);
				level.sendParticles(ParticleTypes.CLOUD, true, true, p.x, p.y, p.z, 0, -dir.x, 0, -dir.z, 0.6);
			}
		}

		@Override
		public void active(AttackRun run) {
			ServerLevel level = run.level();
			FinalDragon dragon = dragon(run);
			Vec3 origin = dragon.position();
			for (int i = 0; i < 6; i++) {
				double spread = Math.toRadians(config().wingGustAngle / 2) * (run.random().nextDouble() * 2 - 1);
				Vec3 dir = rotate(run.direction(), spread);
				Vec3 p = origin.add(dir.scale(2)).add(0, 1 + run.random().nextDouble() * 2, 0);
				level.sendParticles(ParticleTypes.GUST, true, true, p.x + dir.x * 3, p.y, p.z + dir.z * 3, 1, 0, 0, 0, 0);
				level.sendParticles(ParticleTypes.CLOUD, true, true, p.x, p.y, p.z, 0, dir.x, 0, dir.z, 1.2);
			}
			if (!run.first()) {
				return;
			}
			run.sound(FinalRaidSounds.GUST, 5f, 1.0f);
			Vec3 center = dragon.arena().centerVec();
			for (LivingEntity victim : Hitboxes.inArc(dragon, origin, run.direction(), config().wingGustRadius, config().wingGustAngle, 6)) {
				run.hurt(victim);
				// Toward the void: away from the island's center (or straight out from the wings when standing on it).
				Vec3 out = new Vec3(victim.getX() - center.x, 0, victim.getZ() - center.z);
				out = out.lengthSqr() < 4 ? run.direction() : out.normalize();
				victim.push(out.x * config().wingGustPush, 0.45, out.z * config().wingGustPush);
				victim.needsSync = true;
			}
		}

		private static Vec3 rotate(Vec3 dir, double radians) {
			double cos = Math.cos(radians);
			double sin = Math.sin(radians);
			return new Vec3(dir.x * cos - dir.z * sin, 0, dir.x * sin + dir.z * cos);
		}
	}

	/** Meteor shower (P3): telegraph circles under every player and around them fill for 1.5 s, then the sky falls. */
	static final class MeteorShower extends Attack {
		MeteorShower() {
			super("meteor_shower", config().meteorShower);
		}

		@Override
		public boolean canUse(BossEntity boss, LivingEntity target) {
			return flying(boss);
		}

		@Override
		public void start(AttackRun run) {
			FinalDragon dragon = dragon(run);
			run.sound(FinalRaidSounds.ROAR, 6f, 1.4f);
			List<LivingEntity> targets = new ArrayList<>();
			for (ServerPlayer player : dragon.participants()) {
				if (dragon.inFight(player) && !player.isCreative()) {
					targets.add(player);
				}
			}
			if (targets.isEmpty() && run.target() != null) {
				targets.add(run.target());
			}
			for (LivingEntity target : targets) {
				run.points().add(ground(run.level(), target.position()));
				for (int i = 1; i < config().meteorsPerPlayer; i++) {
					run.points().add(scatter(run, target.position(), config().meteorScatter));
				}
			}
			Vec3 center = dragon.arena().centerVec();
			for (int i = 0; i < config().meteorsExtra && !targets.isEmpty(); i++) {
				run.points().add(scatter(run, center, dragon.arena().scaled(24)));
			}
		}

		private static Vec3 scatter(AttackRun run, Vec3 around, double range) {
			double a = run.random().nextDouble() * Math.PI * 2;
			double r = 2 + run.random().nextDouble() * Math.max(0, range - 2);
			return ground(run.level(), around.add(Math.cos(a) * r, 0, Math.sin(a) * r));
		}

		@Override
		public void windup(AttackRun run) {
			if (run.tick() % 3 == 0) {
				for (Vec3 point : run.points()) {
					Telegraphs.circleFilling(run.level(), OUTLINE, FILL, point, config().meteorRadius, run.progress());
				}
			}
			// Falling stars high above, getting closer.
			if (run.tick() % 4 == 0) {
				for (Vec3 point : run.points()) {
					double h = 30 * (1 - run.progress()) + 6;
					run.level().sendParticles(ParticleTypes.END_ROD, true, true, point.x, point.y + h, point.z, 2, 0.3, 0.3, 0.3, 0.02);
				}
			}
		}

		@Override
		public void active(AttackRun run) {
			List<Vec3> points = run.points();
			int n = points.size();
			for (int i = 0; i < n; i++) {
				// Impacts are spread over the active window, so the shower rolls across the island.
				int at = i * run.stageLength() / Math.max(1, n);
				if (run.tick() != at) {
					continue;
				}
				Vec3 p = points.get(i);
				ServerLevel level = run.level();
				Telegraphs.column(level, ParticleTypes.REVERSE_PORTAL, p, 10, 0.3, 2);
				level.sendParticles(ParticleTypes.EXPLOSION, true, true, p.x, p.y + 0.5, p.z, 2, 0.6, 0.2, 0.6, 0);
				level.sendParticles(VOID, true, true, p.x, p.y + 0.3, p.z, 30, config().meteorRadius * 0.5, 0.3, config().meteorRadius * 0.5, 0);
				run.sound(FinalRaidSounds.METEOR, p, 4f, 0.7f + run.random().nextFloat() * 0.3f);
				for (LivingEntity victim : Hitboxes.inCircle(run.boss(), p, config().meteorRadius, 3)) {
					if (run.hurt(victim)) {
						victim.push(0, 0.5, 0);
						victim.needsSync = true;
					}
				}
			}
			// It keeps circling while the sky falls.
			FinalDragon dragon = dragon(run);
			dragon.steer(dragon.position().add(dragon.getDeltaMovement().scale(4)), 0.8, 0.1);
			dragon.faceVelocity();
		}

		/** The floor under {@code pos}: a standable surface a few blocks up or down, else {@code pos}. */
		private static Vec3 ground(ServerLevel level, Vec3 pos) {
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
}
