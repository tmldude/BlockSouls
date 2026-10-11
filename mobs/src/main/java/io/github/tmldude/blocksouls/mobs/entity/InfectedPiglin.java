package io.github.tmldude.blocksouls.mobs.entity;

import org.jspecify.annotations.Nullable;

import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

import io.github.tmldude.blocksouls.mobs.registry.MobSounds;

/** A piglin overrun by the infection: always hostile, gold sword, fleshy growths. Spawns naturally in the Nether. */
public class InfectedPiglin extends HostileMob {
	public InfectedPiglin(EntityType<? extends InfectedPiglin> type, Level level) {
		super(type, level);
	}

	@Override
	protected void registerGoals() {
		goalSelector.addGoal(0, new FloatGoal(this));
		goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0, false));
		goalSelector.addGoal(7, new WaterAvoidingRandomStrollGoal(this, 0.8));
		goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8));
		goalSelector.addGoal(8, new RandomLookAroundGoal(this));
		targetSelector.addGoal(1, new HurtByTargetGoal(this));
		targetSelector.addGoal(2, targetPlayers());
	}

	@Override
	public @Nullable SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason spawnReason,
			@Nullable SpawnGroupData groupData) {
		SpawnGroupData data = super.finalizeSpawn(level, difficulty, spawnReason, groupData);
		populateDefaultEquipmentSlots(level.getRandom(), difficulty);
		return data;
	}

	@Override
	protected void populateDefaultEquipmentSlots(RandomSource random, DifficultyInstance difficulty) {
		setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.GOLDEN_SWORD));
		setDropChance(EquipmentSlot.MAINHAND, 0.05f);
	}

	@Override
	protected SoundEvent ambientSound() {
		return MobSounds.INFECTED_PIGLIN_AMBIENT;
	}

	@Override
	protected SoundEvent hurtSound() {
		return MobSounds.INFECTED_PIGLIN_HURT;
	}

	@Override
	protected SoundEvent deathSound() {
		return MobSounds.INFECTED_PIGLIN_DEATH;
	}
}
