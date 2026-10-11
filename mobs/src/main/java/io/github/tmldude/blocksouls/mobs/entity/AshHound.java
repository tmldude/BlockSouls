package io.github.tmldude.blocksouls.mobs.entity;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.level.Level;

import io.github.tmldude.blocksouls.mobs.ai.PounceGoal;
import io.github.tmldude.blocksouls.mobs.registry.MobSounds;

/** Fast, low, wolf-like hunter. Only arrives in packs sent by Nether dread, already hunting a player. */
public class AshHound extends HostileMob {
	public AshHound(EntityType<? extends AshHound> type, Level level) {
		super(type, level);
	}

	@Override
	protected void registerGoals() {
		goalSelector.addGoal(0, new FloatGoal(this));
		goalSelector.addGoal(2, new PounceGoal(this, () -> 5, () -> 0.35, () -> 0.8, () -> 50, null));
		goalSelector.addGoal(3, new MeleeAttackGoal(this, 1.1, true));
		goalSelector.addGoal(7, new WaterAvoidingRandomStrollGoal(this, 0.8));
		goalSelector.addGoal(8, new RandomLookAroundGoal(this));
		targetSelector.addGoal(1, new HurtByTargetGoal(this, AshHound.class).setAlertOthers(AshHound.class));
		targetSelector.addGoal(2, targetPlayers());
	}

	@Override
	protected SoundEvent ambientSound() {
		return MobSounds.ASH_HOUND_AMBIENT;
	}

	@Override
	protected SoundEvent hurtSound() {
		return MobSounds.ASH_HOUND_HURT;
	}

	@Override
	protected SoundEvent deathSound() {
		return MobSounds.ASH_HOUND_DEATH;
	}
}
