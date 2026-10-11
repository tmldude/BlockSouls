package io.github.tmldude.blocksouls.bosses.raid;

import java.util.List;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

import io.github.tmldude.blocksouls.bosses.api.BossDefinition;
import io.github.tmldude.blocksouls.bosses.api.BossEntity;
import io.github.tmldude.blocksouls.bosses.api.BossPart;
import io.github.tmldude.blocksouls.bosses.api.BossPhase;
import io.github.tmldude.blocksouls.bosses.raid.logic.CrystalOrder;
import io.github.tmldude.blocksouls.bosses.raid.logic.RaidMath;
import io.github.tmldude.blocksouls.common.CommonIds;
import io.github.tmldude.blocksouls.common.player.PlayerStatus;

/**
 * The final raid: a gaunt void-black dragon that replaces the Ender Dragon. Three phases on the main island:
 * <ol>
 * <li><b>The Crystal Order</b> (100–70%): shielded while any crystal lives; the obelisk shows the order to break them
 * in ({@link CrystalPuzzle}). It strafes: breath lines and dives.</li>
 * <li><b>Grounding</b> (70–30%): circles out of reach (hits barely land). Held plates ({@link PlateGate}) chain it to
 * its perch for a DPS window: ×1.5 damage, ×2 more on the head. Perched it sweeps its tail, gusts its wings toward the
 * void edge and spits void orbs.</li>
 * <li><b>Fall</b> (30–0%): faster and harder; meteor showers and breath sweeps across the island; perches more often
 * but for shorter. Soft enrage after 12 minutes: +50% damage.</li>
 * </ol>
 * A wipe heals it to the start of the current phase and resets that phase's mechanic. Its death opens the exit portal
 * as vanilla's would ({@link FinalRaidEnd}) and fires {@code BossEvents.KILLED} with {@link CommonIds#FINAL_BOSS}.
 */
public class FinalDragon extends BossEntity {
	/** Flight state (synced: the model folds the wings when perched). */
	public enum Mode {
		FLYING, PULLED, PERCHED, TAKEOFF;

		static Mode byId(int id) {
			Mode[] values = values();
			return values[Math.clamp(id, 0, values.length - 1)];
		}
	}

	private static final EntityDataAccessor<Byte> DATA_MODE = SynchedEntityData.defineId(FinalDragon.class, EntityDataSerializers.BYTE);
	private static final EntityDataAccessor<Boolean> DATA_SHIELDED = SynchedEntityData.defineId(FinalDragon.class, EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<Boolean> DATA_ENRAGED = SynchedEntityData.defineId(FinalDragon.class, EntityDataSerializers.BOOLEAN);
	private static final DustParticleOptions SHIELD = new DustParticleOptions(0xB98CFF, 1.8f);
	private static final DustParticleOptions CRACKS = new DustParticleOptions(0x9B4DFF, 1.4f);

	private static BossDefinition definition;

	private @Nullable RaidArena arena;
	private BlockPos arenaOrigin = BlockPos.ZERO;
	private boolean endArena;
	private final CrystalPuzzle puzzle = new CrystalPuzzle(this);
	private final PlateGate plates = new PlateGate(this);
	private final VoidOrbs orbs = new VoidOrbs(this);
	private @Nullable List<PlateGate.Replaced> savedPlates;
	private int modeTicks;
	private Vec3 perchFacing = new Vec3(0, 0, 1);
	private int fightTicks;
	private double orbitAngle;
	private int lastDeflectTick;

	public FinalDragon(EntityType<? extends FinalDragon> type, Level level) {
		super(type, level);
		this.noPhysics = true;
		this.xpReward = config().experience;
		setNoGravity(true);
		// The head: always hittable, a weak point while perched.
		addPart("head", 2.2f, 2.0f, config().headDamageMult, new Vec3(0, 2.6, 5.8));
	}

	public static FinalRaidConfig config() {
		return FinalRaids.CONFIG;
	}

	/** Built once, after the config loads. */
	static BossDefinition createDefinition() {
		FinalRaidConfig c = config();
		var breathLine = new FinalDragonAttacks.BreathLine("breath_line", c.breathLine, () -> c.breathLineLength, () -> c.breathLineHalfWidth);
		var dive = new FinalDragonAttacks.Dive();
		var orbs = new FinalDragonAttacks.VoidOrbBarrage();
		var tail = new FinalDragonAttacks.TailSweep();
		var gust = new FinalDragonAttacks.WingGust();
		var meteors = new FinalDragonAttacks.MeteorShower();
		var sweep = new FinalDragonAttacks.BreathLine("breath_sweep", c.breathSweep, () -> c.breathSweepLength, () -> c.breathSweepHalfWidth);
		definition = BossDefinition.builder(CommonIds.FINAL_BOSS)
				.health(c.health)
				.arenaRadius(c.arenaRadius)
				.music(FinalRaidSounds.MUSIC)
				// Two legendary guns + ammo (guns), only when that module is installed.
				.extraLoot(CommonIds.id(CommonIds.BOSSES, "boss_drops/final_dragon_guns"))
				.phase(BossPhase.first().attacks(breathLine, dive))
				.phase(BossPhase.below(c.phase2Threshold).attacks(breathLine, dive, orbs, tail, gust))
				.phase(BossPhase.below(c.phase3Threshold)
						.attacks(breathLine, dive, orbs, tail, gust, meteors, sweep)
						.damage(c.phase3Damage)
						.cooldowns(c.phase3Cooldowns)
						.recovery(c.phase3Recovery)
						.speed(c.phase3Speed))
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

	// ---------------------------------------------------------------------------------------------------------
	// State

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_MODE, (byte) Mode.FLYING.ordinal());
		builder.define(DATA_SHIELDED, false);
		builder.define(DATA_ENRAGED, false);
	}

	public Mode mode() {
		return Mode.byId(entityData.get(DATA_MODE));
	}

	private void setMode(Mode mode) {
		entityData.set(DATA_MODE, (byte) mode.ordinal());
		modeTicks = 0;
	}

	/** The crystal shield is up (both sides). */
	public boolean shielded() {
		return entityData.get(DATA_SHIELDED);
	}

	/** Soft enrage (both sides). */
	public boolean enraged() {
		return entityData.get(DATA_ENRAGED);
	}

	public int fightTicks() {
		return fightTicks;
	}

	/** Debug: jump the fight clock (e.g. to the soft enrage). */
	public void setFightTicks(int ticks) {
		this.fightTicks = ticks;
	}

	public RaidArena arena() {
		if (arena == null) {
			if (level() instanceof ServerLevel level && level.getDragonFight() != null) {
				arena = RaidArena.fromEnd(level, arenaOrigin);
				endArena = true;
			} else {
				arena = FinalRaidEnd.arenaAround(home());
			}
		}
		return arena;
	}

	/** Places this fight in {@code arena} (the End's island, or a scripted one in tests). */
	public void setArena(RaidArena arena, boolean end) {
		this.arena = arena;
		this.endArena = end;
		this.arenaOrigin = arena.center();
		setHome(BlockPos.containing(arena.orbit(0, 0, config().cruiseHeight)));
	}

	CrystalPuzzle puzzle() {
		return puzzle;
	}

	PlateGate plates() {
		return plates;
	}

	VoidOrbs orbs() {
		return orbs;
	}

	/** The Crystal Order in play (null outside phase 1). Read-only use: tests, debug. */
	public @Nullable CrystalOrder crystalOrder() {
		return puzzle.order();
	}

	/** Crystals of the order standing right now. */
	public List<RaidCrystal> standingCrystals() {
		return level() instanceof ServerLevel level ? puzzle.standing(level) : List.of();
	}

	/** Centers of the plates set into the island (block positions of the plate surface). */
	public List<BlockPos> plateCenters() {
		return List.copyOf(plates.plates());
	}

	Vec3 perchFacing() {
		return perchFacing;
	}

	/** Players in the fight right now (alive, not downed, in the arena), at least 1. */
	int playersInFight() {
		int count = 0;
		for (ServerPlayer player : participants()) {
			if (inFight(player)) {
				count++;
			}
		}
		return Math.max(1, count);
	}

	/** Where its mouth is (attacks spawn from here). */
	Vec3 mouth() {
		Vec3 look = Vec3.directionFromRotation(0, getYRot());
		return position().add(look.scale(6.2)).add(0, 2.2, 0);
	}

	// ---------------------------------------------------------------------------------------------------------
	// Arena: the whole island, not a fog-walled ring

	@Override
	protected boolean raisesFogWall() {
		return false;
	}

	@Override
	public boolean inArena(Entity entity) {
		if (entity.level() != level()) {
			return false;
		}
		RaidArena a = arena();
		double dx = entity.getX() - (a.center().getX() + 0.5);
		double dz = entity.getZ() - (a.center().getZ() + 0.5);
		double r = config().arenaRadius;
		double dy = entity.getY() - a.floorY();
		return dx * dx + dz * dz <= r * r && dy >= -config().arenaBelow && dy <= config().arenaAbove;
	}

	@Override
	protected double preferredDistance() {
		// It flies; the framework's ground navigation never runs.
		return 1000;
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	public void travel(Vec3 input) {
		// Moves exactly by the velocity the flight code set: no gravity, no friction, through pillars.
		move(MoverType.SELF, getDeltaMovement());
	}

	// ---------------------------------------------------------------------------------------------------------
	// Ticking

	@Override
	public void tick() {
		super.tick();
		if (level().isClientSide() && isAlive()) {
			clientEffects();
		}
	}

	private void clientEffects() {
		// Violet light leaking from the cracks; a shell of sparks while shielded.
		if (tickCount % 3 == 0) {
			level().addParticle(ParticleTypes.REVERSE_PORTAL, getX() + (random.nextDouble() - 0.5) * 3, getY() + 1 + random.nextDouble() * 2,
					getZ() + (random.nextDouble() - 0.5) * 3, 0, 0.02, 0);
		}
		if (enraged() && tickCount % 2 == 0) {
			level().addParticle(ParticleTypes.SOUL_FIRE_FLAME, getX() + (random.nextDouble() - 0.5) * 3, getY() + 1 + random.nextDouble() * 2,
					getZ() + (random.nextDouble() - 0.5) * 3, 0, 0.05, 0);
		}
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		if (savedPlates != null) {
			plates.restore(level, savedPlates);
			savedPlates = null;
		}
		if (isDeadOrDying()) {
			return;
		}
		if (!isEngaged()) {
			if (endArena && tickCount % 20 == 0) {
				FinalRaidEnd.seen(level, this);
			}
			idleFlight(level);
			if (tickCount % 10 == 0) {
				for (ServerPlayer player : level.players()) {
					if (!player.isSpectator() && !player.isCreative() && player.isAlive() && !PlayerStatus.isDowned(player) && inArena(player)) {
						engage(player);
						break;
					}
				}
			}
			return;
		}
		fightTicks++;
		modeTicks++;
		boolean enraged = RaidMath.enraged(fightTicks, config().softEnrageTicks);
		if (enraged && !enraged()) {
			entityData.set(DATA_ENRAGED, true);
			level.playSound(null, getX(), getY(), getZ(), FinalRaidSounds.ENRAGE, SoundSource.HOSTILE, 6f, 0.6f);
			level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, true, true, getX(), getY() + 2, getZ(), 120, 2.5, 1.5, 2.5, 0.15);
		}
		entityData.set(DATA_SHIELDED, puzzle.shielded());

		puzzle.tick(level);
		orbs.tick(level);
		boolean flyingFree = mode() == Mode.FLYING && !isStaggered();
		if (plates.tick(level, playersInFight(), flyingFree && phase() >= 1)) {
			startPull(level);
		}
		modeTick(level);
		if (tickCount % 5 == 0) {
			FinalRaidEnd.pacifyEndermen(level, this);
		}
		if (endArena && tickCount % 20 == 0) {
			FinalRaidEnd.seen(level, this);
		}
		if (shielded() && tickCount % 4 == 0) {
			shieldShell(level);
		}
	}

	/** Not fighting: circle home slowly (and fly back there after a reset). */
	private void idleFlight(ServerLevel level) {
		Vec3 home = Vec3.atBottomCenterOf(home());
		double far = arena().scaled(40);
		if (isReturning() || distanceToSqr(home) > far * far) {
			steer(home, 1.2, 0.3);
		} else {
			orbitAngle += 0.012;
			double r = arena().scaled(12);
			steer(home.add(Math.cos(orbitAngle) * r, Math.sin(tickCount * 0.03) * 2, Math.sin(orbitAngle) * r), 0.4, 0.15);
		}
		faceVelocity();
		flapSound(level);
	}

	private void modeTick(ServerLevel level) {
		switch (mode()) {
			case FLYING -> {
				if (isStaggered()) {
					// The phase roar: hang in the air.
					setDeltaMovement(getDeltaMovement().scale(0.8));
				}
				// Otherwise fightTick (between attacks) or the attack moves it.
			}
			case PULLED -> {
				Vec3 perch = arena().perch();
				int left = Math.max(1, config().pullTicks - modeTicks);
				Vec3 delta = perch.subtract(position());
				setDeltaMovement(delta.scale(1.0 / left));
				face(perchFacing);
				setXRot(25);
				plates.chains(level, position().add(0, 1.5, 0));
				if (modeTicks % 10 == 0) {
					playSound(FinalRaidSounds.CHAINS, 4f, 0.6f + modeTicks / (float) config().pullTicks * 0.4f);
				}
				if (modeTicks >= config().pullTicks) {
					land(level);
				}
			}
			case PERCHED -> {
				setDeltaMovement(Vec3.ZERO);
				setPos(arena().perch());
				face(perchFacing);
				setXRot(0);
				if (isStaggered()) {
					// The roar doesn't eat the DPS window.
					modeTicks--;
				}
				if (modeTicks % 8 == 0) {
					// Vulnerable: light spills out of the cracks.
					level.sendParticles(CRACKS, true, true, getX(), getY() + 2, getZ(), 12, 2.2, 1.0, 2.2, 0);
				}
				int perch = phase() >= 2 ? config().perchTicksPhase3 : config().perchTicks;
				if (modeTicks >= perch) {
					takeOff(level);
				}
			}
			case TAKEOFF -> {
				Vec3 up = arena().orbit(orbitAngle, config().highRadius * 0.5, config().highHeight * 0.7);
				steer(up, 1.0, 0.2);
				faceVelocity();
				flapSound(level);
				if (modeTicks >= config().takeoffTicks) {
					setMode(Mode.FLYING);
				}
			}
		}
	}

	@Override
	protected void fightTick(ServerLevel level, @Nullable LivingEntity target) {
		if (mode() != Mode.FLYING) {
			return;
		}
		// Circle the island: close and low in P1 (strafing), high and wide in P2/P3 (out of reach).
		boolean high = phase() >= 1;
		double radius = high ? config().highRadius : config().cruiseRadius;
		double height = high ? config().highHeight : config().cruiseHeight;
		double speed = config().cruiseSpeed * definition().phase(phase()).speedMult();
		orbitAngle += speed / arena().scaled(radius);
		Vec3 point = arena().orbit(orbitAngle, radius, height + Math.sin(tickCount * 0.05) * 3);
		steer(point, speed * 1.6, 0.12);
		faceVelocity();
		flapSound(level);
	}

	/**
	 * Steers toward {@code to}: the velocity eases ({@code ease} per tick) toward a straight line there at up to
	 * {@code speed} blocks per tick.
	 */
	void steer(Vec3 to, double speed, double ease) {
		Vec3 delta = to.subtract(position());
		double length = delta.length();
		Vec3 want = length < 1e-3 ? Vec3.ZERO : delta.scale(Math.min(speed, length) / length);
		Vec3 velocity = getDeltaMovement();
		setDeltaMovement(velocity.add(want.subtract(velocity).scale(ease)));
	}

	void faceVelocity() {
		Vec3 v = getDeltaMovement();
		if (v.x * v.x + v.z * v.z > 0.0025) {
			face(v);
		}
		setXRot((float) Math.clamp(-Math.toDegrees(Math.atan2(v.y, Math.max(0.05, v.horizontalDistance()))), -40, 40));
	}

	private void flapSound(ServerLevel level) {
		if (tickCount % 22 == 0) {
			level.playSound(null, getX(), getY(), getZ(), FinalRaidSounds.FLAP, SoundSource.HOSTILE, 3f, 0.7f + random.nextFloat() * 0.2f);
		}
	}

	private void shieldShell(ServerLevel level) {
		double r = 3.5;
		for (int i = 0; i < 10; i++) {
			double a = random.nextDouble() * Math.PI * 2;
			double b = Math.acos(random.nextDouble() * 2 - 1);
			level.sendParticles(SHIELD, true, true, getX() + Math.sin(b) * Math.cos(a) * r, getY() + 1.5 + Math.cos(b) * r * 0.7,
					getZ() + Math.sin(b) * Math.sin(a) * r, 1, 0, 0, 0, 0);
		}
	}

	// ---------------------------------------------------------------------------------------------------------
	// Grounding: pull, perch, take off

	/** The plates were held: chains drag it to the perch. */
	void startPull(ServerLevel level) {
		cancelAttack();
		Vec3 from = position().subtract(arena().perch());
		Vec3 flat = new Vec3(from.x, 0, from.z);
		// It lands facing the way it was dragged in from, head toward the island.
		perchFacing = flat.lengthSqr() < 1e-3 ? new Vec3(0, 0, 1) : flat.normalize().scale(-1);
		setMode(Mode.PULLED);
		level.playSound(null, getX(), getY(), getZ(), FinalRaidSounds.ROAR, SoundSource.HOSTILE, 6f, 1.3f);
		Vec3 c = arena().centerVec();
		level.playSound(null, c.x, c.y, c.z, FinalRaidSounds.CHAINS, SoundSource.HOSTILE, 6f, 0.5f);
	}

	private void land(ServerLevel level) {
		setMode(Mode.PERCHED);
		setPos(arena().perch());
		setDeltaMovement(Vec3.ZERO);
		level.playSound(null, getX(), getY(), getZ(), FinalRaidSounds.LAND, SoundSource.HOSTILE, 5f, 0.6f);
		level.sendParticles(ParticleTypes.EXPLOSION, true, true, getX(), getY() + 0.5, getZ(), 6, 3, 0.3, 3, 0);
		level.sendParticles(ParticleTypes.END_ROD, true, true, getX(), getY() + 2, getZ(), 60, 2.5, 1.5, 2.5, 0.2);
	}

	private void takeOff(ServerLevel level) {
		cancelAttack();
		setMode(Mode.TAKEOFF);
		plates.disarm(phase() >= 2 ? config().plateRearmTicksPhase3 : config().plateRearmTicks);
		level.playSound(null, getX(), getY(), getZ(), FinalRaidSounds.ROAR, SoundSource.HOSTILE, 5f, 0.9f);
		level.sendParticles(ParticleTypes.CLOUD, true, true, getX(), getY() + 0.5, getZ(), 40, 4, 0.2, 4, 0.1);
	}

	/** Debug: pull it down now (as if the plates were held). */
	public void forcePerch() {
		if (level() instanceof ServerLevel level) {
			startPull(level);
		}
	}

	// ---------------------------------------------------------------------------------------------------------
	// Crystals

	void onCrystalBroken(RaidCrystal crystal, ServerPlayer player) {
		if (level() instanceof ServerLevel level) {
			if (!isEngaged()) {
				engage(player);
			}
			puzzle.onBroken(level, crystal, player);
		}
	}

	/** A wrong crystal: a stream of violet from it to the dragon, and it heals {@code fraction} of max health. */
	void drainFrom(ServerLevel level, Vec3 from, double fraction) {
		Vec3 to = position().add(0, 1.5, 0);
		Vec3 delta = to.subtract(from);
		int points = (int) Math.min(100, delta.length());
		for (int i = 0; i <= points; i++) {
			Vec3 p = from.add(delta.scale(i / (double) Math.max(1, points)));
			level.sendParticles(CRACKS, true, true, p.x, p.y, p.z, 2, 0.1, 0.1, 0.1, 0);
		}
		level.sendParticles(ParticleTypes.HEART, true, true, to.x, to.y + 2, to.z, 6, 1.5, 0.5, 1.5, 0);
		setHealth((float) RaidMath.heal(getHealth(), getMaxHealth(), fraction));
	}

	/** The last crystal of the order broke. */
	void onShieldBroken(ServerLevel level) {
		entityData.set(DATA_SHIELDED, false);
		level.playSound(null, getX(), getY(), getZ(), FinalRaidSounds.SHIELD_BREAK, SoundSource.HOSTILE, 8f, 0.8f);
		level.playSound(null, getX(), getY(), getZ(), FinalRaidSounds.ROAR, SoundSource.HOSTILE, 6f, 0.7f);
		level.sendParticles(ParticleTypes.END_ROD, true, true, getX(), getY() + 1.5, getZ(), 150, 3, 2, 3, 0.4);
		level.sendParticles(ColorParticleOption.create(ParticleTypes.FLASH, 0xFFF2ECFF), true, true, getX(), getY() + 1.5, getZ(), 1, 0, 0, 0, 0);
	}

	/** Debug: complete the Crystal Order. */
	public void skipCrystals() {
		if (level() instanceof ServerLevel level) {
			puzzle.skip(level);
			onShieldBroken(level);
		}
	}

	// ---------------------------------------------------------------------------------------------------------
	// Engage, phases, wipe

	@Override
	protected void onEngage() {
		if (!(level() instanceof ServerLevel level)) {
			return;
		}
		fightTicks = 0;
		entityData.set(DATA_ENRAGED, false);
		setMode(Mode.FLYING);
		setupPhase(level, phase());
		level.playSound(null, getX(), getY(), getZ(), FinalRaidSounds.ROAR, SoundSource.HOSTILE, 8f, 0.6f);
	}

	@Override
	protected void onPhaseEnter(int index) {
		if (level() instanceof ServerLevel level && isEngaged()) {
			setupPhase(level, index);
		}
	}

	/** What a phase needs on the island: crystals for P1, plates from P2. */
	private void setupPhase(ServerLevel level, int index) {
		if (index == 0) {
			plates.remove(level);
			puzzle.setup(level, playersInFight());
		} else {
			puzzle.clear(level);
			if (!plates.placed()) {
				plates.place(level, playersInFight());
			}
			if (index >= 2) {
				// The fall: it shakes its wings and burns brighter.
				level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, true, true, getX(), getY() + 2, getZ(), 80, 3, 1.5, 3, 0.1);
			}
		}
	}

	/**
	 * A wipe (everyone dead, downed or gone for a while): back to the start of the current phase, not the whole fight.
	 * The phase's mechanic resets with it when the next fight starts.
	 */
	@Override
	public void resetFight() {
		int keep = phase();
		super.resetFight();
		if (keep > 0 && isAlive()) {
			forcePhase(keep);
			setHealth((float) (getMaxHealth() * RaidMath.phaseStartFraction(keep, definition().thresholds())));
		}
	}

	@Override
	protected void onReset() {
		if (level() instanceof ServerLevel level) {
			cleanup(level);
		}
		fightTicks = 0;
		entityData.set(DATA_ENRAGED, false);
		entityData.set(DATA_SHIELDED, false);
		setMode(Mode.FLYING);
	}

	private void cleanup(ServerLevel level) {
		puzzle.clear(level);
		plates.remove(level);
		orbs.clear();
	}

	// ---------------------------------------------------------------------------------------------------------
	// Damage

	/** What incoming damage is multiplied by right now. */
	double incomingMultiplier() {
		if (puzzle.shielded()) {
			return config().shieldDamageMult;
		}
		return switch (mode()) {
			case PERCHED -> config().perchDamageMult;
			case PULLED -> 1.0;
			default -> phase() >= 1 ? config().airborneDamageMult : 1.0;
		};
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			return super.hurtServer(level, source, damage);
		}
		double mult = incomingMultiplier();
		float amount = (float) (damage * mult);
		boolean shielded = puzzle.shielded();
		if (shielded) {
			// The shield holds it in phase 1 until the order is done.
			float floor = (float) (getMaxHealth() * RaidMath.healthFloor(true, definition().thresholds()));
			amount = Math.min(amount, Math.max(0, getHealth() - floor));
		}
		if (mult < 1 && source.getEntity() instanceof Player && tickCount - lastDeflectTick >= 6) {
			lastDeflectTick = tickCount;
			level.playSound(null, getX(), getY(), getZ(), FinalRaidSounds.DEFLECT, SoundSource.HOSTILE, 2f, shielded ? 1.6f : 0.9f);
			level.sendParticles(shielded ? SHIELD : ParticleTypes.CRIT, true, true, getX(), getY() + 1.5, getZ(), 8, 1.5, 1, 1.5, 0.1);
		}
		if (amount <= 0) {
			// Still counts as joining the fight, just no damage through the shield.
			if (source.getEntity() instanceof ServerPlayer player && !player.isSpectator()) {
				engage(player);
			}
			return false;
		}
		boolean hurt = super.hurtServer(level, source, amount);
		if (shielded) {
			float floor = (float) (getMaxHealth() * RaidMath.healthFloor(true, definition().thresholds()));
			if (getHealth() < floor && isAlive()) {
				setHealth(floor);
			}
		}
		return hurt;
	}

	@Override
	public boolean hurtFromPart(ServerLevel level, BossPart part, DamageSource source, float damage) {
		// The head only counts double while it's down on the perch.
		double mult = mode() == Mode.PERCHED ? part.damageMultiplier() : 1.0;
		return hurtServer(level, source, (float) (damage * mult));
	}

	@Override
	public double damageMultiplier() {
		return super.damageMultiplier() * (enraged() ? config().softEnrageDamage : 1.0);
	}

	// ---------------------------------------------------------------------------------------------------------
	// Death

	@Override
	public void die(DamageSource source) {
		boolean wasAlive = !isRemoved() && !dead;
		super.die(source);
		if (wasAlive && level() instanceof ServerLevel level) {
			cleanup(level);
			setDeltaMovement(Vec3.ZERO);
			level.playSound(null, getX(), getY(), getZ(), FinalRaidSounds.DEATH, SoundSource.HOSTILE, 10f, 0.8f);
			if (endArena) {
				FinalRaidEnd.onKilled(level, this);
			}
		}
	}

	@Override
	protected void tickDeath() {
		deathTime++;
		setDeltaMovement(0, 0.02, 0);
		if (level() instanceof ServerLevel level) {
			if (deathTime % 4 == 0) {
				level.sendParticles(ParticleTypes.END_ROD, true, true, getX(), getY() + 2, getZ(), 10 + deathTime / 2, 2.5, 1.5, 2.5, 0.3);
				level.sendParticles(CRACKS, true, true, getX(), getY() + 2, getZ(), 20, 3, 1.5, 3, 0);
			}
			if (deathTime % 20 == 0) {
				level.sendParticles(ParticleTypes.EXPLOSION, true, true, getX(), getY() + 2, getZ(), 3, 2, 1, 2, 0);
			}
			if (deathTime >= 80 && !isRemoved()) {
				level.sendParticles(ColorParticleOption.create(ParticleTypes.FLASH, 0xFFF2ECFF), true, true, getX(), getY() + 2, getZ(), 1, 0, 0, 0, 0);
				level.sendParticles(ParticleTypes.REVERSE_PORTAL, true, true, getX(), getY() + 2, getZ(), 200, 3, 2, 3, 0.5);
				level.broadcastEntityEvent(this, (byte) 60);
				remove(RemovalReason.KILLED);
			}
		}
	}

	@Override
	public void remove(RemovalReason reason) {
		if (reason.shouldDestroy() && level() instanceof ServerLevel level) {
			cleanup(level);
		}
		super.remove(reason);
	}

	// ---------------------------------------------------------------------------------------------------------
	// Sounds

	@Override
	protected SoundEvent getAmbientSound() {
		return FinalRaidSounds.AMBIENT;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return FinalRaidSounds.HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return FinalRaidSounds.DEATH;
	}

	@Override
	protected SoundEvent staggerSound() {
		return FinalRaidSounds.ROAR;
	}

	@Override
	protected float getSoundVolume() {
		return 5f;
	}

	@Override
	public int getAmbientSoundInterval() {
		return 200;
	}

	// ---------------------------------------------------------------------------------------------------------
	// Save data

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.store("RaidOrigin", BlockPos.CODEC, arenaOrigin);
		output.putBoolean("RaidEnd", endArena);
		List<PlateGate.Replaced> saved = plates.saved();
		if (!saved.isEmpty()) {
			output.store("RaidPlates", PlateGate.Replaced.CODEC.listOf(), saved);
		}
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		arenaOrigin = input.read("RaidOrigin", BlockPos.CODEC).orElse(BlockPos.ZERO);
		endArena = input.getBooleanOr("RaidEnd", false);
		if (!endArena) {
			arena = FinalRaidEnd.arenaAround(home());
		}
		input.read("RaidPlates", PlateGate.Replaced.CODEC.listOf()).ifPresent(list -> savedPlates = list);
	}
}
