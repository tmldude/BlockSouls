package io.github.tmldude.blocksouls.bosses.api;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import io.github.tmldude.blocksouls.bosses.logic.BossScaling;

/**
 * One execution of an {@link Attack}: the stage clock plus scratch state for the attack's hooks. The boss creates it
 * when the attack is picked and drops it after {@link Attack#end}.
 */
public final class AttackRun {
	private final BossEntity boss;
	private final ServerLevel level;
	private final Attack attack;
	private final BossPhase phase;
	private final int windupTicks;
	private final int activeTicks;
	private int recoveryTicks;
	private @Nullable LivingEntity target;
	private Stage stage = Stage.WINDUP;
	private int tick;
	private Vec3 origin;
	private Vec3 direction;
	private final List<Vec3> points = new ArrayList<>();
	private final Set<LivingEntity> hit = new HashSet<>();
	private final Map<LivingEntity, Integer> lastHitTick = new HashMap<>();
	private int totalTicks;
	private boolean endActive;
	private final Set<java.util.UUID> rolled = new HashSet<>();
	private int firstTelegraphTick = -1;
	private int firstHitTick = -1;

	AttackRun(BossEntity boss, ServerLevel level, Attack attack, BossPhase phase, @Nullable LivingEntity target) {
		this.boss = boss;
		this.level = level;
		this.attack = attack;
		this.phase = phase;
		this.target = target;
		AttackStats stats = attack.stats();
		this.windupTicks = Math.max(1, stats.windupTicks);
		this.activeTicks = Math.max(1, stats.activeTicks);
		this.recoveryTicks = Math.max(0, (int) Math.round(stats.recoveryTicks * phase.recoveryMult()));
		this.origin = boss.position();
		this.direction = target != null ? flatDirection(boss.position(), target.position(), boss.getLookAngle()) : flat(boss.getLookAngle());
	}

	// --- Who and where ---

	public BossEntity boss() {
		return boss;
	}

	public ServerLevel level() {
		return level;
	}

	public Attack attack() {
		return attack;
	}

	public RandomSource random() {
		return boss.getRandom();
	}

	/** The target when the attack started (may be null, e.g. in a test). Its position is live. */
	public @Nullable LivingEntity target() {
		return target;
	}

	/** Where the attack is anchored. Defaults to the boss's feet at start; attacks may move it. */
	public Vec3 origin() {
		return origin;
	}

	public void setOrigin(Vec3 origin) {
		this.origin = origin;
	}

	/** Horizontal unit vector the attack aims along (tracks the target during the windup, see {@link Attack#trackFraction}). */
	public Vec3 direction() {
		return direction;
	}

	public void setDirection(Vec3 direction) {
		this.direction = flat(direction);
	}

	/** Free list of positions for the attack (e.g. where pillars will erupt). */
	public List<Vec3> points() {
		return points;
	}

	// --- Clock ---

	public Stage stage() {
		return stage;
	}

	/** Ticks into the current stage (0 on the first tick of a stage). */
	public int tick() {
		return tick;
	}

	/** Ticks since the attack started. */
	public int totalTicks() {
		return totalTicks;
	}

	/** Length of the current stage. */
	public int stageLength() {
		return switch (stage) {
			case WINDUP -> windupTicks;
			case ACTIVE -> activeTicks;
			case RECOVERY -> recoveryTicks;
			default -> 1;
		};
	}

	/** 0..1 through the current stage. */
	public float progress() {
		return Math.min(1f, (tick + 1) / (float) stageLength());
	}

	/** First tick of the current stage. */
	public boolean first() {
		return tick == 0;
	}

	/** Cut the active stage short (e.g. a charge that hit a wall); recovery starts next tick. */
	public void endActive() {
		endActive = true;
	}

	/** Lengthens the recovery (e.g. a charge stuck in a wall). Only from the active stage or the recovery itself. */
	public void extendRecovery(int ticks) {
		if (ticks > 0 && (stage == Stage.ACTIVE || stage == Stage.RECOVERY)) {
			recoveryTicks += ticks;
		}
	}

	/**
	 * Ticks until the hitboxes go live: positive during the windup, 0 on the first active tick, negative after that
	 * (and {@code Integer.MIN_VALUE / 2} once in recovery).
	 */
	public int ticksBeforeActive() {
		return switch (stage) {
			case WINDUP -> windupTicks - tick;
			case ACTIVE -> -tick;
			default -> Integer.MIN_VALUE / 2;
		};
	}

	public int activeTicks() {
		return activeTicks;
	}

	/** Marks that this run drew its telegraph this tick (archetypes call it; tests check telegraph-before-hit). */
	public void telegraphed() {
		if (firstTelegraphTick < 0) {
			firstTelegraphTick = totalTicks;
		}
	}

	/** Run tick of the first telegraph, or -1. */
	public int firstTelegraphTick() {
		return firstTelegraphTick;
	}

	/** Run tick of the first landed hit, or -1. */
	public int firstHitTick() {
		return firstHitTick;
	}

	/** Records that {@code player} rolled during this run; false if they already had. */
	boolean markRolled(java.util.UUID player) {
		return rolled.add(player);
	}

	/** Advances the clock; returns false once the attack is over. Called by the boss after the stage's hook. */
	boolean advance() {
		tick++;
		totalTicks++;
		if (stage == Stage.ACTIVE && endActive) {
			tick = stageLength();
		}
		if (tick < stageLength()) {
			return true;
		}
		tick = 0;
		stage = switch (stage) {
			case WINDUP -> Stage.ACTIVE;
			case ACTIVE -> recoveryTicks > 0 ? Stage.RECOVERY : Stage.NONE;
			default -> Stage.NONE;
		};
		return stage != Stage.NONE;
	}

	// --- Damage ---

	/** Damage after difficulty and phase multipliers. */
	public double scaled(double baseDamage) {
		return BossScaling.damage(baseDamage, boss.damageMultiplier(), phase.damageMult());
	}

	/** Hits {@code victim} for the attack's damage, once per run. Returns true if it landed. */
	public boolean hurt(LivingEntity victim) {
		return hurt(victim, attack.stats().damage);
	}

	/** Hits {@code victim} for {@code baseDamage} (before scaling), once per run. */
	public boolean hurt(LivingEntity victim, double baseDamage) {
		if (!hit.add(victim)) {
			return false;
		}
		return deal(victim, baseDamage);
	}

	/** For lingering hitboxes (breath, pillars): hits at most once every {@code intervalTicks}. */
	public boolean hurtEvery(LivingEntity victim, double baseDamage, int intervalTicks) {
		Integer last = lastHitTick.get(victim);
		if (last != null && totalTicks - last < intervalTicks) {
			return false;
		}
		lastHitTick.put(victim, totalTicks);
		hit.add(victim);
		return deal(victim, baseDamage);
	}

	private boolean deal(LivingEntity victim, double baseDamage) {
		DamageSource source = level.damageSources().mobAttack(boss);
		float amount = (float) scaled(baseDamage);
		boolean landed = victim.hurtServer(level, source, amount);
		if (landed) {
			if (firstHitTick < 0) {
				firstHitTick = totalTicks;
			}
			boss.onAttackLanded(level, victim, amount);
		}
		return landed;
	}

	/** Everyone this run has hit so far. */
	public Set<LivingEntity> hitSoFar() {
		return hit;
	}

	// --- Feedback ---

	public void sound(SoundEvent sound, float volume, float pitch) {
		level.playSound(null, boss.getX(), boss.getY(), boss.getZ(), sound, SoundSource.HOSTILE, volume, pitch);
	}

	public void sound(SoundEvent sound, Vec3 at, float volume, float pitch) {
		level.playSound(null, at.x, at.y, at.z, sound, SoundSource.HOSTILE, volume, pitch);
	}

	// --- Internals ---

	void track(LivingEntity target) {
		this.target = target;
		this.direction = flatDirection(boss.position(), target.position(), direction);
	}

	public static Vec3 flat(Vec3 dir) {
		Vec3 flat = new Vec3(dir.x, 0, dir.z);
		double length = flat.length();
		return length < 1e-4 ? new Vec3(0, 0, 1) : flat.scale(1 / length);
	}

	public static Vec3 flatDirection(Vec3 from, Vec3 to, Vec3 fallback) {
		Vec3 delta = new Vec3(to.x - from.x, 0, to.z - from.z);
		return delta.lengthSqr() < 1e-4 ? flat(fallback) : delta.normalize();
	}
}
