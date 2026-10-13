package io.github.tmldude.blocksouls.bosses.riftwarden;

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

import io.github.tmldude.blocksouls.bosses.api.BossDefinition;
import io.github.tmldude.blocksouls.bosses.api.BossEntity;
import io.github.tmldude.blocksouls.bosses.api.BossPhase;
import io.github.tmldude.blocksouls.bosses.archetype.ChargeStick;
import io.github.tmldude.blocksouls.bosses.archetype.ExpandingRing;
import io.github.tmldude.blocksouls.bosses.archetype.Feint;
import io.github.tmldude.blocksouls.bosses.archetype.HomingOrbs;
import io.github.tmldude.blocksouls.bosses.archetype.LeapSlam;
import io.github.tmldude.blocksouls.bosses.archetype.LineCross;
import io.github.tmldude.blocksouls.bosses.archetype.RotatingBeam;
import io.github.tmldude.blocksouls.bosses.archetype.SummonShield;
import io.github.tmldude.blocksouls.bosses.archetype.Theme;
import io.github.tmldude.blocksouls.common.CommonIds;

/**
 * The Riftwarden, floor 6's final boss: a tall robed sentinel in cracked obsidian plate, a rift glowing through its
 * chest. It waits at the centre of the final arena until the party steps on the trigger plate ({@link RiftArena}).
 * Phase 1: ring, fault lines, leap, charge. Phase 2 (below {@code phase2Threshold}): the rift tears it open
 * (transformed): beam, seeking motes and adds from floor 5's roster join. Phase 3 (below {@code phase3Threshold}):
 * the damage rush. Its death ends a floors run (game listens on {@code BossEvents.KILLED}).
 */
public class Riftwarden extends BossEntity {
	/** Violet telegraph outline, teal fill as the windup runs out. */
	static final ParticleOptions OUTLINE = new DustParticleOptions(0x9B4DFF, 1.6f);
	static final ParticleOptions FILL = new DustParticleOptions(0x4DFFE1, 1.2f);
	static final Theme THEME = new Theme(OUTLINE, FILL, ParticleTypes.REVERSE_PORTAL, SoundEvents.EVOKER_PREPARE_ATTACK,
			SoundEvents.END_PORTAL_SPAWN);
	private static BossDefinition definition;

	public Riftwarden(EntityType<? extends Riftwarden> type, Level level) {
		super(type, level);
	}

	public static RiftwardenConfig config() {
		return Riftwardens.CONFIG;
	}

	/** Built once, after the config loads. */
	static BossDefinition createDefinition() {
		RiftwardenConfig c = config();
		ExpandingRing ring = new ExpandingRing("ring", c.ring, THEME, "slam");
		LineCross cross = new LineCross("cross", c.cross, THEME, "cast");
		LeapSlam leap = new LeapSlam("leap", c.leap, THEME, "leap");
		ChargeStick charge = new ChargeStick("charge", c.charge, THEME, "charge");
		RotatingBeam beam = new RotatingBeam("beam", c.beam, THEME, "cast");
		HomingOrbs orbs = new HomingOrbs("orbs", c.orbs, THEME, "cast");
		SummonShield summon = new SummonShield("summon", c.summon, THEME, "cast");
		Feint feint = new Feint("feint", c.feint, ring, leap);
		BossDefinition.Builder builder = BossDefinition.builder(CommonIds.RIFTWARDEN)
				.health(c.health)
				.arenaRadius(c.arenaRadius)
				.phase(BossPhase.first().attacks(ring, cross, leap, charge))
				.phase(BossPhase.below(c.phase2Threshold)
						.attacks(ring, cross, leap, charge, beam, orbs, summon, feint)
						.damage(c.phase2Damage)
						.recovery(c.phase2Recovery)
						.cooldowns(c.phase2Cooldowns)
						.speed(c.phase2Speed)
						.onEnter(boss -> boss.level().playSound(null, boss.blockPosition(), SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE,
								2.5f, 0.7f)))
				.phase(BossPhase.below(c.phase3Threshold)
						.attacks(ring, cross)
						.damage(c.phase3Damage)
						.recovery(c.phase3Recovery)
						.cooldowns(c.phase3Cooldowns)
						.speed(c.phase3Speed)
						.onEnter(boss -> boss.level().playSound(null, boss.blockPosition(), SoundEvents.ENDER_DRAGON_GROWL, SoundSource.HOSTILE,
								2.5f, 0.6f)));
		if (c.poise > 0) {
			builder.poise(c.poise);
		}
		definition = builder.build();
		return definition;
	}

	public static BossDefinition boss() {
		return definition;
	}

	@Override
	public BossDefinition definition() {
		return definition;
	}

	/** Phase 2 and on: the rift has torn it open (client and server). */
	public boolean transformed() {
		return phase() >= 1;
	}

	/** Phase 3: the damage rush (client and server). */
	public boolean rushing() {
		return phase() >= 2;
	}

	@Override
	protected boolean sidesteps() {
		return true;
	}

	@Override
	protected double preferredDistance() {
		return 3.0;
	}

	@Override
	public void tick() {
		super.tick();
		if (level().isClientSide() && isAlive()) {
			motes();
		}
	}

	/** Rift motes drifting off the chest; a torrent once transformed. */
	private void motes() {
		int every = transformed() ? 1 : 3;
		if (tickCount % every == 0) {
			double a = random.nextDouble() * Math.PI * 2;
			level().addParticle(ParticleTypes.REVERSE_PORTAL, getX() + Math.cos(a) * 0.7, getY() + 1.5 + random.nextDouble() * 2,
					getZ() + Math.sin(a) * 0.7, 0, 0.02, 0);
		}
		if (rushing() && tickCount % 2 == 0) {
			level().addParticle(ParticleTypes.END_ROD, getX(), getY() + getBbHeight() * 0.6, getZ(), (random.nextDouble() - 0.5) * 0.2,
					0.05, (random.nextDouble() - 0.5) * 0.2);
		}
	}

	@Override
	protected void onPhaseEnter(int index) {
		if (level() instanceof ServerLevel level) {
			level.sendParticles(index >= 2 ? ParticleTypes.END_ROD : ParticleTypes.REVERSE_PORTAL, getX(), getY() + getBbHeight() / 2, getZ(),
					80, 1.0, 1.4, 1.0, 0.12);
		}
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return SoundEvents.WITHER_AMBIENT;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.AMETHYST_BLOCK_RESONATE;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.BEACON_DEACTIVATE;
	}

	@Override
	protected void playStepSound(BlockPos pos, BlockState state) {
		playSound(SoundEvents.IRON_GOLEM_STEP, 0.9f, 0.6f);
	}

	@Override
	protected SoundEvent staggerSound() {
		return SoundEvents.ELDER_GUARDIAN_CURSE;
	}

	@Override
	protected float getSoundVolume() {
		return 1.8f;
	}
}
