package io.github.tmldude.blocksouls.world.floor;

import org.jspecify.annotations.Nullable;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.MoveTowardsTargetGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;

/**
 * The Colossus, a Vault mini-boss (floor 4): a vault guardian on the vanilla iron golem's body (its swing throws you
 * up), turned on the players: never player-made, it hunts the nearest player instead of monsters. Party-scaled health
 * from {@code colossusHealth}, {@code colossusDamage} per swing (the golem's swing rolls half to one and a half of it).
 */
public class VaultColossus extends IronGolem {
	private final VaultBoss boss = new VaultBoss(this, BossEvent.BossBarColor.WHITE);

	public VaultColossus(EntityType<? extends IronGolem> type, Level level) {
		super(type, level);
		this.xpReward = 40;
	}

	@Override
	protected void registerGoals() {
		goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.0, true));
		goalSelector.addGoal(2, new MoveTowardsTargetGoal(this, 0.9, 32.0F));
		goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.6));
		goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 8.0F));
		goalSelector.addGoal(8, new RandomLookAroundGoal(this));
		targetSelector.addGoal(1, new HurtByTargetGoal(this));
		targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
	}

	@Override
	public @Nullable SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason spawnReason,
			@Nullable SpawnGroupData groupData) {
		SpawnGroupData data = super.finalizeSpawn(level, difficulty, spawnReason, groupData);
		VaultConfig config = VaultHazards.config();
		boss.setUp(level.getLevel(), config.colossusHealth, config.colossusDamage);
		return data;
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		boss.tick();
	}

	@Override
	public void die(DamageSource source) {
		super.die(source);
		boss.died();
	}

	@Override
	public void startSeenByPlayer(ServerPlayer player) {
		super.startSeenByPlayer(player);
		boss.seenBy(player);
	}

	@Override
	public void stopSeenByPlayer(ServerPlayer player) {
		super.stopSeenByPlayer(player);
		boss.unseenBy(player);
	}
}
