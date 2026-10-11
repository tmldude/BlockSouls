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
 * The final raid's dragon, the Hollow Sovereign. The raid has three phases; the first is the vanilla Ender Dragon
 * fight, untouched. When that dragon dies it doesn't rise and burst: it <b>falls</b>, and this dragon tears out of the
 * fall ({@link FinalRaidEnd#vanillaDragonFell}), lies dazed while the obsidian towers come down around it, then rises.
 * <ol start="2">
 * <li><b>Grounded</b> (100% → 35%, its phase 0): on foot, Souls style: bite, tail sweep, wing gust, ground breath,
 * charge, stomp, void orbs. At 85% it takes to the air and calls a <b>zombie wave</b>; at 60% it calls a <b>herald</b>
 * (one of the field bosses). It is untouchable in the air; the wave cleared, it lands; the herald killed, it crashes
 * down <b>stunned</b> (×2 damage).</li>
 * <li><b>Final Stand</b> (35% → 0, its phase 1): it hovers over the island and casts <b>Sovereign's End</b> (a closing
 * violet ring; when it completes, everyone dies). Crystals stand on the tower stumps, <b>sealed</b> unless the plates
 * are held; broken in the order the obelisk shows, they crash it down stunned. Then it rises and casts again,
 * faster. A damage check: a group that can't kill it in a few stuns doesn't.</li>
 * </ol>
 * A wipe heals it to the last checkpoint (the phase start, or the last cleared interlude). Its death opens the exit
 * portal as vanilla's would and fires {@code BossEvents.KILLED} with {@link CommonIds#FINAL_BOSS}.
 */
public class FinalDragon extends BossEntity {
	/** What it's doing (synced: the model walks, flies or lies sprawled). */
	public enum Mode {
		/** Falling from where the vanilla dragon died. */
		FALLING,
		/** Lying where it fell; the towers come down. */
		DAZED,
		/** On foot, fighting. */
		GROUNDED,
		/** Rising to hover. */
		TAKEOFF,
		/** Hovering over the island (interludes, phase 3). */
		AIRBORNE,
		/** Coming back down to fight on foot. */
		LANDING,
		/** Dropping out of the sky to its perch. */
		CRASHING,
		/** Sprawled on its perch: the damage window. */
		STUNNED;

		static Mode byId(int id) {
			Mode[] values = values();
			return values[Math.clamp(id, 0, values.length - 1)];
		}

		/** Body on the ground (walk cycle or sprawled). */
		public boolean onGround() {
			return this == GROUNDED || this == DAZED || this == STUNNED;
		}

		/** Lying sprawled. */
		public boolean sprawled() {
			return this == DAZED || this == STUNNED;
		}
	}

	/** Phase 2's breaks. */
	private enum Interlude {
		NONE, WAVE, HERALD
	}

	private static final EntityDataAccessor<Byte> DATA_MODE = SynchedEntityData.defineId(FinalDragon.class, EntityDataSerializers.BYTE);
	private static final EntityDataAccessor<Boolean> DATA_SHIELDED = SynchedEntityData.defineId(FinalDragon.class, EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<Boolean> DATA_ENRAGED = SynchedEntityData.defineId(FinalDragon.class, EntityDataSerializers.BOOLEAN);
	private static final DustParticleOptions SHIELD = new DustParticleOptions(0xB98CFF, 1.8f);
	private static final DustParticleOptions CRACKS = new DustParticleOptions(0x9B4DFF, 1.4f);
	private static final DustParticleOptions CAST = new DustParticleOptions(0x7A2FD6, 2.2f);

	private static BossDefinition definition;

	private @Nullable RaidArena arena;
	private BlockPos arenaOrigin = BlockPos.ZERO;
	private boolean endArena;
	private final CrystalPuzzle puzzle = new CrystalPuzzle(this);
	private final PlateGate plates = new PlateGate(this);
	private final VoidOrbs orbs = new VoidOrbs(this);
	private final RaidAdds adds = new RaidAdds(this);
	private @Nullable TowerCollapse towers;
	private @Nullable List<PlateGate.Replaced> savedPlates;
	private int modeTicks;
	private int fightTicks;
	private double orbitAngle;
	private int lastDeflectTick;
	private Interlude interlude = Interlude.NONE;
	private boolean waveDone;
	private boolean heraldDone;
	private int cycle;
	private int castTotal;
	private int castLeft;
	private int restorePhase;
	private int castsCompleted;

	public FinalDragon(EntityType<? extends FinalDragon> type, Level level) {
		super(type, level);
		this.noPhysics = true;
		this.xpReward = config().experience;
		setNoGravity(true);
		// The head: a weak point while it's on the ground.
		addPart("head", 2.2f, 2.0f, config().headDamageMult, new Vec3(0, 2.6, 5.8));
	}

	public static FinalRaidConfig config() {
		return FinalRaids.CONFIG;
	}

	/** Built once, after the config loads. */
	static BossDefinition createDefinition() {
		FinalRaidConfig c = config();
		var bite = new FinalDragonAttacks.Bite();
		var tail = new FinalDragonAttacks.TailSweep();
		var gust = new FinalDragonAttacks.WingGust();
		var groundBreath = new FinalDragonAttacks.GroundBreath();
		var charge = new FinalDragonAttacks.Charge();
		var stomp = new FinalDragonAttacks.Stomp();
		var orbs = new FinalDragonAttacks.VoidOrbBarrage();
		var breathLine = new FinalDragonAttacks.BreathLine("breath_line", c.breathLine, () -> c.breathLineLength, () -> c.breathLineHalfWidth);
		var dive = new FinalDragonAttacks.Dive();
		var meteors = new FinalDragonAttacks.MeteorShower();
		var sweep = new FinalDragonAttacks.BreathLine("breath_sweep", c.breathSweep, () -> c.breathSweepLength, () -> c.breathSweepHalfWidth);
		definition = BossDefinition.builder(CommonIds.FINAL_BOSS)
				.health(c.health)
				.arenaRadius(c.arenaRadius)
				.music(FinalRaidSounds.MUSIC)
				// Two legendary guns + ammo (guns), only when that module is installed.
				.extraLoot(CommonIds.id(CommonIds.BOSSES, "boss_drops/final_dragon_guns"))
				// Grounded: the ground moveset, plus a few airborne moves for while it hovers over the interludes.
				.phase(BossPhase.first().attacks(bite, tail, gust, groundBreath, charge, stomp, orbs, breathLine, dive))
				.phase(BossPhase.below(c.finalStandThreshold)
						.attacks(orbs, breathLine, dive, meteors, sweep)
						.damage(c.finalStandDamage)
						.cooldowns(c.finalStandCooldowns)
						.recovery(c.finalStandRecovery))
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
		builder.define(DATA_MODE, (byte) Mode.GROUNDED.ordinal());
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

	/** Untouchable right now (falling, dazed, or hovering over an interlude) (both sides). */
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
		setHome(arena.center());
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

	RaidAdds adds() {
		return adds;
	}

	/** The Crystal Order in play (null outside phase 3). Read-only use: tests, debug. */
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

	/** The herald, while it lives (phase 2's second interlude). */
	public @Nullable BossEntity herald() {
		return level() instanceof ServerLevel level ? adds.herald(level) : null;
	}

	/** Wave mobs still alive. */
	public int waveAlive() {
		return level() instanceof ServerLevel level ? adds.waveAlive(level) : 0;
	}

	public boolean waveDone() {
		return waveDone;
	}

	public boolean heraldDone() {
		return heraldDone;
	}

	/** Ticks left on Sovereign's End (phase 3), and its full length this cycle. */
	public int castLeft() {
		return castLeft;
	}

	public int castTotal() {
		return castTotal;
	}

	/** How many times Sovereign's End has gone off this fight. */
	public int castsCompleted() {
		return castsCompleted;
	}

	/** Debug and tests: the cast completes in {@code ticks}. */
	public void finishCastIn(int ticks) {
		castLeft = Math.max(1, ticks);
	}

	/** One line of raid state for debug output ({@code /bs raid info}, test failures). */
	public String debugInfo() {
		CrystalOrder order = puzzle.order();
		return String.format("raid phase %d, %s%s%s, hp %.0f/%.0f, fight %ds%s%s, wave %s, herald %s, cast %d/%d (cycle %d), crystals %s, "
				+ "plates %d (held %d, open %s), orbs %d, at %s, ticks %d%s",
				phase() + 2, mode(), interlude != Interlude.NONE ? " " + interlude : "", isStaggered() ? " (staggered)" : "", getHealth(), getMaxHealth(),
				fightTicks / 20, isEngaged() ? ", engaged" : "", enraged() ? ", ENRAGED" : "", waveDone ? "done" : "pending",
				heraldDone ? "done" : "pending", castLeft, castTotal, cycle,
				order == null ? "none" : order.remaining() + " (" + order.destroyed() + " done)", plates.plates().size(), plates.lastHeld(), plates.open(),
				orbs.count(), blockPosition().toShortString(), tickCount,
				level() instanceof ServerLevel level && !level.isPositionEntityTicking(blockPosition()) ? ", NOT ENTITY-TICKING" : "");
	}

	/** Players the mechanics count: in the fight right now, at least 1, at most {@code maxScaledPlayers}. */
	int playersInFight() {
		int count = 0;
		for (ServerPlayer player : participants()) {
			if (inFight(player)) {
				count++;
			}
		}
		return RaidMath.scaledPlayers(count, config().maxScaledPlayers);
	}

	/** Where its mouth is (attacks spawn from here). */
	Vec3 mouth() {
		Vec3 look = Vec3.directionFromRotation(0, getYRot());
		return position().add(look.scale(6.2)).add(0, 2.2, 0);
	}

	/** The island surface under {@code (x, z)}. */
	double groundAt(double x, double z) {
		return level() instanceof ServerLevel level ? arena().groundY(level, x, z) : getY();
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
		// Scaled like the flight, so small arenas (tests, summons outside the End) don't pull in players far away.
		double r = a.scaled(config().arenaRadius);
		double dy = entity.getY() - a.floorY();
		return dx * dx + dz * dz <= r * r && dy >= -a.scaled(config().arenaBelow) && dy <= a.scaled(config().arenaAbove);
	}

	@Override
	protected double preferredDistance() {
		// It moves itself (walking and flying); the framework's ground navigation never runs.
		return 1000;
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	public void travel(Vec3 input) {
		// Moves exactly by the velocity the movement code set: no gravity, no friction, through rubble...
		Vec3 velocity = getDeltaMovement();
		// ...but on a leash: never farther from the island center than half the fight radius (55 blocks in the End),
		// so no attack path can carry it off the island or out of a small arena.
		RaidArena a = arena();
		Vec3 center = a.centerVec();
		double leash = a.scaled(config().arenaRadius * 0.5);
		Vec3 next = position().add(velocity);
		double dx = next.x - center.x;
		double dz = next.z - center.z;
		double distance = Math.sqrt(dx * dx + dz * dz);
		if (distance > leash) {
			double k = leash / distance;
			velocity = new Vec3(center.x + dx * k - getX(), velocity.y, center.z + dz * k - getZ());
			setDeltaMovement(velocity);
		}
		move(MoverType.SELF, velocity);
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
		// Violet light leaking from the cracks; more, and soul fire, in the final stand and the soft enrage.
		if (tickCount % 3 == 0) {
			level().addParticle(ParticleTypes.REVERSE_PORTAL, getX() + (random.nextDouble() - 0.5) * 3, getY() + 1 + random.nextDouble() * 2,
					getZ() + (random.nextDouble() - 0.5) * 3, 0, 0.02, 0);
		}
		if ((enraged() || phase() >= 1) && tickCount % 2 == 0) {
			level().addParticle(ParticleTypes.SOUL_FIRE_FLAME, getX() + (random.nextDouble() - 0.5) * 3, getY() + 1 + random.nextDouble() * 2,
					getZ() + (random.nextDouble() - 0.5) * 3, 0, 0.05, 0);
		}
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		if (mode() == Mode.GROUNDED) {
			// On foot it stands still on the ground unless walking or an attack moves it this tick (no sliding).
			walk(Vec3.ZERO);
		}
		super.customServerAiStep(level);
		if (savedPlates != null) {
			plates.restore(level, savedPlates);
			savedPlates = null;
		}
		if (restorePhase > 0) {
			forcePhase(restorePhase);
			restorePhase = 0;
		}
		if (isDeadOrDying()) {
			return;
		}
		if (endArena && tickCount % 20 == 0) {
			FinalRaidEnd.seen(level, this);
		}
		if (towers != null) {
			towers.tick(level);
			if (towers.done()) {
				towers = null;
			}
		}
		Mode mode = mode();
		if (mode == Mode.FALLING || mode == Mode.DAZED) {
			modeTicks++;
			entityData.set(DATA_SHIELDED, true);
			fallTick(level, mode);
			return;
		}
		if (!isEngaged()) {
			idle(level);
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
		entityData.set(DATA_SHIELDED, incomingMultiplier() <= 0);

		orbs.tick(level);
		if (phase() == 0) {
			interludeTick(level);
		} else {
			finalStandTick(level);
		}
		modeTick(level);
		if (tickCount % 5 == 0) {
			FinalRaidEnd.pacifyEndermen(level, this);
		}
		if (shielded() && tickCount % 4 == 0) {
			shieldShell(level);
		}
	}

	// --- The fall (raid phase 1 → 2) ---

	/** Starts the fall from where the vanilla dragon died (called right after spawning it there). */
	public void startFall() {
		setMode(Mode.FALLING);
		entityData.set(DATA_SHIELDED, true);
	}

	private void fallTick(ServerLevel level, Mode mode) {
		if (mode == Mode.FALLING) {
			double ground = groundAt(getX(), getZ());
			double speed = config().fallSpeed;
			setDeltaMovement(getDeltaMovement().x * 0.9, -Math.min(speed, Math.max(0.05, getY() - ground)), getDeltaMovement().z * 0.9);
			setXRot(35);
			if (modeTicks % 3 == 0) {
				level.sendParticles(ParticleTypes.LARGE_SMOKE, true, true, getX(), getY() + 2, getZ(), 6, 1.5, 1, 1.5, 0.02);
			}
			if (getY() - ground < 0.15 || modeTicks > 20 * 20) {
				setPos(getX(), ground, getZ());
				setDeltaMovement(Vec3.ZERO);
				setXRot(0);
				impact(level);
			}
			return;
		}
		// Dazed: lies still while the towers fall, then rises and the second phase begins.
		setDeltaMovement(Vec3.ZERO);
		if (modeTicks % 10 == 0) {
			level.sendParticles(CRACKS, true, true, getX(), getY() + 1, getZ(), 10, 2.5, 0.6, 2.5, 0);
		}
		if (modeTicks >= config().dazedTicks) {
			setMode(Mode.GROUNDED);
			entityData.set(DATA_SHIELDED, false);
			level.playSound(null, getX(), getY(), getZ(), FinalRaidSounds.ROAR, SoundSource.HOSTILE, 10f, 0.5f);
			level.sendParticles(ParticleTypes.REVERSE_PORTAL, true, true, getX(), getY() + 2, getZ(), 200, 3, 2, 3, 0.3);
		}
	}

	private void impact(ServerLevel level) {
		setMode(Mode.DAZED);
		level.playSound(null, getX(), getY(), getZ(), FinalRaidSounds.LAND, SoundSource.HOSTILE, 10f, 0.4f);
		level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, true, true, getX(), getY() + 1, getZ(), 2, 2, 0.5, 2, 0);
		level.sendParticles(ParticleTypes.CLOUD, true, true, getX(), getY() + 0.5, getZ(), 80, 6, 0.3, 6, 0.15);
		if (endArena) {
			// The impact brings the towers down: the island is the open arena from here on.
			towers = new TowerCollapse(arena(), config().towerCollapseStaggerTicks);
		}
	}

	// --- Idle (not engaged) ---

	private void idle(ServerLevel level) {
		if (phase() >= 1) {
			// Over the island, out of reach, waiting.
			if (mode() != Mode.AIRBORNE) {
				setMode(Mode.AIRBORNE);
			}
			orbitAngle += 0.01;
			steer(arena().orbit(orbitAngle, config().hoverRadius, config().hoverHeight), 0.6, 0.12);
			faceVelocity();
			return;
		}
		if (mode() != Mode.GROUNDED) {
			setMode(Mode.GROUNDED);
		}
		Vec3 home = Vec3.atBottomCenterOf(home());
		double dx = home.x - getX();
		double dz = home.z - getZ();
		double distance = Math.sqrt(dx * dx + dz * dz);
		Vec3 step = distance > 1.5 ? new Vec3(dx, 0, dz).scale(Math.min(config().walkSpeed, distance) / distance) : Vec3.ZERO;
		walk(step);
		if (step.lengthSqr() > 0) {
			face(step);
		}
	}

	// --- Phase 2: interludes ---

	private void interludeTick(ServerLevel level) {
		switch (interlude) {
			case NONE -> {
				if (mode() != Mode.GROUNDED || attackRun() != null || isStaggered()) {
					return;
				}
				double fraction = getHealth() / getMaxHealth();
				if (!waveDone && fraction <= config().waveThreshold + 1e-4) {
					beginWave(level);
				} else if (waveDone && !heraldDone && fraction <= config().heraldThreshold + 1e-4) {
					beginHerald(level);
				}
			}
			case WAVE -> {
				if (adds.waveOver(level)) {
					interlude = Interlude.NONE;
					waveDone = true;
					land(level);
				}
			}
			case HERALD -> {
				if (adds.heraldOver(level)) {
					interlude = Interlude.NONE;
					heraldDone = true;
					crash(level);
				}
			}
		}
	}

	/** Debug and tests: start the zombie wave now. */
	public void beginWave(ServerLevel level) {
		cancelAttack();
		interlude = Interlude.WAVE;
		setMode(Mode.TAKEOFF);
		level.playSound(null, getX(), getY(), getZ(), FinalRaidSounds.ROAR, SoundSource.HOSTILE, 10f, 0.8f);
		adds.startWave(level, playersInFight());
	}

	/** Debug and tests: call the herald now. */
	public void beginHerald(ServerLevel level) {
		cancelAttack();
		waveDone = true;
		if (!adds.startHerald(level)) {
			// No boss to call: the interlude is skipped.
			heraldDone = true;
			return;
		}
		interlude = Interlude.HERALD;
		setMode(Mode.TAKEOFF);
		level.playSound(null, getX(), getY(), getZ(), FinalRaidSounds.ROAR, SoundSource.HOSTILE, 10f, 0.6f);
	}

	// --- Phase 3: the Final Stand ---

	private void setupFinalStand(ServerLevel level) {
		adds.clear(level);
		interlude = Interlude.NONE;
		cycle = 0;
		int players = playersInFight();
		plates.place(level, players);
		puzzle.setup(level, players);
		startCast(players);
		setMode(Mode.TAKEOFF);
		level.playSound(null, getX(), getY(), getZ(), FinalRaidSounds.ENRAGE, SoundSource.HOSTILE, 8f, 0.5f);
	}

	private void startCast(int players) {
		castTotal = RaidMath.castTicks(cycle, players, config().castTicks, config().castSoloBonus, config().castCycleFactor, config().castMinTicks);
		castLeft = castTotal;
	}

	private void finalStandTick(ServerLevel level) {
		Mode mode = mode();
		boolean casting = mode == Mode.AIRBORNE || mode == Mode.TAKEOFF;
		boolean open = plates.tick(level, playersInFight());
		if (casting) {
			puzzle.seal(level, !open);
			puzzle.tick(level);
			castTick(level);
		}
	}

	private void castTick(ServerLevel level) {
		if (castTotal <= 0) {
			startCast(playersInFight());
		}
		castLeft--;
		float progress = 1f - castLeft / (float) castTotal;
		RaidArena a = arena();
		Vec3 center = a.centerVec();
		if (castLeft % 4 == 0) {
			// A ring of violet closing in on the island center: when it reaches the middle, the cast completes.
			double r = a.scaled(46) * (1 - progress) + 1;
			int points = Math.max(12, (int) (r * 1.2));
			for (int i = 0; i < points; i++) {
				double angle = i * Math.PI * 2 / points + level.getGameTime() * 0.01;
				level.sendParticles(CAST, true, true, center.x + Math.cos(angle) * r, center.y + 0.3, center.z + Math.sin(angle) * r, 1, 0, 0.3, 0, 0);
			}
			level.sendParticles(CAST, true, true, getX(), getY() + 2, getZ(), 6, 1.5, 1.5, 1.5, 0);
		}
		if (castLeft % 40 == 0) {
			level.playSound(null, center.x, center.y, center.z, FinalRaidSounds.ORB, SoundSource.HOSTILE, 8f, 0.5f + progress);
		}
		if (castLeft <= 0) {
			sovereignsEnd(level);
		}
	}

	/** The cast completes: a flash of void across the island, and everyone in the fight dies. */
	private void sovereignsEnd(ServerLevel level) {
		Vec3 center = arena().centerVec();
		level.playSound(null, center.x, center.y, center.z, FinalRaidSounds.DEATH, SoundSource.HOSTILE, 16f, 0.5f);
		level.sendParticles(ColorParticleOption.create(ParticleTypes.FLASH, 0xFF9B4DFF), true, true, center.x, center.y + 3, center.z, 1, 0, 0, 0, 0);
		level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, true, true, center.x, center.y + 2, center.z, 6, 8, 2, 8, 0);
		for (ServerPlayer player : participants()) {
			if (inFight(player)) {
				player.hurtServer(level, level.damageSources().indirectMagic(this, this), 10000);
			}
		}
		// Whoever survived it (a totem) faces the next cast.
		castsCompleted++;
		castLeft = castTotal;
	}

	/** A wrong crystal: the cast surges forward. */
	void castPenalty() {
		castLeft = Math.max(1, castLeft - (int) Math.round(castTotal * config().wrongCrystalCastPenalty));
	}

	/** The whole Crystal Order broken: the cast breaks and it crashes down stunned. */
	void onOrderComplete(ServerLevel level) {
		level.playSound(null, getX(), getY(), getZ(), FinalRaidSounds.SHIELD_BREAK, SoundSource.HOSTILE, 8f, 0.8f);
		level.sendParticles(ParticleTypes.END_ROD, true, true, getX(), getY() + 1.5, getZ(), 150, 3, 2, 3, 0.4);
		level.sendParticles(ColorParticleOption.create(ParticleTypes.FLASH, 0xFFF2ECFF), true, true, getX(), getY() + 1.5, getZ(), 1, 0, 0, 0, 0);
		crash(level);
	}

	/** After a stun in phase 3: back up, a new order, a faster cast. */
	private void nextCycle(ServerLevel level) {
		cycle++;
		int players = playersInFight();
		puzzle.setup(level, players);
		startCast(players);
	}

	// --- Movement by mode ---

	private void modeTick(ServerLevel level) {
		switch (mode()) {
			case GROUNDED -> {
				if (isStaggered()) {
					walk(Vec3.ZERO);
				}
				// Otherwise fightTick (between attacks) or the attack moves it.
			}
			case TAKEOFF -> {
				Vec3 hover = arena().orbit(orbitAngle, config().hoverRadius, config().hoverHeight);
				steer(hover, config().flySpeed, 0.2);
				faceVelocity();
				flapSound(level);
				if (position().distanceTo(hover) < arena().scaled(3) + 1 || modeTicks > 100) {
					setMode(Mode.AIRBORNE);
				}
			}
			case AIRBORNE -> {
				if (isStaggered()) {
					setDeltaMovement(getDeltaMovement().scale(0.8));
				}
			}
			case LANDING -> {
				Vec3 spot = landingSpot();
				int left = Math.max(1, 40 - modeTicks);
				setDeltaMovement(spot.subtract(position()).scale(1.0 / left));
				faceVelocity();
				if (modeTicks >= 40) {
					setPos(spot);
					setDeltaMovement(Vec3.ZERO);
					setXRot(0);
					setMode(Mode.GROUNDED);
					level.playSound(null, getX(), getY(), getZ(), FinalRaidSounds.LAND, SoundSource.HOSTILE, 5f, 0.8f);
					level.sendParticles(ParticleTypes.CLOUD, true, true, getX(), getY() + 0.5, getZ(), 40, 4, 0.2, 4, 0.1);
				}
			}
			case CRASHING -> {
				Vec3 perch = arena().perch();
				int left = Math.max(1, 30 - modeTicks);
				setDeltaMovement(perch.subtract(position()).scale(1.0 / left));
				setXRot(30);
				if (modeTicks % 2 == 0) {
					level.sendParticles(ParticleTypes.LARGE_SMOKE, true, true, getX(), getY() + 2, getZ(), 4, 1.5, 1, 1.5, 0.02);
				}
				if (modeTicks >= 30) {
					setPos(perch);
					setDeltaMovement(Vec3.ZERO);
					setXRot(0);
					setMode(Mode.STUNNED);
					level.playSound(null, getX(), getY(), getZ(), FinalRaidSounds.LAND, SoundSource.HOSTILE, 8f, 0.5f);
					level.sendParticles(ParticleTypes.EXPLOSION, true, true, getX(), getY() + 0.5, getZ(), 8, 3, 0.3, 3, 0);
					level.sendParticles(ParticleTypes.END_ROD, true, true, getX(), getY() + 2, getZ(), 60, 2.5, 1.5, 2.5, 0.2);
				}
			}
			case STUNNED -> {
				setDeltaMovement(Vec3.ZERO);
				if (isStaggered()) {
					// The phase roar doesn't eat the damage window.
					modeTicks--;
				}
				if (modeTicks % 8 == 0) {
					// Light spills out of the cracks: hit it now.
					level.sendParticles(CRACKS, true, true, getX(), getY() + 1.5, getZ(), 14, 2.5, 0.8, 2.5, 0);
				}
				if (modeTicks >= config().stunTicks) {
					level.playSound(null, getX(), getY(), getZ(), FinalRaidSounds.ROAR, SoundSource.HOSTILE, 6f, 0.9f);
					if (phase() >= 1) {
						nextCycle(level);
						setMode(Mode.TAKEOFF);
					} else {
						setMode(Mode.GROUNDED);
					}
				}
			}
			default -> {
			}
		}
	}

	@Override
	protected void fightTick(ServerLevel level, @Nullable LivingEntity target) {
		switch (mode()) {
			case GROUNDED -> stalk(target);
			case AIRBORNE -> {
				hover(0.12);
				flapSound(level);
			}
			default -> {
			}
		}
	}

	/** On foot between attacks: walk toward the target until close, facing it. */
	private void stalk(@Nullable LivingEntity target) {
		if (target == null) {
			walk(Vec3.ZERO);
			return;
		}
		double dx = target.getX() - getX();
		double dz = target.getZ() - getZ();
		double distance = Math.sqrt(dx * dx + dz * dz);
		double speed = config().walkSpeed * definition().phase(phase()).speedMult();
		Vec3 step = distance > arena().scaled(config().walkDistance) + 1 ? new Vec3(dx, 0, dz).scale(speed / Math.max(1e-3, distance)) : Vec3.ZERO;
		walk(step);
		if (distance > 0.5) {
			face(new Vec3(dx, 0, dz));
		}
	}

	/** Moves {@code step} horizontally with the feet on the island surface. */
	void walk(Vec3 step) {
		double ground = groundAt(getX() + step.x, getZ() + step.z);
		setDeltaMovement(step.x, (ground - getY()) * 0.5, step.z);
		setXRot(0);
	}

	/** In the air between attacks: circle over the island (attack recoveries use it to come back to the circle). */
	void hover(double ease) {
		double speed = config().flySpeed * definition().phase(phase()).speedMult();
		Vec3 center = arena().centerVec();
		double current = Math.atan2(getZ() - center.z, getX() - center.x);
		// Keep the angle near where it actually is, so coming back from an attack doesn't cut across the island.
		if (Math.abs(Math.atan2(Math.sin(current - orbitAngle), Math.cos(current - orbitAngle))) > 0.6) {
			orbitAngle = current;
		}
		orbitAngle += speed / Math.max(1, arena().scaled(config().hoverRadius));
		Vec3 point = arena().orbit(orbitAngle, config().hoverRadius, config().hoverHeight + Math.sin(tickCount * 0.05) * 2);
		steer(point, speed * 1.6, ease);
		faceVelocity();
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

	private Vec3 landingSpot() {
		Vec3 center = arena().centerVec();
		Vec3 out = new Vec3(getX() - center.x, 0, getZ() - center.z);
		Vec3 spot = out.lengthSqr() < 1e-3 ? center : center.add(out.normalize().scale(arena().scaled(8)));
		return new Vec3(spot.x, groundAt(spot.x, spot.z), spot.z);
	}

	private void land(ServerLevel level) {
		setMode(Mode.LANDING);
		level.playSound(null, getX(), getY(), getZ(), FinalRaidSounds.ROAR, SoundSource.HOSTILE, 6f, 1.1f);
	}

	private void crash(ServerLevel level) {
		cancelAttack();
		setMode(Mode.CRASHING);
		level.playSound(null, getX(), getY(), getZ(), FinalRaidSounds.SCREECH, SoundSource.HOSTILE, 8f, 0.6f);
	}

	/** Debug: crash it down stunned now. */
	public void forceStun() {
		if (level() instanceof ServerLevel level) {
			crash(level);
		}
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

	/** Debug: complete the current Crystal Order (it crashes down stunned). */
	public void skipCrystals() {
		if (level() instanceof ServerLevel level && phase() >= 1) {
			puzzle.skip(level);
			onOrderComplete(level);
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
		if (phase() >= 1) {
			setupFinalStand(level);
		} else if (mode() != Mode.GROUNDED) {
			setMode(Mode.GROUNDED);
		}
		level.playSound(null, getX(), getY(), getZ(), FinalRaidSounds.ROAR, SoundSource.HOSTILE, 8f, 0.6f);
	}

	@Override
	protected void onPhaseEnter(int index) {
		if (level() instanceof ServerLevel level && isEngaged() && index >= 1) {
			setupFinalStand(level);
		}
	}

	/**
	 * A wipe (everyone dead, downed or gone for a while): back to the last checkpoint, not the whole fight. Cleared
	 * interludes stay cleared; the phase's mechanic is rebuilt when the next fight starts.
	 */
	@Override
	public void resetFight() {
		int keep = phase();
		double checkpoint = RaidMath.checkpoint(keep, definition().thresholds(), waveDone, config().waveThreshold, heraldDone,
				config().heraldThreshold);
		super.resetFight();
		if (!isAlive()) {
			return;
		}
		if (keep > 0) {
			forcePhase(keep);
		}
		setHealth((float) (getMaxHealth() * checkpoint));
	}

	@Override
	protected void onReset() {
		if (level() instanceof ServerLevel level) {
			cleanup(level);
		}
		fightTicks = 0;
		interlude = Interlude.NONE;
		cycle = 0;
		castLeft = 0;
		castTotal = 0;
		entityData.set(DATA_ENRAGED, false);
		entityData.set(DATA_SHIELDED, false);
	}

	private void cleanup(ServerLevel level) {
		puzzle.clear(level);
		plates.remove(level);
		orbs.clear();
		adds.clear(level);
	}

	// ---------------------------------------------------------------------------------------------------------
	// Damage

	/** What incoming damage is multiplied by right now. */
	double incomingMultiplier() {
		Mode mode = mode();
		if (mode == Mode.FALLING || mode == Mode.DAZED || interlude != Interlude.NONE) {
			return 0;
		}
		return switch (mode) {
			case STUNNED -> config().stunDamageMult;
			case GROUNDED, CRASHING, LANDING -> 1.0;
			default -> phase() >= 1 ? config().finalStandAirborneDamageMult : 1.0;
		};
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			return super.hurtServer(level, source, damage);
		}
		double mult = incomingMultiplier();
		float amount = (float) (damage * mult);
		// Phase 2 can't be pushed past an interlude that hasn't happened yet.
		float floor = phase() == 0
				? (float) (getMaxHealth() * RaidMath.healthFloor(waveDone, config().waveThreshold, heraldDone, config().heraldThreshold))
				: 0;
		amount = Math.min(amount, Math.max(0, getHealth() - floor));
		if (mult < 1 && source.getEntity() instanceof Player && tickCount - lastDeflectTick >= 6) {
			lastDeflectTick = tickCount;
			level.playSound(null, getX(), getY(), getZ(), FinalRaidSounds.DEFLECT, SoundSource.HOSTILE, 2f, mult <= 0 ? 1.6f : 0.9f);
			level.sendParticles(mult <= 0 ? SHIELD : ParticleTypes.CRIT, true, true, getX(), getY() + 1.5, getZ(), 8, 1.5, 1, 1.5, 0.1);
		}
		if (amount <= 0) {
			// Still counts as joining the fight, just no damage.
			if (source.getEntity() instanceof ServerPlayer player && !player.isSpectator() && mode() != Mode.FALLING && mode() != Mode.DAZED) {
				engage(player);
			}
			return false;
		}
		boolean hurt = super.hurtServer(level, source, amount);
		if (floor > 0 && getHealth() < floor && isAlive()) {
			setHealth(floor);
		}
		return hurt;
	}

	@Override
	public boolean hurtFromPart(ServerLevel level, BossPart part, DamageSource source, float damage) {
		// The head only counts double while it's down where you can reach it.
		double mult = mode() == Mode.GROUNDED || mode() == Mode.STUNNED ? part.damageMultiplier() : 1.0;
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
		output.putBoolean("RaidWaveDone", waveDone);
		output.putBoolean("RaidHeraldDone", heraldDone);
		output.putInt("RaidPhase", phase());
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
		waveDone = input.getBooleanOr("RaidWaveDone", false);
		heraldDone = input.getBooleanOr("RaidHeraldDone", false);
		if (!endArena) {
			arena = FinalRaidEnd.arenaAround(home());
		}
		// A restart keeps the phase (applied on the first tick; its stagger plays when the fight resumes).
		restorePhase = input.getIntOr("RaidPhase", 0);
		input.read("RaidPlates", PlateGate.Replaced.CODEC.listOf()).ifPresent(list -> savedPlates = list);
	}
}
