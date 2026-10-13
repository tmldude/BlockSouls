package io.github.tmldude.blocksouls.bosses.riftwarden;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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
import io.github.tmldude.blocksouls.bosses.api.Telegraphs;
import io.github.tmldude.blocksouls.bosses.archetype.Archetype;
import io.github.tmldude.blocksouls.bosses.archetype.ChargeStick;
import io.github.tmldude.blocksouls.bosses.archetype.ExpandingRing;
import io.github.tmldude.blocksouls.bosses.archetype.Feint;
import io.github.tmldude.blocksouls.bosses.archetype.HomingOrbs;
import io.github.tmldude.blocksouls.bosses.archetype.LeapSlam;
import io.github.tmldude.blocksouls.bosses.archetype.LineCross;
import io.github.tmldude.blocksouls.bosses.archetype.RotatingBeam;
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

	/** Open fissure circles (server), erupting when {@link #fissureTicks} reaches the windup. */
	private final List<Vec3> fissures = new ArrayList<>();
	private int fissureTicks;
	private int fissureCooldown;
	/** Rushes started this fight (1-based once P3 begins); 0 before the first. */
	private int rushes;
	private int rushTicksLeft;
	private float rushDealt;
	/** The last timed rush failed: no timer, no healing, harder hits until it falls. */
	private boolean unbound;

	public Riftwarden(EntityType<? extends Riftwarden> type, Level level) {
		super(type, level);
		// The rift core floating before the chest (RiftwardenModel "core"): a weak point; breaking it costs the beam.
		addPart("core", 0.9f, 0.9f, config().coreDamageMultiplier, new Vec3(0, 2.6, 0.8));
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
		RiftSummon summon = new RiftSummon("summon", c.summon, THEME, "cast");
		Feint feint = new Feint("feint", c.feint, ring, leap);
		BossDefinition.Builder builder = BossDefinition.builder(CommonIds.RIFTWARDEN)
				.health(c.health)
				.arenaRadius(c.arenaRadius)
				.breakable("core", c.coreBreakShare, "beam", c.coreSpeedBonus)
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
		} else if (level() instanceof ServerLevel level && isAlive() && isEngaged() && !isReturning() && transformed()) {
			// Here, not fightTick: that only runs between attacks.
			fissureTick(level);
			if (rushing()) {
				rushTick(level);
			}
		}
	}

	@Override
	public double damageMultiplier() {
		return unbound ? super.damageMultiplier() * config().unboundDamage : super.damageMultiplier();
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		float before = getHealth();
		boolean hurt = super.hurtServer(level, source, damage);
		if (hurt && rushing() && rushTicksLeft > 0 && isAlive()) {
			rushDealt += Math.max(0, before - getHealth());
			if (RiftMath.rushWon(rushDealt, getMaxHealth(), config().rushFraction)) {
				// The rush landed: the Warden falls now (the kill event ends the run).
				setHealth(0);
				die(source);
			}
		}
		return hurt;
	}

	/** Seconds left in the current rush (0 when none is running). */
	public int rushSecondsLeft() {
		return (rushTicksLeft + 19) / 20;
	}

	/** Damage dealt this rush as a share of what wins it (0..1). */
	public double rushProgress() {
		double goal = getMaxHealth() * config().rushFraction;
		return goal > 0 ? Math.min(1, rushDealt / goal) : 1;
	}

	/** Debug and tests: the running rush ends in {@code ticks} (no-op when none runs). */
	public void shortenRush(int ticks) {
		if (rushTicksLeft > 0) {
			rushTicksLeft = Math.max(1, Math.min(rushTicksLeft, ticks));
		}
	}

	public int rushNumber() {
		return rushes;
	}

	public boolean isUnbound() {
		return unbound;
	}

	private void startRush(ServerLevel level) {
		RiftwardenConfig c = config();
		if (unbound) {
			return;
		}
		rushes++;
		rushDealt = 0;
		rushTicksLeft = RiftMath.rushTicks(c.rushSeconds, Math.max(1, participants().size()), rushes, c.rushShrink, c.minRushSeconds);
		level.playSound(null, blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.HOSTILE, 3.0f, 0.5f);
		tell(Component.translatable("boss.blocksouls_bosses.riftwarden.rush", rushSecondsLeft(),
				(int) Math.round(c.rushFraction * 100)));
	}

	/** The rush clock: paused while staggered or shielded (no damage can land then), fails at zero. */
	private void rushTick(ServerLevel level) {
		if (rushTicksLeft <= 0 || isStaggered() || isShielded()) {
			return;
		}
		rushTicksLeft--;
		if (tickCount % 10 == 0) {
			Component bar = Component.translatable("boss.blocksouls_bosses.riftwarden.rush_bar", rushSecondsLeft(),
					(int) Math.floor(rushProgress() * 100));
			for (ServerPlayer player : participants()) {
				player.sendOverlayMessage(bar);
			}
		}
		if (rushTicksLeft == 20 * 5) {
			level.playSound(null, blockPosition(), SoundEvents.BELL_RESONATE, SoundSource.HOSTILE, 3.0f, 0.6f);
		}
		if (rushTicksLeft > 0) {
			return;
		}
		RiftwardenConfig c = config();
		level.playSound(null, blockPosition(), SoundEvents.BEACON_DEACTIVATE, SoundSource.HOSTILE, 3.0f, 0.5f);
		if (RiftMath.lastRush(rushes, c.maxRushes)) {
			unbound = true;
			tell(Component.translatable("boss.blocksouls_bosses.riftwarden.unbound"));
			return;
		}
		// Failed: the rift mends it to just under the phase-2 line and phase 2 resumes.
		double share = Math.clamp(c.rushFailHealShare, c.phase3Threshold + 0.01, c.phase2Threshold - 0.001);
		setHealth((float) Math.max(getHealth(), share * getMaxHealth()));
		tell(Component.translatable("boss.blocksouls_bosses.riftwarden.mended"));
		forcePhase(1);
	}

	private void tell(Component message) {
		for (ServerPlayer player : participants()) {
			player.sendSystemMessage(message);
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
			if (index >= 2) {
				startRush(level);
			}
		}
	}

	@Override
	protected void onReset() {
		fissures.clear();
		fissureCooldown = 0;
		rushes = 0;
		rushTicksLeft = 0;
		rushDealt = 0;
		unbound = false;
	}

	/**
	 * Rift fissures: circles open under (near) the players, fill over the windup, then erupt. Only whoever stands
	 * inside a drawn circle at the eruption is hurt, once, however many circles overlap.
	 */
	private void fissureTick(ServerLevel level) {
		RiftwardenConfig c = config();
		if (fissures.isEmpty()) {
			if (fissureCooldown-- > 0) {
				return;
			}
			List<ServerPlayer> players = participants().stream().filter(this::canHarm).toList();
			if (players.isEmpty()) {
				return;
			}
			int n = RiftMath.fissures(c.fissures, players.size(), c.maxFissures);
			for (int i = 0; i < n; i++) {
				ServerPlayer anchor = players.get(i % players.size());
				double a = random.nextDouble() * Math.PI * 2;
				double r = i < players.size() ? random.nextDouble() * 1.5 : random.nextDouble() * c.fissureSpread;
				fissures.add(Archetype.ground(level, clampToArena(anchor.position().add(Math.cos(a) * r, 0, Math.sin(a) * r))));
			}
			fissureTicks = 0;
			level.playSound(null, blockPosition(), SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.HOSTILE, 2.0f, 0.6f);
			return;
		}
		fissureTicks++;
		float progress = Math.min(1f, fissureTicks / (float) Math.max(1, c.fissureWindupTicks));
		if (fissureTicks % 3 == 0) {
			for (Vec3 at : fissures) {
				Telegraphs.circleFilling(level, OUTLINE, FILL, at, c.fissureRadius, progress);
			}
		}
		if (fissureTicks < c.fissureWindupTicks) {
			return;
		}
		for (ServerPlayer player : participants()) {
			if (canHarm(player) && inFissure(player.position(), c.fissureRadius)) {
				player.hurtServer(level, damageSources().mobAttack(this), (float) (c.fissureDamage * damageMultiplier()));
			}
		}
		for (Vec3 at : fissures) {
			Telegraphs.column(level, ParticleTypes.REVERSE_PORTAL, at, 3, 0.4, 6);
			level.playSound(null, at.x, at.y, at.z, SoundEvents.END_PORTAL_FRAME_FILL, SoundSource.HOSTILE, 1.5f, 0.6f);
		}
		fissures.clear();
		fissureCooldown = c.fissureIntervalTicks;
	}

	private boolean inFissure(Vec3 pos, double radius) {
		for (Vec3 at : fissures) {
			double dx = pos.x - at.x;
			double dz = pos.z - at.z;
			if (dx * dx + dz * dz <= radius * radius && Math.abs(pos.y - at.y) < 2) {
				return true;
			}
		}
		return false;
	}

	/** Keeps a point inside the fog wall (one block in). */
	private Vec3 clampToArena(Vec3 point) {
		Vec3 home = Vec3.atBottomCenterOf(home());
		Vec3 d = new Vec3(point.x - home.x, 0, point.z - home.z);
		double max = Math.max(1, arenaRadius() - 1.5);
		return d.length() <= max ? point : new Vec3(home.x + d.x / d.length() * max, point.y, home.z + d.z / d.length() * max);
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
