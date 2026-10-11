package io.github.tmldude.blocksouls.vehicles.kart;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

import io.github.tmldude.blocksouls.common.player.PlayerStatus;
import io.github.tmldude.blocksouls.vehicles.BlockSoulsVehicles;
import io.github.tmldude.blocksouls.vehicles.VehicleRegistry;
import io.github.tmldude.blocksouls.vehicles.VehiclesConfig;

/**
 * A broken-down kart. It never moves (except falling), never despawns and shrugs off damage, explosions
 * included. Right-click it with wheels, an engine and scrap, one part per click; when every part is fitted it
 * turns into a {@link KartEntity}.
 */
public class KartWreckEntity extends Entity {
	private static final EntityDataAccessor<Integer> DATA_WHEELS = SynchedEntityData.defineId(KartWreckEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_ENGINES = SynchedEntityData.defineId(KartWreckEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_SCRAP = SynchedEntityData.defineId(KartWreckEntity.class, EntityDataSerializers.INT);

	/** The fuel the kart had when it broke down; {@link KartFuel#UNKNOWN} for a wreck that never ran. Server only. */
	private int storedFuel = KartFuel.UNKNOWN;

	public KartWreckEntity(EntityType<? extends KartWreckEntity> type, Level level) {
		super(type, level);
		this.blocksBuilding = true;
	}

	private static VehiclesConfig.Wreck cfg() {
		return BlockSoulsVehicles.CONFIG.wreck;
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(DATA_WHEELS, 0);
		builder.define(DATA_ENGINES, 0);
		builder.define(DATA_SCRAP, 0);
	}

	public RepairProgress getProgress() {
		return new RepairProgress(entityData.get(DATA_WHEELS), entityData.get(DATA_ENGINES), entityData.get(DATA_SCRAP));
	}

	public void setProgress(RepairProgress progress) {
		entityData.set(DATA_WHEELS, progress.wheels());
		entityData.set(DATA_ENGINES, progress.engines());
		entityData.set(DATA_SCRAP, progress.scrap());
	}

	public int getStoredFuel() {
		return storedFuel;
	}

	public void setStoredFuel(int fuel) {
		storedFuel = fuel < 0 ? KartFuel.UNKNOWN : fuel;
	}

	public static RepairProgress.@Nullable Part partFor(ItemStack stack) {
		if (stack.is(VehicleRegistry.WHEEL)) {
			return RepairProgress.Part.WHEEL;
		}
		if (stack.is(VehicleRegistry.ENGINE)) {
			return RepairProgress.Part.ENGINE;
		}
		if (stack.is(VehicleRegistry.REPAIR_SCRAP)) {
			return RepairProgress.Part.SCRAP;
		}
		return null;
	}

	@Override
	protected double getDefaultGravity() {
		return 0.04;
	}

	@Override
	public void tick() {
		super.tick();
		// It only ever falls (e.g. placed above uneven ground); it never slides.
		if (!onGround() || getDeltaMovement().y > 0) {
			applyGravity();
			setDeltaMovement(0, getDeltaMovement().y * 0.98, 0);
			move(MoverType.SELF, getDeltaMovement());
		} else {
			setDeltaMovement(Vec3.ZERO);
		}

		if (level().isClientSide() && random.nextInt(40) == 0) {
			Vec3 engine = position().add(new Vec3(0, 0, -0.85).yRot(-getYRot() * Mth.DEG_TO_RAD));
			level().addParticle(ParticleTypes.SMOKE, engine.x, getY() + 0.6, engine.z, 0, 0.02, 0);
		}
	}

	@Override
	public InteractionResult interact(Player player, InteractionHand hand, Vec3 location) {
		InteractionResult result = super.interact(player, hand, location);
		if (result != InteractionResult.PASS || PlayerStatus.isDowned(player)) {
			return result;
		}
		ItemStack stack = player.getItemInHand(hand);
		RepairProgress.Part part = partFor(stack);
		RepairProgress progress = getProgress();
		if (part == null || !progress.needs(part, cfg())) {
			// Wrong item or a part it already has: just show what's still missing.
			if (!level().isClientSide()) {
				player.sendOverlayMessage(progressMessage(progress));
			}
			return part == null && !stack.isEmpty() ? InteractionResult.PASS : InteractionResult.SUCCESS;
		}
		if (level() instanceof ServerLevel level) {
			fitPart(level, part, player);
			stack.consume(1, player);
		}
		return InteractionResult.SUCCESS;
	}

	/** Server: fits one part, and completes the repair if that was the last one. */
	public void fitPart(ServerLevel level, RepairProgress.Part part, @Nullable Player player) {
		RepairProgress progress = getProgress().add(part, cfg());
		setProgress(progress);
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, getX(), getY() + 0.5, getZ(), 10, 0.6, 0.3, 0.6, 0.1);
		level.playSound(null, getX(), getY(), getZ(), VehicleRegistry.WRECK_FIT_PART, SoundSource.NEUTRAL, 1.0f,
				0.8f + 0.4f * (float) progress.fraction(cfg()));
		if (player != null) {
			player.sendOverlayMessage(progressMessage(progress));
		}
		if (progress.complete(cfg())) {
			repair(level);
		}
	}

	/** Server: turns this wreck into a working kart, whatever parts it has. */
	public @Nullable KartEntity repair(ServerLevel level) {
		KartEntity kart = VehicleRegistry.KART.create(level, EntitySpawnReason.CONVERSION);
		if (kart == null) {
			return null;
		}
		kart.snapTo(getX(), getY(), getZ(), getYRot(), 0);
		kart.setHealth(BlockSoulsVehicles.CONFIG.kart.maxHealth);
		kart.setFuel(KartFuel.afterRepair(storedFuel, BlockSoulsVehicles.CONFIG.fuel));
		level.addFreshEntity(kart);
		level.sendParticles(ParticleTypes.POOF, getX(), getY() + 0.5, getZ(), 25, 0.7, 0.3, 0.7, 0.05);
		level.sendParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() + 0.5, getZ(), 10, 0.5, 0.2, 0.5, 0.02);
		level.playSound(null, getX(), getY(), getZ(), VehicleRegistry.WRECK_REPAIRED, SoundSource.NEUTRAL, 1.0f, 1.0f);
		discard();
		return kart;
	}

	public static Component progressMessage(RepairProgress progress) {
		VehiclesConfig.Wreck cfg = cfg();
		return Component.translatable("message.blocksouls_vehicles.wreck_progress",
				progress.wheels(), cfg.wheels, progress.engines(), cfg.engines, progress.scrap(), cfg.scrap);
	}

	@Override
	public boolean hurtClient(DamageSource source) {
		return true;
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		// It's already a wreck. Creative players can clear it away like a boat.
		if (!isRemoved() && source.getEntity() instanceof Player player && player.getAbilities().instabuild
				&& source.getDirectEntity() == player) {
			discard();
			return true;
		}
		return false;
	}

	@Override
	public boolean ignoreExplosion(Explosion explosion) {
		return true;
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
		setProgress(new RepairProgress(input.getIntOr("wheels", 0), input.getIntOr("engines", 0), input.getIntOr("scrap", 0)));
		setStoredFuel(input.getIntOr("fuel", KartFuel.UNKNOWN));
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		RepairProgress progress = getProgress();
		output.putInt("wheels", progress.wheels());
		output.putInt("engines", progress.engines());
		output.putInt("scrap", progress.scrap());
		if (storedFuel != KartFuel.UNKNOWN) {
			output.putInt("fuel", storedFuel);
		}
	}
}
