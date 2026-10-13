package io.github.tmldude.blocksouls.world.floor;

import org.jspecify.annotations.Nullable;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Unit;
import net.minecraft.world.BossEvent;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;

/**
 * The Listener, a Vault mini-boss (floor 4): a blind hunter on the vanilla warden's body and brain (it hunts by
 * sound and smell, and its darkness pulses through the dark vault), cut down to a mini-boss: party-scaled health from
 * {@code listenerHealth}, {@code listenerDamage} per swing, and it never digs away.
 */
public class VaultListener extends Warden {
	private final VaultBoss boss = new VaultBoss(this, BossEvent.BossBarColor.BLUE);

	public VaultListener(EntityType<? extends Monster> type, Level level) {
		super(type, level);
		this.xpReward = 40;
	}

	@Override
	public @Nullable SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason spawnReason,
			@Nullable SpawnGroupData groupData) {
		SpawnGroupData data = super.finalizeSpawn(level, difficulty, spawnReason, groupData);
		VaultConfig config = VaultHazards.config();
		boss.setUp(level.getLevel(), config.listenerHealth, config.listenerDamage);
		return data;
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		// a mini-boss stays for the fight: keep the dig cooldown topped up so the warden brain never burrows away
		getBrain().setMemoryWithExpiry(MemoryModuleType.DIG_COOLDOWN, Unit.INSTANCE, 1200L);
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
