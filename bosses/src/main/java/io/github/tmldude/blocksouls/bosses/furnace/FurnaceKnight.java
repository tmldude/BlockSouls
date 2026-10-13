package io.github.tmldude.blocksouls.bosses.furnace;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import io.github.tmldude.blocksouls.bosses.api.BossDefinition;
import io.github.tmldude.blocksouls.bosses.api.BossEntity;
import io.github.tmldude.blocksouls.bosses.api.BossPhase;
import io.github.tmldude.blocksouls.bosses.api.Stage;
import io.github.tmldude.blocksouls.bosses.archetype.ExpandingRing;
import io.github.tmldude.blocksouls.bosses.archetype.Feint;
import io.github.tmldude.blocksouls.bosses.archetype.LeapSlam;
import io.github.tmldude.blocksouls.bosses.archetype.LingeringPools;
import io.github.tmldude.blocksouls.common.CommonIds;

/**
 * The Furnace Knight: a 3.2-block knight whose helm is a glowing furnace grate, swinging a huge cleaver. Guards a
 * Nether bastion. Phase 1: slam, sweep, breath, charge. Phase 2 (below 50%): the blade ignites (+25% damage, faster
 * recovery, shorter cooldowns) and fire pillars join the rotation.
 */
public class FurnaceKnight extends BossEntity {
	private static BossDefinition definition;

	public FurnaceKnight(EntityType<? extends FurnaceKnight> type, Level level) {
		super(type, level);
		// The grate is the weak point.
		addPart("helm", 0.9f, 0.8f, config().helmDamageMult, new Vec3(0, 2.85, 0.05));
	}

	public static FurnaceKnightConfig config() {
		return FurnaceKnights.CONFIG;
	}

	/** Built once, after the config loads. */
	static BossDefinition createDefinition() {
		FurnaceKnightConfig c = config();
		FurnaceKnightAttacks.Slam slam = new FurnaceKnightAttacks.Slam();
		FurnaceKnightAttacks.Sweep sweep = new FurnaceKnightAttacks.Sweep();
		FurnaceKnightAttacks.Breath breath = new FurnaceKnightAttacks.Breath();
		FurnaceKnightAttacks.Charge charge = new FurnaceKnightAttacks.Charge();
		FurnaceKnightAttacks.Pillars pillars = new FurnaceKnightAttacks.Pillars();
		ExpandingRing ring = new ExpandingRing("ring", c.ring, FurnaceKnightAttacks.THEME, "slam");
		LingeringPools pools = new LingeringPools("pools", c.pools, FurnaceKnightAttacks.THEME, "pillars");
		LeapSlam leap = new LeapSlam("leap", c.leap, FurnaceKnightAttacks.THEME, "charge");
		Feint feint = new Feint("feint", c.feint, slam, sweep);
		definition = BossDefinition.builder(CommonIds.FURNACE_KNIGHT)
				.health(c.health)
				.arenaRadius(c.arenaRadius)
				.music(FurnaceKnightSounds.MUSIC)
				// Blight core (world) and a legendary gun (guns), only when those modules are installed.
				.extraLoot(CommonIds.id(CommonIds.BOSSES, "boss_drops/furnace_knight_world"),
						CommonIds.id(CommonIds.BOSSES, "boss_drops/legendary_gun"))
				.phase(BossPhase.first().attacks(slam, sweep, breath, charge, ring, leap))
				.phase(BossPhase.below(c.phase2Threshold)
						.attacks(slam, sweep, breath, charge, pillars, ring, leap, pools, feint)
						.damage(c.phase2Damage)
						.recovery(c.phase2Recovery)
						.cooldowns(c.phase2Cooldowns)
						.speed(c.phase2Speed)
						.onEnter(boss -> boss.level().playSound(null, boss.blockPosition(), FurnaceKnightSounds.ROAR,
								net.minecraft.sounds.SoundSource.HOSTILE, 3.0f, 0.6f)))
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

	/** Phase 2: the cleaver burns (client and server). */
	public boolean bladeLit() {
		return phase() >= 1;
	}

	/** Sidesteps a held aim (v2 reactions). */
	@Override
	protected boolean sidesteps() {
		return true;
	}

	@Override
	protected double preferredDistance() {
		return 2.5;
	}

	@Override
	public void tick() {
		super.tick();
		if (level().isClientSide() && isAlive()) {
			embers();
		}
	}

	/** Embers drifting off the armor; more of them, and a burning blade, in phase 2. */
	private void embers() {
		int every = bladeLit() ? 2 : 4;
		if (tickCount % every == 0) {
			double a = random.nextDouble() * Math.PI * 2;
			level().addParticle(ParticleTypes.SMALL_FLAME, getX() + Math.cos(a) * 0.6, getY() + 1 + random.nextDouble() * 2, getZ() + Math.sin(a) * 0.6,
					0, 0.03 + random.nextDouble() * 0.03, 0);
		}
		if (bladeLit() && stage() != Stage.STAGGER) {
			// Roughly where the cleaver hangs at the right side.
			double yaw = Math.toRadians(getYRot());
			double rx = -Math.cos(yaw) * 1.1;
			double rz = -Math.sin(yaw) * 1.1;
			level().addParticle(ParticleTypes.FLAME, getX() + rx + (random.nextDouble() - 0.5) * 0.4, getY() + 0.8 + random.nextDouble() * 1.6,
					getZ() + rz + (random.nextDouble() - 0.5) * 0.4, 0, 0.02, 0);
		}
		// The grate glows from inside.
		if (tickCount % 6 == 0) {
			level().addParticle(ParticleTypes.LAVA, getX(), getY() + 3.0, getZ(), 0, 0, 0);
		}
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return FurnaceKnightSounds.AMBIENT;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return FurnaceKnightSounds.HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return FurnaceKnightSounds.DEATH;
	}

	@Override
	protected void playStepSound(BlockPos pos, BlockState state) {
		playSound(FurnaceKnightSounds.STEP, 0.8f, 0.8f);
	}

	@Override
	protected SoundEvent staggerSound() {
		return FurnaceKnightSounds.ROAR;
	}

	@Override
	protected float getSoundVolume() {
		return 1.6f;
	}

	@Override
	protected void onPhaseEnter(int index) {
		if (index == 1 && level() instanceof ServerLevel level) {
			level.sendParticles(ParticleTypes.FLAME, getX(), getY() + 1.5, getZ(), 60, 0.8, 1.2, 0.8, 0.08);
		}
	}
}
