package io.github.tmldude.blocksouls.bosses.palerider;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import io.github.tmldude.blocksouls.bosses.api.Attack;
import io.github.tmldude.blocksouls.bosses.api.AttackRun;
import io.github.tmldude.blocksouls.bosses.api.BossEntity;
import io.github.tmldude.blocksouls.bosses.api.Hitboxes;
import io.github.tmldude.blocksouls.bosses.api.Telegraphs;
import io.github.tmldude.blocksouls.bosses.palerider.PaleRiderMath.LanceStep;

/** The Pale Rider's moves. Numbers come from {@link PaleRiderConfig}. */
final class PaleRiderAttacks {
	private PaleRiderAttacks() {
	}

	private static PaleRiderConfig config() {
		return PaleRider.config();
	}

	/** Theme for the shared archetypes: pale soul fire. */
	static final io.github.tmldude.blocksouls.bosses.archetype.Theme THEME = new io.github.tmldude.blocksouls.bosses.archetype.Theme(
			new net.minecraft.core.particles.DustParticleOptions(0x9FE3E8, 1.6f), new net.minecraft.core.particles.DustParticleOptions(0xF0FFFF, 1.2f),
			ParticleTypes.SOUL_FIRE_FLAME, PaleRiderSounds.WINDUP, PaleRiderSounds.SCREAM);

	private static void slashParticles(AttackRun run, Vec3 origin, Vec3 dir, double radius, double angle) {
		double half = Math.toRadians(angle / 2);
		double base = Math.atan2(dir.z, dir.x);
		for (int i = 0; i <= 14; i++) {
			double a = base - half + 2 * half * i / 14;
			double r = radius * 0.8;
			run.level().sendParticles(ParticleTypes.SWEEP_ATTACK, true, true, origin.x + Math.cos(a) * r, origin.y + 1.0,
					origin.z + Math.sin(a) * r, 1, 0, 0, 0, 0);
		}
	}

	/**
	 * Lance charge: lowers the lance, a 20-block lane lights up, and the rider gallops straight through it. Then it
	 * wheels around, a new lane toward its target lights up, and it makes a second pass.
	 */
	static final class Lance extends Attack {
		Lance() {
			super("lance", config().lance);
		}

		@Override
		public double trackFraction() {
			return 0.6;
		}

		@Override
		public void start(AttackRun run) {
			run.sound(PaleRiderSounds.WINDUP, 2.0f, 0.8f);
		}

		@Override
		public void windup(AttackRun run) {
			if (run.tick() % 3 == 0) {
				Telegraphs.lineFilling(run.level(), PaleRider.OUTLINE, PaleRider.FILL, run.boss().position(), run.direction(), config().lanceLength,
						config().lanceHalfWidth, run.progress());
			}
		}

		@Override
		public void active(AttackRun run) {
			PaleRiderConfig c = config();
			BossEntity boss = run.boss();
			int pass = c.lancePassTicks;
			int turn = c.lanceTurnTicks;
			LanceStep step = PaleRiderMath.lanceStep(run.tick(), pass, turn);
			switch (step) {
				case FIRST_PASS, SECOND_PASS -> {
					int t = step == LanceStep.FIRST_PASS ? run.tick() : run.tick() - pass - turn;
					if (t == 0) {
						run.sound(PaleRiderSounds.CHARGE, 2.5f, 0.9f);
					}
					gallop(run, boss, c.lanceLength / Math.max(1, pass), pass + turn);
				}
				case TURN -> {
					int t = run.tick() - pass;
					boss.setDeltaMovement(boss.getDeltaMovement().multiply(0.5, 1, 0.5));
					LivingEntity target = run.target();
					if (t < turn * 0.6 && target != null && target.isAlive()) {
						// Wheels around toward its target; the return lane locks for the last 40% of the turn.
						Vec3 to = target.position().subtract(boss.position());
						if (to.horizontalDistanceSqr() > 1e-4) {
							run.setDirection(to);
						}
					}
					boss.face(run.direction());
					if (t % 3 == 0) {
						Telegraphs.lineFilling(run.level(), PaleRider.OUTLINE, PaleRider.FILL, boss.position(), run.direction(), c.lanceLength,
								c.lanceHalfWidth, (t + 1) / (float) turn);
					}
				}
				default -> boss.setDeltaMovement(0, boss.getDeltaMovement().y, 0);
			}
		}

		/** One tick of a pass: runs along the locked direction and spears whoever is in the way (once per pass). */
		private static void gallop(AttackRun run, BossEntity boss, double speed, int hitInterval) {
			Vec3 dir = run.direction();
			boss.face(dir);
			boss.setDeltaMovement(dir.x * speed, boss.getDeltaMovement().y, dir.z * speed);
			ServerLevel level = run.level();
			level.sendParticles(ParticleTypes.CLOUD, boss.getX(), boss.getY() + 0.3, boss.getZ(), 3, 0.5, 0.1, 0.5, 0.01);
			level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, boss.getX(), boss.getY() + 0.6, boss.getZ(), 2, 0.4, 0.3, 0.4, 0.01);
			for (LivingEntity victim : Hitboxes.inBox(boss, boss.getBoundingBox().inflate(0.8, 0, 0.8).expandTowards(dir.scale(speed)))) {
				if (run.hurtEvery(victim, config().lance.damage, hitInterval)) {
					victim.knockback(config().lanceKnockback, -dir.x, -dir.z, level.damageSources().mobAttack(boss), 0);
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

	/** Spectral sweep: the lance swings out in a wide arc around the rider. Jump it or get out of reach. */
	static final class Sweep extends Attack {
		Sweep() {
			super("sweep", config().sweep);
		}

		@Override
		public void start(AttackRun run) {
			run.sound(PaleRiderSounds.WINDUP, 1.5f, 1.2f);
		}

		@Override
		public void windup(AttackRun run) {
			if (run.tick() % 3 == 0) {
				Telegraphs.arc(run.level(), PaleRider.OUTLINE, run.boss().position(), run.direction(), config().sweepRadius, config().sweepAngle);
				if (run.progress() > 0.4f) {
					Telegraphs.arc(run.level(), PaleRider.FILL, run.boss().position(), run.direction(), config().sweepRadius * run.progress(),
							config().sweepAngle);
				}
			}
		}

		@Override
		public void active(AttackRun run) {
			if (!run.first()) {
				return;
			}
			run.sound(PaleRiderSounds.SWING, 2.0f, 0.8f);
			Vec3 origin = run.boss().position();
			slashParticles(run, origin, run.direction(), config().sweepRadius, config().sweepAngle);
			for (LivingEntity victim : Hitboxes.inArc(run.boss(), origin, run.direction(), config().sweepRadius, config().sweepAngle,
					config().sweepHeight)) {
				run.hurt(victim);
			}
		}
	}

	/** Rearing stomp: the horse rears up and slams down; a low ring shockwave rolls outward. Jump the ring. */
	static final class Stomp extends Attack {
		Stomp() {
			super("stomp", config().stomp);
		}

		@Override
		public void start(AttackRun run) {
			run.sound(PaleRiderSounds.SCREAM, 1.5f, 1.4f);
		}

		@Override
		public void windup(AttackRun run) {
			if (run.tick() % 3 == 0) {
				Telegraphs.circleFilling(run.level(), PaleRider.OUTLINE, PaleRider.FILL, run.boss().position(), config().stompRadius, run.progress());
			}
		}

		@Override
		public void active(AttackRun run) {
			PaleRiderConfig c = config();
			Vec3 center = run.origin();
			int ticks = Math.max(1, run.stageLength());
			double r = c.stompRadius * (run.tick() + 1) / ticks;
			double band = c.stompRadius / ticks * 1.5;
			if (run.first()) {
				run.sound(PaleRiderSounds.STOMP, 2.5f, 0.7f);
				run.level().sendParticles(ParticleTypes.EXPLOSION, center.x, center.y + 0.3, center.z, 1, 0, 0, 0, 0);
			}
			Telegraphs.circle(run.level(), ParticleTypes.CLOUD, center, r);
			for (LivingEntity victim : Hitboxes.inCircle(run.boss(), center, r, c.stompHeight)) {
				// Only the moving ring hurts: a player who jumped it and landed inside is safe.
				if (Hitboxes.horizontalDistance(victim, center) + victim.getBbWidth() / 2 >= r - band && run.hurt(victim)) {
					victim.push(0, c.stompKnockup, 0);
					victim.needsSync = true;
				}
			}
		}
	}

	/** Wraith call: raises the lance and screams; three skeleton riders gallop out of the fog. Once per phase. */
	static final class WraithCall extends Attack {
		WraithCall() {
			super("call", config().wraithCall);
		}

		@Override
		public boolean canUse(BossEntity boss, LivingEntity target) {
			return boss instanceof PaleRider rider && rider.canCallWraiths() && config().wraithCallRiders > 0;
		}

		@Override
		public void start(AttackRun run) {
			run.sound(PaleRiderSounds.CALL, 3.0f, 0.8f);
		}

		@Override
		public void windup(AttackRun run) {
			if (run.tick() % 2 == 0) {
				BossEntity boss = run.boss();
				double a = run.tick() * 0.6;
				for (int i = 0; i < 3; i++) {
					double b = a + i * Math.PI * 2 / 3;
					run.level().sendParticles(ParticleTypes.SOUL, true, true, boss.getX() + Math.cos(b) * 2.5, boss.getY() + 0.2,
							boss.getZ() + Math.sin(b) * 2.5, 1, 0.1, 0.3, 0.1, 0.02);
				}
			}
		}

		@Override
		public void active(AttackRun run) {
			if (run.first() && run.boss() instanceof PaleRider rider) {
				rider.summonWraiths(run.level(), run.target());
			}
		}
	}

	/** Phase 2: three spectral spears thrown in a fan; each flies along its own telegraphed line. */
	static final class Spears extends Attack {
		Spears() {
			super("spears", config().spears);
		}

		private static Vec3[] directions(AttackRun run) {
			double[][] fan = PaleRiderMath.fan(run.direction().x, run.direction().z, config().spearCount, config().spearSpread);
			Vec3[] out = new Vec3[fan.length];
			for (int i = 0; i < fan.length; i++) {
				out[i] = new Vec3(fan[i][0], 0, fan[i][1]);
			}
			return out;
		}

		@Override
		public void start(AttackRun run) {
			run.sound(PaleRiderSounds.WINDUP, 1.5f, 1.4f);
		}

		@Override
		public void windup(AttackRun run) {
			if (run.tick() % 3 == 0) {
				for (Vec3 dir : directions(run)) {
					Telegraphs.lineFilling(run.level(), PaleRider.OUTLINE, PaleRider.FILL, run.boss().position(), dir, config().spearLength,
							config().spearHalfWidth, run.progress());
				}
			}
			// Spears forming over the rider's head.
			BossEntity boss = run.boss();
			run.level().sendParticles(ParticleTypes.END_ROD, boss.getX(), boss.getY() + boss.getBbHeight() + 0.5, boss.getZ(), 1, 0.6, 0.2, 0.6, 0);
		}

		@Override
		public void active(AttackRun run) {
			PaleRiderConfig c = config();
			if (run.first()) {
				run.sound(PaleRiderSounds.THROW, 2.0f, 1.0f);
			}
			int ticks = Math.max(1, run.stageLength());
			double from = c.spearLength * run.tick() / ticks;
			double to = c.spearLength * (run.tick() + 1) / ticks;
			Vec3 origin = run.boss().position();
			for (Vec3 dir : directions(run)) {
				Vec3 tip = origin.add(dir.scale(to));
				run.level().sendParticles(ParticleTypes.END_ROD, true, true, tip.x, tip.y + 1.2, tip.z, 3, 0.1, 0.1, 0.1, 0.01);
				run.level().sendParticles(ParticleTypes.SOUL_FIRE_FLAME, true, true, tip.x, tip.y + 1.2, tip.z, 1, 0.05, 0.05, 0.05, 0);
				Vec3 segment = origin.add(dir.scale(from));
				for (LivingEntity victim : Hitboxes.inLine(run.boss(), segment, dir, to - from, c.spearHalfWidth, 2.5)) {
					run.hurt(victim);
				}
			}
		}
	}

	/** Phase 2: the rider shimmers in place and at a spot behind its target, then blinks there and cuts. */
	static final class Blink extends Attack {
		Blink() {
			super("blink", config().blink);
		}

		/** The spot behind the target; follows it for the first 70% of the shimmer, then locks. */
		private static Vec3 destination(AttackRun run) {
			LivingEntity target = run.target();
			if (target != null && target.isAlive() && (run.points().isEmpty() || run.tick() < run.stageLength() * 0.7)) {
				BossEntity boss = run.boss();
				double[] spot = PaleRiderMath.behind(boss.getX(), boss.getZ(), target.getX(), target.getZ(), config().blinkBehind);
				Vec3 dest = insideArena(boss, new Vec3(spot[0], target.getY(), spot[1]));
				if (run.points().isEmpty()) {
					run.points().add(dest);
				} else {
					run.points().set(0, dest);
				}
			}
			return run.points().isEmpty() ? run.boss().position() : run.points().get(0);
		}

		/** Never blinks through the fog wall: pulls the spot back inside the arena. */
		private static Vec3 insideArena(BossEntity boss, Vec3 dest) {
			Vec3 home = Vec3.atBottomCenterOf(boss.home());
			double max = Math.max(1, boss.arenaRadius() - 1.5);
			double dx = dest.x - home.x;
			double dz = dest.z - home.z;
			double d = Math.sqrt(dx * dx + dz * dz);
			return d <= max ? dest : new Vec3(home.x + dx / d * max, dest.y, home.z + dz / d * max);
		}

		@Override
		public void start(AttackRun run) {
			run.sound(PaleRiderSounds.BLINK, 1.5f, 0.7f);
			destination(run);
		}

		@Override
		public void windup(AttackRun run) {
			Vec3 dest = destination(run);
			if (run.tick() % 2 == 0) {
				BossEntity boss = run.boss();
				run.level().sendParticles(ParticleTypes.REVERSE_PORTAL, true, true, boss.getX(), boss.getY() + 1.2, boss.getZ(), 6, 0.4, 0.8, 0.4, 0.02);
				run.level().sendParticles(ParticleTypes.END_ROD, true, true, dest.x, dest.y + 1.2, dest.z, 4, 0.3, 0.7, 0.3, 0.01);
				Telegraphs.circle(run.level(), PaleRider.OUTLINE, dest, 0.8 + 0.6 * run.progress());
			}
		}

		@Override
		public void active(AttackRun run) {
			if (!run.first()) {
				return;
			}
			BossEntity boss = run.boss();
			ServerLevel level = run.level();
			Vec3 dest = destination(run);
			Vec3 delta = dest.subtract(boss.position());
			if (level.noCollision(boss, boss.getBoundingBox().move(delta))) {
				level.sendParticles(ParticleTypes.SOUL, boss.getX(), boss.getY() + 1, boss.getZ(), 15, 0.3, 0.6, 0.3, 0.03);
				boss.teleportTo(dest.x, dest.y, dest.z);
			}
			run.sound(PaleRiderSounds.BLINK, 2.0f, 1.3f);
			LivingEntity target = run.target();
			Vec3 dir = target != null && target.isAlive() ? target.position().subtract(boss.position()) : run.direction();
			if (dir.horizontalDistanceSqr() > 1e-4) {
				run.setDirection(dir);
			}
			boss.face(run.direction());
			slashParticles(run, boss.position(), run.direction(), config().blinkSlashRadius, config().blinkSlashAngle);
			for (LivingEntity victim : Hitboxes.inArc(boss, boss.position(), run.direction(), config().blinkSlashRadius, config().blinkSlashAngle, 2.5)) {
				run.hurt(victim);
			}
		}
	}

	/** Phase 2: a quick cut with the broken lance at close range. */
	static final class Slash extends Attack {
		Slash() {
			super("slash", config().slash);
		}

		@Override
		public void start(AttackRun run) {
			run.sound(PaleRiderSounds.WINDUP, 1.0f, 1.6f);
		}

		@Override
		public void windup(AttackRun run) {
			if (run.tick() % 3 == 0) {
				Telegraphs.arc(run.level(), PaleRider.OUTLINE, run.boss().position(), run.direction(), config().slashRadius, config().slashAngle);
			}
		}

		@Override
		public void active(AttackRun run) {
			if (!run.first()) {
				return;
			}
			run.sound(PaleRiderSounds.SWING, 1.5f, 1.2f);
			slashParticles(run, run.boss().position(), run.direction(), config().slashRadius, config().slashAngle);
			for (LivingEntity victim : Hitboxes.inArc(run.boss(), run.boss().position(), run.direction(), config().slashRadius, config().slashAngle,
					2.5)) {
				run.hurt(victim);
			}
		}
	}
}
