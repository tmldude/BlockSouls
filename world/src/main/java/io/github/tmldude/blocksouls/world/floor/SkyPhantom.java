package io.github.tmldude.blocksouls.world.floor;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Phantom;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.phys.AABB;

import io.github.tmldude.blocksouls.common.mob.Affix;
import io.github.tmldude.blocksouls.common.mob.Affixes;

/**
 * Skyrift's flyers (floor 3), all phantom-bodied (the vanilla phantom model and AI; their types don't burn in daylight).
 * {@link Role#ROC}: the Storm Roc mini-boss, huge, whose wingbeat throws everyone near it outward (toward the open edge).
 * {@link Role#MATRIARCH}: the Glider Matriarch mini-boss, who calls a frosted glider pack when she arrives and again at
 * half health. {@link Role#GLIDER}: a pack glider. Mini-boss health scales by party ({@link SkyBossMath}).
 */
public class SkyPhantom extends Phantom {
	public enum Role {
		ROC, MATRIARCH, GLIDER
	}

	private final Role role;
	private @Nullable ServerBossEvent bossBar;
	private int packsCalled;

	public SkyPhantom(EntityType<? extends Phantom> type, Level level, Role role) {
		super(type, level);
		this.role = role;
		this.xpReward = role == Role.GLIDER ? 3 : 30;
	}

	public Role role() {
		return role;
	}

	@Override
	public @Nullable SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason spawnReason,
			@Nullable SpawnGroupData groupData) {
		SpawnGroupData data = super.finalizeSpawn(level, difficulty, spawnReason, groupData);
		FloorsConfig config = WorldFloors.config();
		int players = Math.max(1, level.getLevel().getServer().getPlayerCount());
		switch (role) {
			case ROC -> {
				setPhantomSize(Math.max(0, config.rocSize));
				setMaxHealth(SkyBossMath.health(config.rocHealth, players));
				setPersistenceRequired();
			}
			case MATRIARCH -> {
				setPhantomSize(Math.max(0, config.matriarchSize));
				setMaxHealth(SkyBossMath.health(config.matriarchHealth, players));
				setPersistenceRequired();
			}
			case GLIDER -> setMaxHealth(Math.max(1.0, config.gliderHealth));
		}
		return data;
	}

	private void setMaxHealth(double health) {
		AttributeInstance max = getAttribute(Attributes.MAX_HEALTH);
		if (max != null) {
			max.setBaseValue(health);
		}
		setHealth((float) health);
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		if (role == Role.GLIDER) {
			return;
		}
		ServerBossEvent bar = bossBar();
		bar.setProgress(getHealth() / getMaxHealth());
		FloorsConfig config = WorldFloors.config();
		if (role == Role.ROC && config.rocGustTicks > 0 && tickCount % config.rocGustTicks == 0) {
			gust(level, config);
		}
		if (role == Role.MATRIARCH && (packsCalled == 0 || (packsCalled == 1 && getHealth() <= getMaxHealth() / 2))) {
			packsCalled++;
			callPack(level, config);
		}
	}

	/** The Roc's wingbeat: everyone within {@code rocGustRadius} is thrown outward and a little up. */
	void gust(ServerLevel level, FloorsConfig config) {
		AABB box = getBoundingBox().inflate(config.rocGustRadius);
		for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, box, p -> !p.isSpectator() && !p.isCreative())) {
			double dx = player.getX() - getX();
			double dz = player.getZ() - getZ();
			double d = Math.max(0.001, Math.sqrt(dx * dx + dz * dz));
			if (d > config.rocGustRadius) {
				continue;
			}
			player.push(dx / d * config.rocGustStrength, 0.45, dz / d * config.rocGustStrength);
			player.needsSync = true;
		}
		level.sendParticles(ParticleTypes.GUST_EMITTER_SMALL, getX(), getY(), getZ(), 1, 0, 0, 0, 0);
		level.playSound(null, blockPosition(), SoundEvents.PHANTOM_FLAP, SoundSource.HOSTILE, 3.0F, 0.5F);
	}

	/** The Matriarch's call: a party-scaled pack of frosted gliders around her. Returns how many came. */
	int callPack(ServerLevel level, FloorsConfig config) {
		int size = SkyBossMath.gliderPack(config.gliderPack, Math.max(1, level.getServer().getPlayerCount()));
		int called = 0;
		for (int i = 0; i < size; i++) {
			BlockPos at = blockPosition().offset(getRandom().nextInt(7) - 3, getRandom().nextInt(3), getRandom().nextInt(7) - 3);
			SkyPhantom glider = SkyMobs.SKY_GLIDER.spawn(level, at, EntitySpawnReason.MOB_SUMMONED);
			if (glider != null) {
				Affixes.apply(glider, Affix.FROSTED);
				glider.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 100, 0));
				called++;
			}
		}
		level.playSound(null, blockPosition(), SoundEvents.PHANTOM_AMBIENT, SoundSource.HOSTILE, 3.0F, 1.4F);
		return called;
	}

	private ServerBossEvent bossBar() {
		if (bossBar == null) {
			bossBar = new ServerBossEvent(getUUID(), getDisplayName(), role == Role.ROC ? BossEvent.BossBarColor.BLUE : BossEvent.BossBarColor.WHITE,
					BossEvent.BossBarOverlay.PROGRESS);
		}
		return bossBar;
	}

	@Override
	public void startSeenByPlayer(ServerPlayer player) {
		super.startSeenByPlayer(player);
		if (role != Role.GLIDER) {
			bossBar().addPlayer(player);
		}
	}

	@Override
	public void stopSeenByPlayer(ServerPlayer player) {
		super.stopSeenByPlayer(player);
		if (bossBar != null) {
			bossBar.removePlayer(player);
		}
	}
}
