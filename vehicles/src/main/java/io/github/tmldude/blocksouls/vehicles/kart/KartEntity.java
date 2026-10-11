package io.github.tmldude.blocksouls.vehicles.kart;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.InterpolationHandler;
import net.minecraft.world.entity.LinearInterpolationHandler;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import io.github.tmldude.blocksouls.common.CommonTags;
import io.github.tmldude.blocksouls.common.player.PlayerStatus;
import io.github.tmldude.blocksouls.vehicles.BlockSoulsVehicles;
import io.github.tmldude.blocksouls.vehicles.VehicleRegistry;
import io.github.tmldude.blocksouls.vehicles.VehiclesConfig;

/**
 * The repaired 4-seat kart. Driven like a boat: the driver's client simulates the movement and the server
 * accepts it (vanilla's vehicle move packets); with nobody driving, the server simulates it (coasting,
 * falling). Ramming, health and fuel are server-side; the driver's client reads the synced fuel to know whether
 * the engine pulls.
 */
public class KartEntity extends Entity {
	public static final int MAX_PASSENGERS = 4;
	/** Seat offsets in the kart's frame {x (left), z (forward)}: driver front-left, then front-right, rear-left, rear-right. */
	private static final double[][] SEATS = {{0.38, 0.05}, {-0.38, 0.05}, {0.38, -0.6}, {-0.38, -0.6}};
	private static final double SEAT_HEIGHT = 0.3;
	/** Wheel radius in blocks, for the spin animation. */
	private static final double WHEEL_RADIUS = 0.22;

	private static final EntityDataAccessor<Float> DATA_HEALTH = SynchedEntityData.defineId(KartEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Integer> DATA_HURT_TIME = SynchedEntityData.defineId(KartEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_FUEL = SynchedEntityData.defineId(KartEntity.class, EntityDataSerializers.INT);

	// Driving state, only meaningful on the side simulating the movement.
	private Input input = Input.EMPTY;
	private boolean drifting;
	private int driftTicks;
	private int hopCooldown;
	private boolean lastJump;
	private float deltaRotation;

	// Server: measured movement (the driver's client moves the kart between our ticks) and ram cooldowns.
	private @Nullable Vec3 lastServerPos;
	private double measuredSpeed;
	private final Map<Integer, Integer> lastRammed = new HashMap<>();
	private final Cooldown horn = new Cooldown();
	private final Cooldown flinch = new Cooldown();

	// Client visuals, derived from movement so every client sees them, not just the driver.
	public float wheelSpin;
	public float wheelSpinO;
	public float steer;
	public float steerO;
	private int lastDriftTier;

	public KartEntity(EntityType<? extends KartEntity> type, Level level) {
		super(type, level);
		this.blocksBuilding = true;
	}

	private static VehiclesConfig.Kart cfg() {
		return BlockSoulsVehicles.CONFIG.kart;
	}

	private static VehiclesConfig.Fuel fuelCfg() {
		return BlockSoulsVehicles.CONFIG.fuel;
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(DATA_HEALTH, cfg().maxHealth);
		builder.define(DATA_HURT_TIME, 0);
		builder.define(DATA_FUEL, fuelCfg().fuelCapacity);
	}

	/** Fuel left, in ticks of throttle. */
	public int getFuel() {
		return entityData.get(DATA_FUEL);
	}

	public void setFuel(int fuel) {
		entityData.set(DATA_FUEL, KartFuel.clamp(fuel, fuelCfg()));
	}

	public float getHealth() {
		return entityData.get(DATA_HEALTH);
	}

	public void setHealth(float health) {
		entityData.set(DATA_HEALTH, Mth.clamp(health, 0, cfg().maxHealth));
	}

	public int getHurtTime() {
		return entityData.get(DATA_HURT_TIME);
	}

	/** Called by the driver's client every tick with its movement keys. */
	public void setInput(Input input) {
		this.input = input;
	}

	public boolean isDrifting() {
		return drifting;
	}

	public int getDriftTicks() {
		return driftTicks;
	}

	/** Horizontal speed the server measured last tick (blocks/tick). */
	public double getMeasuredSpeed() {
		return measuredSpeed;
	}

	@Override
	protected InterpolationHandler createInterpolationHandler() {
		// Smooths the kart for everyone but the driver (same as boats).
		return LinearInterpolationHandler.create(this, 3);
	}

	@Override
	protected double getDefaultGravity() {
		return cfg().gravity;
	}

	@Override
	public float maxUpStep() {
		return cfg().stepHeight;
	}

	@Override
	protected MovementEmission getMovementEmission() {
		return MovementEmission.EVENTS;
	}

	@Override
	public void tick() {
		super.tick();
		if (getHurtTime() > 0 && !level().isClientSide()) {
			entityData.set(DATA_HURT_TIME, getHurtTime() - 1);
		}

		if (isLocalInstanceAuthoritative()) {
			simulate(getControllingPassenger() != null ? input : Input.EMPTY);
		} else {
			deltaRotation = 0;
			drifting = false;
			driftTicks = 0;
		}

		applyEffectsFromBlocks();

		if (level() instanceof ServerLevel serverLevel) {
			serverTick(serverLevel);
		} else {
			clientTick();
		}
	}

	/**
	 * One tick of driving physics with these keys. {@link #tick} runs it on whichever side simulates the kart;
	 * game tests call it directly, since a mock driver's client doesn't exist to do it.
	 */
	public void simulate(Input in) {
		boolean wasOnGround = onGround();
		drive(in);
		double fallSpeed = getDeltaMovement().y;
		move(MoverType.SELF, getDeltaMovement());
		if (!wasOnGround && onGround()) {
			land(fallSpeed);
		}
	}

	private void drive(Input in) {
		VehiclesConfig.Kart cfg = cfg();
		boolean onGround = onGround();
		boolean inWater = isInWater();
		Vec3 velocity = getDeltaMovement();

		int throttle = (in.forward() ? 1 : 0) - (in.backward() ? 1 : 0);
		int steer = (in.right() ? 1 : 0) - (in.left() ? 1 : 0);

		// Steer first, then split the old velocity along the new heading: whatever isn't along it is sideways
		// slide, which grip removes. Low grip (drifting, ice) lets the rear swing out.
		double[] heading = KartPhysics.heading(getYRot());
		double forward = velocity.x * heading[0] + velocity.z * heading[1];
		deltaRotation = (float) KartPhysics.yawRate(forward, steer, drifting, onGround, cfg);
		setYRot(getYRot() + deltaRotation);

		heading = KartPhysics.heading(getYRot());
		forward = velocity.x * heading[0] + velocity.z * heading[1];
		double sideX = velocity.x - forward * heading[0];
		double sideZ = velocity.z - forward * heading[1];

		boolean nowDrifting = KartPhysics.drifting(drifting, in.sprint(), steer, forward, cfg) && (onGround || drifting) && !inWater;
		if (drifting && !nowDrifting) {
			double boost = KartPhysics.driftBoost(driftTicks, cfg);
			if (boost > 0) {
				forward += boost;
				playLocalSound(VehicleRegistry.KART_BOOST, 0.8f, 1.0f + 0.2f * KartPhysics.driftTier(driftTicks, cfg));
			}
		}
		driftTicks = nowDrifting ? driftTicks + 1 : 0;
		drifting = nowDrifting;

		if (onGround || inWater) {
			// An empty tank still brakes but won't pull: the kart coasts to a stop.
			int engine = KartFuel.effectiveThrottle(throttle, forward, getFuel());
			forward = KartPhysics.forwardSpeed(forward, engine, inWater ? cfg.waterMaxSpeed : cfg.maxSpeed, cfg);
		} else {
			forward *= cfg.airDrag;
		}
		double kept = KartPhysics.sidewaysKept(drifting, onIce(), onGround, cfg);
		sideX *= kept;
		sideZ *= kept;

		double vertical = velocity.y;
		if (inWater) {
			// Floats on the surface instead of sinking.
			vertical = getFluidHeight(FluidTags.WATER) > cfg.floatDepth ? cfg.waterBuoyancy : Math.max(0, vertical) * 0.5;
		} else {
			vertical = (vertical - getGravity()) * cfg.airDrag;
		}

		if (hopCooldown > 0) {
			hopCooldown--;
		}
		if (in.jump() && !lastJump && onGround && hopCooldown == 0 && !inWater) {
			vertical = cfg.hopVelocity;
			hopCooldown = cfg.hopCooldownTicks;
			playLocalSound(VehicleRegistry.KART_HOP, 0.6f, 1.0f);
		}
		lastJump = in.jump();

		setDeltaMovement(forward * heading[0] + sideX, vertical, forward * heading[1] + sideZ);
	}

	private void land(double fallSpeed) {
		double bounce = KartPhysics.landingBounce(fallSpeed, cfg());
		if (bounce > 0) {
			Vec3 velocity = getDeltaMovement();
			setDeltaMovement(velocity.x, bounce, velocity.z);
			playLocalSound(VehicleRegistry.KART_HOP, 0.5f, 0.7f);
		}
	}

	private boolean onIce() {
		BlockPos below = getBlockPosBelowThatAffectsMyMovement();
		return level().getBlockState(below).getBlock().getFriction() > cfg().iceFriction;
	}

	/** Plays a sound on the simulating side: everyone on the server, or just this client. */
	private void playLocalSound(SoundEvent sound, float volume, float pitch) {
		if (level().isClientSide()) {
			level().playLocalSound(getX(), getY(), getZ(), sound, SoundSource.NEUTRAL, volume, pitch, false);
		} else {
			level().playSound(null, getX(), getY(), getZ(), sound, SoundSource.NEUTRAL, volume, pitch);
		}
	}

	private void serverTick(ServerLevel level) {
		Vec3 pos = position();
		Vec3 moved = lastServerPos == null ? Vec3.ZERO : pos.subtract(lastServerPos);
		lastServerPos = pos;
		measuredSpeed = moved.horizontalDistance();
		if (!isLocalInstanceAuthoritative()) {
			// Keeps the driver's momentum, so the kart coasts on if they hop out.
			setDeltaMovement(moved);
		}
		ram(level, moved);
		burnFuel(level);
	}

	/**
	 * Burns fuel for the driver's throttle. The server doesn't run the driver's physics, but it knows their keys:
	 * vanilla sends every client's movement input ({@link ServerPlayer#getLastClientInput}).
	 */
	private void burnFuel(ServerLevel level) {
		if (!(getControllingPassenger() instanceof ServerPlayer driver)) {
			return;
		}
		Input keys = driver.getLastClientInput();
		int throttle = (keys.forward() ? 1 : 0) - (keys.backward() ? 1 : 0);
		if (throttle == 0) {
			return;
		}
		VehiclesConfig.Fuel cfg = fuelCfg();
		int fuel = getFuel();
		setFuel(KartFuel.burn(fuel, throttle, cfg));
		if (KartFuel.low(fuel, cfg) && cfg.sputterIntervalTicks > 0 && tickCount % cfg.sputterIntervalTicks == 0) {
			// Coughs while running low, and when you floor it on an empty tank.
			Vec3 exhaust = local(-0.35, -1.05);
			level.sendParticles(ParticleTypes.LARGE_SMOKE, exhaust.x, getY() + 0.55, exhaust.z, 4, 0.1, 0.05, 0.1, 0.02);
			level.playSound(null, getX(), getY(), getZ(), VehicleRegistry.KART_SPUTTER, SoundSource.NEUTRAL, 0.8f,
					0.8f + random.nextFloat() * 0.3f);
		}
	}

	/**
	 * Server: sounds the horn if its cooldown allows. Wave mobs close by flinch away from the kart, at most once
	 * per flinch cooldown: a panic button, not a weapon. Returns whether it honked.
	 */
	public boolean honk(ServerLevel level) {
		VehiclesConfig.Horn cfg = BlockSoulsVehicles.CONFIG.horn;
		long now = level.getGameTime();
		if (!horn.tryUse(now, cfg.cooldownTicks)) {
			return false;
		}
		// Variable-range sounds carry 16 blocks per unit of volume.
		level.playSound(null, getX(), getY(), getZ(), VehicleRegistry.KART_HORN, SoundSource.NEUTRAL,
				Math.max(1.0f, cfg.hearingRange / 16.0f), 1.0f);

		if (flinch.ready(now, cfg.flinchCooldownTicks)) {
			double radius = cfg.flinchRadius;
			List<Mob> mobs = level.getEntitiesOfClass(Mob.class, getBoundingBox().inflate(radius), mob -> mob.isAlive()
					&& mob.is(CommonTags.WAVE_MOBS) && mob.distanceToSqr(this) <= radius * radius);
			if (!mobs.isEmpty()) {
				flinch.tryUse(now, cfg.flinchCooldownTicks);
				DamageSource source = level.damageSources().generic();
				for (Mob mob : mobs) {
					mob.getNavigation().stop();
					// knockback() pushes away from the given direction, so point it at the kart.
					mob.knockback(cfg.flinchStrength, getX() - mob.getX(), getZ() - mob.getZ(), source, 0);
					level.sendParticles(ParticleTypes.SMOKE, mob.getX(), mob.getY(1.0) + 0.2, mob.getZ(), 3, 0.2, 0.1, 0.2, 0.01);
				}
			}
		}
		return true;
	}

	private void ram(ServerLevel level, Vec3 moved) {
		VehiclesConfig.Kart cfg = cfg();
		float damage = KartPhysics.ramDamage(measuredSpeed, cfg);
		lastRammed.values().removeIf(tick -> tickCount - tick > cfg.ramCooldownTicks);
		if (damage <= 0) {
			return;
		}
		AABB area = getBoundingBox().inflate(0.3, 0, 0.3).expandTowards(moved.x, 0, moved.z);
		List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class, area, target -> target.isAlive()
				&& !(target instanceof Player) && target.getRootVehicle() != this && target.isAttackable()
				&& !lastRammed.containsKey(target.getId()));
		if (targets.isEmpty()) {
			return;
		}

		LivingEntity driver = getControllingPassenger();
		DamageSource source = driver instanceof Player player ? level.damageSources().playerAttack(player)
				: driver != null ? level.damageSources().mobAttack(driver) : level.damageSources().flyIntoWall();
		Vec3 direction = new Vec3(moved.x, 0, moved.z).normalize();
		double knockback = KartPhysics.ramKnockback(measuredSpeed, cfg);
		for (LivingEntity target : targets) {
			lastRammed.put(target.getId(), tickCount);
			target.hurtServer(level, source, damage);
			target.knockback(knockback, -direction.x, -direction.z, source, damage);
			Vec3 velocity = target.getDeltaMovement();
			target.setDeltaMovement(velocity.x, Math.max(velocity.y, cfg.ramLift), velocity.z);
			target.needsSync = true;
			level.sendParticles(ParticleTypes.CRIT, target.getX(), target.getY(0.5), target.getZ(), 8, 0.3, 0.3, 0.3, 0.2);
		}
		level.playSound(null, getX(), getY(), getZ(), VehicleRegistry.KART_RAM, SoundSource.NEUTRAL, 1.0f, 0.8f + random.nextFloat() * 0.3f);
	}

	private void clientTick() {
		double dx = getX() - xo;
		double dz = getZ() - zo;
		double[] heading = KartPhysics.heading(getYRot());
		double forward = dx * heading[0] + dz * heading[1];
		double speed = Math.sqrt(dx * dx + dz * dz);

		wheelSpinO = wheelSpin;
		wheelSpin += (float) (forward / WHEEL_RADIUS);
		steerO = steer;
		float turning = Mth.wrapDegrees(getYRot() - yRotO);
		steer += (Mth.clamp(turning * 0.12f, -0.6f, 0.6f) - steer) * 0.5f;

		// Tyre smoke when sliding sideways (drifts, ice), sparks once a drift has earned a boost (driver only knows).
		double slide = Math.abs(dx * heading[1] - dz * heading[0]);
		if (onGround() && speed > 0.15 && slide > 0.08) {
			for (int side = -1; side <= 1; side += 2) {
				Vec3 wheel = local(side * 0.7, -0.75);
				level().addParticle(ParticleTypes.SMOKE, wheel.x, getY() + 0.1, wheel.z, 0, 0.02, 0);
			}
		}
		int tier = drifting ? KartPhysics.driftTier(driftTicks, cfg()) : 0;
		if (tier > 0) {
			for (int side = -1; side <= 1; side += 2) {
				Vec3 wheel = local(side * 0.7, -0.75);
				level().addParticle(tier >= 2 ? ParticleTypes.SOUL_FIRE_FLAME : ParticleTypes.FLAME, wheel.x, getY() + 0.15, wheel.z,
						(random.nextDouble() - 0.5) * 0.05, 0.03, (random.nextDouble() - 0.5) * 0.05);
			}
		}
		if (tier > lastDriftTier) {
			level().playLocalSound(getX(), getY(), getZ(), VehicleRegistry.KART_BOOST, SoundSource.NEUTRAL, 0.4f, 1.6f + tier * 0.2f, false);
		}
		lastDriftTier = tier;

		// Exhaust puffs while someone's in it; heavy smoke when badly damaged.
		Vec3 exhaust = local(-0.35, -1.05);
		if (isVehicle() && random.nextInt(speed > 0.3 ? 2 : 6) == 0) {
			level().addParticle(ParticleTypes.SMOKE, exhaust.x, getY() + 0.55, exhaust.z, 0, 0.03, 0);
		}
		if (getHealth() < cfg().maxHealth * 0.3f && random.nextInt(3) == 0) {
			level().addParticle(ParticleTypes.LARGE_SMOKE, exhaust.x, getY() + 0.7, exhaust.z, 0, 0.05, 0);
		}
	}

	/** World position of a point in the kart's frame {x (left), z (forward)} at the kart's feet. */
	private Vec3 local(double x, double z) {
		return position().add(new Vec3(x, 0, z).yRot(-getYRot() * Mth.DEG_TO_RAD));
	}

	@Override
	public boolean hurtClient(DamageSource source) {
		return true;
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		if (isRemoved() || isInvulnerableToBase(source)) {
			return false;
		}
		Entity attacker = source.getEntity();
		if (attacker instanceof Player player) {
			if (player.getAbilities().instabuild && source.getDirectEntity() == player) {
				// Creative players can clear karts away like boats.
				discard();
				return true;
			}
			if (!cfg().playersCanDamage || hasPassenger(player)) {
				return false;
			}
		}
		if (attacker != null && hasPassenger(attacker)) {
			return false;
		}
		entityData.set(DATA_HURT_TIME, 10);
		markHurt();
		setHealth(getHealth() - damage);
		if (getHealth() <= 0) {
			breakDown(level);
		}
		return true;
	}

	/** At 0 health: riders are thrown out and the kart falls apart into a wreck (it can be repaired again). */
	public void breakDown(ServerLevel level) {
		ejectPassengers();
		KartWreckEntity wreck = VehicleRegistry.KART_WRECK.create(level, EntitySpawnReason.CONVERSION);
		if (wreck != null) {
			wreck.snapTo(getX(), getY(), getZ(), getYRot(), 0);
			wreck.setProgress(RepairProgress.brokenKart(cfg(), BlockSoulsVehicles.CONFIG.wreck));
			wreck.setStoredFuel(getFuel());
			level.addFreshEntity(wreck);
		}
		level.sendParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() + 0.5, getZ(), 30, 0.6, 0.3, 0.6, 0.05);
		level.sendParticles(ParticleTypes.EXPLOSION, getX(), getY() + 0.5, getZ(), 1, 0, 0, 0, 0);
		level.playSound(null, getX(), getY(), getZ(), VehicleRegistry.KART_BREAK, SoundSource.NEUTRAL, 1.0f, 1.0f);
		discard();
	}

	@Override
	public InteractionResult interact(Player player, InteractionHand hand, Vec3 location) {
		InteractionResult result = super.interact(player, hand, location);
		if (result != InteractionResult.PASS || player.isSecondaryUseActive()) {
			return result;
		}
		ItemStack stack = player.getItemInHand(hand);
		int fuelValue = fuelValue(stack);
		if (KartFuel.canRefuel(getFuel(), fuelValue, fuelCfg())) {
			if (level() instanceof ServerLevel level) {
				setFuel(KartFuel.refuel(getFuel(), fuelValue, fuelCfg()));
				stack.consume(1, player);
				level.sendParticles(ParticleTypes.SMOKE, getX(), getY() + 0.6, getZ(), 6, 0.4, 0.2, 0.4, 0.02);
				level.playSound(null, getX(), getY(), getZ(), VehicleRegistry.KART_REFUEL, SoundSource.NEUTRAL, 1.0f, 1.0f);
				player.sendOverlayMessage(Component.translatable("message.blocksouls_vehicles.kart_fuel",
						(int) Math.round(KartFuel.share(getFuel(), fuelCfg()) * 100)));
			}
			return InteractionResult.SUCCESS;
		}
		if (stack.is(VehicleRegistry.REPAIR_SCRAP) && getHealth() < cfg().maxHealth) {
			if (level() instanceof ServerLevel level) {
				setHealth(getHealth() + cfg().scrapRepairHealth);
				stack.consume(1, player);
				level.sendParticles(ParticleTypes.WAX_OFF, getX(), getY() + 0.6, getZ(), 8, 0.6, 0.3, 0.6, 0.1);
				level.playSound(null, getX(), getY(), getZ(), VehicleRegistry.KART_REPAIR, SoundSource.NEUTRAL, 1.0f, 1.0f);
				player.sendOverlayMessage(Component.translatable("message.blocksouls_vehicles.kart_health",
						Mth.ceil(getHealth()), Mth.ceil(cfg().maxHealth)));
			}
			return InteractionResult.SUCCESS;
		}
		if (PlayerStatus.isDowned(player) || !canAddPassenger(player)) {
			return InteractionResult.PASS;
		}
		if (!level().isClientSide() && !player.startRiding(this)) {
			return InteractionResult.PASS;
		}
		return InteractionResult.SUCCESS;
	}

	/** Fuel one of this item adds (config {@code fuel.items}), 0 if it isn't fuel. */
	public static int fuelValue(ItemStack stack) {
		if (stack.isEmpty()) {
			return 0;
		}
		Integer value = BlockSoulsVehicles.CONFIG.fuel.items.get(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
		return value == null ? 0 : value;
	}

	@Override
	protected boolean canAddPassenger(Entity passenger) {
		if (passenger instanceof Player player && PlayerStatus.isDowned(player)) {
			return false;
		}
		return getPassengers().size() < MAX_PASSENGERS;
	}

	@Override
	public @Nullable LivingEntity getControllingPassenger() {
		if (getFirstPassenger() instanceof Player player && !PlayerStatus.isDowned(player)) {
			return player;
		}
		return null;
	}

	@Override
	protected Vec3 getPassengerAttachmentPoint(Entity passenger, EntityDimensions dimensions, float scale) {
		int index = Math.max(0, getPassengers().indexOf(passenger)) % SEATS.length;
		return new Vec3(SEATS[index][0], SEAT_HEIGHT, SEATS[index][1]).yRot(-getYRot() * Mth.DEG_TO_RAD);
	}

	@Override
	protected void positionRider(Entity passenger, MoveFunction moveFunction) {
		super.positionRider(passenger, moveFunction);
		if (passenger.isLocalInstanceAuthoritative()) {
			passenger.setYRot(passenger.getYRot() + deltaRotation);
			passenger.setYHeadRot(passenger.getYHeadRot() + deltaRotation);
		}
		clampRotation(passenger);
	}

	@Override
	public void onPassengerTurned(Entity passenger) {
		passenger.yRotO += clampRotation(passenger);
	}

	/** Riders face forward and can look up to 105° to either side (same as boats). */
	private float clampRotation(Entity passenger) {
		passenger.setYBodyRot(getYRot());
		if (passenger instanceof Mob mob) {
			passenger.setYRot(getYRot());
			mob.clampHeadRotationToBody();
			return Mth.wrapDegrees(getYRot() - passenger.getYRot());
		}
		float delta = Mth.wrapDegrees(passenger.getYRot() - getYRot());
		float clamped = Mth.clamp(delta, -105.0f, 105.0f);
		passenger.setYRot(passenger.getYRot() + clamped - delta);
		return clamped - delta;
	}

	@Override
	public boolean isPickable() {
		return !isRemoved();
	}

	@Override
	public boolean canBeCollidedWith(@Nullable Entity other) {
		return true;
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		setHealth(input.getFloatOr("health", cfg().maxHealth));
		// Karts saved before fuel existed get a full tank.
		setFuel(input.getIntOr("fuel", fuelCfg().fuelCapacity));
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		output.putFloat("health", getHealth());
		output.putInt("fuel", getFuel());
	}
}
