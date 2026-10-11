package io.github.tmldude.blocksouls.bosses.raid;

import java.util.Optional;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * A crystal of the Crystal Order: sits on a pillar top like a vanilla End crystal, but breaking it asks its dragon
 * whether it was the right one (vanilla crystals explode for 6; these never hurt anyone). While the obelisk shows the
 * order, the crystal whose turn it is glows in its sequence color and beams at the obelisk.
 */
public class RaidCrystal extends Entity {
	private static final EntityDataAccessor<Integer> DATA_COLOR = SynchedEntityData.defineId(RaidCrystal.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Boolean> DATA_SEALED = SynchedEntityData.defineId(RaidCrystal.class, EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<Optional<BlockPos>> DATA_BEAM = SynchedEntityData.defineId(RaidCrystal.class,
			EntityDataSerializers.OPTIONAL_BLOCK_POS);

	/** Client animation clock (spin and bob), like vanilla's {@code EndCrystal.time}. */
	public int time;
	private @Nullable UUID owner;
	private int slot = -1;

	public RaidCrystal(EntityType<? extends RaidCrystal> type, Level level) {
		super(type, level);
		this.blocksBuilding = true;
		this.time = random.nextInt(100000);
	}

	public void setOwner(UUID dragon, int slot) {
		this.owner = dragon;
		this.slot = slot;
	}

	public @Nullable UUID owner() {
		return owner;
	}

	public int slot() {
		return slot;
	}

	/** Sequence color (RGB) while it's this crystal's turn on the obelisk, or 0 when dark. */
	public int color() {
		return entityData.get(DATA_COLOR);
	}

	/** Sealed crystals can't be broken (hits clink off); the held plates open them. */
	public boolean sealed() {
		return entityData.get(DATA_SEALED);
	}

	public void setSealed(boolean sealed) {
		entityData.set(DATA_SEALED, sealed);
	}

	public @Nullable BlockPos beamTarget() {
		return entityData.get(DATA_BEAM).orElse(null);
	}

	/** Lights the crystal in {@code rgb} and beams it at {@code beam}; {@code rgb} 0 turns both off. */
	public void light(int rgb, @Nullable BlockPos beam) {
		entityData.set(DATA_COLOR, rgb);
		entityData.set(DATA_BEAM, Optional.ofNullable(rgb == 0 ? null : beam));
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(DATA_COLOR, 0);
		builder.define(DATA_SEALED, false);
		builder.define(DATA_BEAM, Optional.empty());
	}

	@Override
	public void tick() {
		super.tick();
		time++;
		if (level().isClientSide() && color() != 0 && time % 2 == 0) {
			level().addParticle(ParticleTypes.END_ROD, getX() + (random.nextDouble() - 0.5) * 1.6, getY() + 1 + random.nextDouble(),
					getZ() + (random.nextDouble() - 0.5) * 1.6, 0, 0.02, 0);
		}
	}

	@Override
	public boolean isPickable() {
		return true;
	}

	@Override
	public boolean hurtClient(DamageSource source) {
		return !isInvulnerableToBase(source) && source.getEntity() instanceof Player;
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		if (isRemoved() || isInvulnerableToBase(source) || !(source.getEntity() instanceof ServerPlayer player)) {
			return false;
		}
		if (owner != null && level.getEntity(owner) instanceof FinalDragon dragon && dragon.isAlive()) {
			dragon.onCrystalBroken(this, player);
		} else {
			// A stray crystal (its dragon is gone): it just shatters.
			level.sendParticles(ParticleTypes.END_ROD, getX(), getY() + 1, getZ(), 20, 0.4, 0.4, 0.4, 0.1);
			level.playSound(null, getX(), getY(), getZ(), FinalRaidSounds.CRYSTAL_SHATTER, SoundSource.HOSTILE, 1.5f, 1f);
			discard();
		}
		return true;
	}

	@Override
	public ItemStack getPickResult() {
		return new ItemStack(Items.END_CRYSTAL);
	}

	@Override
	public boolean shouldRenderAtSqrDistance(double distance) {
		// Pillar tops are far from the island center; the crystals must stay readable from there.
		return distance < 256 * 256;
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		if (owner != null) {
			output.store("RaidOwner", UUIDUtil.CODEC, owner);
		}
		output.putInt("RaidSlot", slot);
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		owner = input.read("RaidOwner", UUIDUtil.CODEC).orElse(null);
		slot = input.getIntOr("RaidSlot", -1);
	}
}
