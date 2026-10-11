package io.github.tmldude.blocksouls.mobs.entity;

import org.jspecify.annotations.Nullable;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import io.github.tmldude.blocksouls.common.player.PlayerStatus;
import io.github.tmldude.blocksouls.mobs.MobsConfig;

/** Base of all our mobs: downed players are never valid targets, and each mob names its own sounds. */
public abstract class HostileMob extends Monster {
	protected HostileMob(EntityType<? extends HostileMob> type, Level level) {
		super(type, level);
	}

	public static AttributeSupplier.Builder attributes(MobsConfig.Stats stats, double followRange) {
		return Monster.createMonsterAttributes()
				.add(Attributes.MAX_HEALTH, stats.health)
				.add(Attributes.ATTACK_DAMAGE, Math.max(stats.damage, 0))
				.add(Attributes.MOVEMENT_SPEED, stats.speed)
				.add(Attributes.ARMOR, stats.armor)
				.add(Attributes.FOLLOW_RANGE, followRange);
	}

	public static boolean isDowned(LivingEntity entity) {
		return entity instanceof Player player && PlayerStatus.isDowned(player);
	}

	/** Targets the nearest player that isn't downed. */
	protected NearestAttackableTargetGoal<Player> targetPlayers() {
		return new NearestAttackableTargetGoal<>(this, Player.class, true, (target, level) -> !isDowned(target));
	}

	@Override
	public boolean canAttack(LivingEntity target) {
		return !isDowned(target) && super.canAttack(target);
	}

	@Override
	public void setTarget(@Nullable LivingEntity target) {
		super.setTarget(target != null && isDowned(target) ? null : target);
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		LivingEntity target = getTarget();
		if (target != null && isDowned(target)) {
			super.setTarget(null);
		}
	}

	protected abstract SoundEvent ambientSound();

	protected abstract SoundEvent hurtSound();

	protected abstract SoundEvent deathSound();

	@Override
	protected SoundEvent getAmbientSound() {
		return ambientSound();
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return hurtSound();
	}

	@Override
	protected SoundEvent getDeathSound() {
		return deathSound();
	}
}
