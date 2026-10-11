package io.github.tmldude.blocksouls.mobs.entity;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.level.Level;

import io.github.tmldude.blocksouls.mobs.BlockSoulsMobs;
import io.github.tmldude.blocksouls.mobs.MobsConfig;
import io.github.tmldude.blocksouls.mobs.ai.PounceGoal;
import io.github.tmldude.blocksouls.mobs.registry.MobSounds;

/** Low and fast; leaps at targets within a few blocks. Small hitbox, hard to hit. */
public class Crawler extends WaveMob {
	public Crawler(EntityType<? extends Crawler> type, Level level) {
		super(type, level);
	}

	private static MobsConfig.Crawler config() {
		return BlockSoulsMobs.CONFIG.crawler;
	}

	@Override
	protected void registerAttackGoals() {
		goalSelector.addGoal(2, new PounceGoal(this, () -> config().leapRange, () -> config().leapUp, () -> config().leapForward,
				() -> config().leapCooldownTicks, MobSounds.CRAWLER_LEAP));
		goalSelector.addGoal(3, new MeleeAttackGoal(this, 1.0, true));
	}

	@Override
	protected SoundEvent ambientSound() {
		return MobSounds.CRAWLER_AMBIENT;
	}

	@Override
	protected SoundEvent hurtSound() {
		return MobSounds.CRAWLER_HURT;
	}

	@Override
	protected SoundEvent deathSound() {
		return MobSounds.CRAWLER_DEATH;
	}
}
