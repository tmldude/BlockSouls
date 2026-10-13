package io.github.tmldude.blocksouls.bosses.blight;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import io.github.tmldude.blocksouls.bosses.api.BossDefinition;
import io.github.tmldude.blocksouls.bosses.api.BossEntity;
import io.github.tmldude.blocksouls.bosses.api.BossPhase;
import io.github.tmldude.blocksouls.bosses.archetype.ExpandingRing;
import io.github.tmldude.blocksouls.bosses.archetype.HomingOrbs;
import io.github.tmldude.blocksouls.bosses.archetype.LeapSlam;
import io.github.tmldude.blocksouls.bosses.archetype.LingeringPools;
import io.github.tmldude.blocksouls.bosses.archetype.RadialBurst;
import io.github.tmldude.blocksouls.bosses.archetype.Theme;
import io.github.tmldude.blocksouls.bosses.furnace.FurnaceKnightSounds;
import io.github.tmldude.blocksouls.common.CommonIds;

/**
 * The Blightheart (v2-floor5-blight): a hulking knight-shaped husk the blight grew around a swollen, pulsing heart. Built
 * only from the shared archetypes. Phase 1: rot wave, blight pools, spores, leap. Phase 2 (below 50%): the heart splits
 * open (+25% damage, faster) and the heartburst joins the rotation.
 */
public class Blightheart extends BossEntity {
	/** Sickly violet telegraph outline. */
	static final ParticleOptions OUTLINE = new DustParticleOptions(0x8A3FB8, 1.6f);
	/** Pale green fill as the windup runs out. */
	static final ParticleOptions FILL = new DustParticleOptions(0xB8E05A, 1.2f);
	/** Theme for the archetypes: spores and rot. Reuses the Furnace Knight's windup and impact sounds. */
	static final Theme THEME = new Theme(OUTLINE, FILL, ParticleTypes.WARPED_SPORE, FurnaceKnightSounds.WINDUP, FurnaceKnightSounds.SLAM);

	private static BossDefinition definition;

	public Blightheart(EntityType<? extends Blightheart> type, Level level) {
		super(type, level);
		addPart("heart", 0.8f, 0.8f, config().heartDamageMult, new Vec3(0, 1.7, 0.35));
	}

	public static BlightheartConfig config() {
		return Blighthearts.CONFIG;
	}

	/** Built once, after the config loads. Animation keys borrow the Furnace Knight model's (same rig). */
	static BossDefinition createDefinition() {
		BlightheartConfig c = config();
		ExpandingRing ring = new ExpandingRing("ring", c.ring, THEME, "slam");
		LingeringPools pools = new LingeringPools("pools", c.pools, THEME, "pillars");
		HomingOrbs spores = new HomingOrbs("spores", c.spores, THEME, "breath");
		LeapSlam leap = new LeapSlam("leap", c.leap, THEME, "charge");
		RadialBurst burst = new RadialBurst("burst", c.burst, THEME, "pillars");
		definition = BossDefinition.builder(Blighthearts.ID)
				.health(c.health)
				.arenaRadius(c.arenaRadius)
				// Blight crystals (world) and a legendary gun (guns), only when those modules are installed.
				.extraLoot(CommonIds.id(CommonIds.BOSSES, "boss_drops/blightheart_world"),
						CommonIds.id(CommonIds.BOSSES, "boss_drops/legendary_gun"))
				.phase(BossPhase.first().attacks(ring, pools, spores, leap))
				.phase(BossPhase.below(c.phase2Threshold)
						.attacks(ring, pools, spores, leap, burst)
						.damage(c.phase2Damage)
						.recovery(c.phase2Recovery)
						.cooldowns(c.phase2Cooldowns)
						.speed(c.phase2Speed)
						.onEnter(boss -> boss.level().playSound(null, boss.blockPosition(), SoundEvents.WARDEN_ROAR, SoundSource.HOSTILE, 3.0f, 0.7f)))
				.build();
		return definition;
	}

	public static BossDefinition boss() {
		return definition;
	}

	@Override
	public BossDefinition definition() {
		return definition;
	}

	@Override
	protected double preferredDistance() {
		return 4;
	}

	@Override
	public void tick() {
		super.tick();
		if (level().isClientSide() && isAlive() && tickCount % (phase() >= 1 ? 2 : 4) == 0) {
			double a = random.nextDouble() * Math.PI * 2;
			level().addParticle(ParticleTypes.WARPED_SPORE, getX() + Math.cos(a) * 0.7, getY() + 0.5 + random.nextDouble() * 2,
					getZ() + Math.sin(a) * 0.7, 0, 0.02, 0);
		}
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return SoundEvents.WARDEN_HEARTBEAT;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.WARDEN_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.WARDEN_DEATH;
	}

	@Override
	protected void playStepSound(BlockPos pos, BlockState state) {
		playSound(FurnaceKnightSounds.STEP, 0.8f, 0.7f);
	}

	@Override
	protected SoundEvent staggerSound() {
		return SoundEvents.WARDEN_ROAR;
	}

	@Override
	protected float getSoundVolume() {
		return 1.5f;
	}

	@Override
	protected void onPhaseEnter(int index) {
		if (index == 1 && level() instanceof ServerLevel level) {
			level.sendParticles(ParticleTypes.WARPED_SPORE, getX(), getY() + 1.5, getZ(), 80, 0.8, 1.2, 0.8, 0.05);
		}
	}
}
