package io.github.tmldude.blocksouls.mobs.entity;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.level.Level;

import io.github.tmldude.blocksouls.mobs.registry.MobSounds;

/** The bread and butter: slow, groaning, comes in numbers. */
public class Shambler extends WaveMob {
	public Shambler(EntityType<? extends Shambler> type, Level level) {
		super(type, level);
	}

	@Override
	protected void registerAttackGoals() {
		goalSelector.addGoal(3, new MeleeAttackGoal(this, 1.0, false));
	}

	@Override
	public int getAmbientSoundInterval() {
		return 160;
	}

	@Override
	protected SoundEvent ambientSound() {
		return MobSounds.SHAMBLER_AMBIENT;
	}

	@Override
	protected SoundEvent hurtSound() {
		return MobSounds.SHAMBLER_HURT;
	}

	@Override
	protected SoundEvent deathSound() {
		return MobSounds.SHAMBLER_DEATH;
	}
}
