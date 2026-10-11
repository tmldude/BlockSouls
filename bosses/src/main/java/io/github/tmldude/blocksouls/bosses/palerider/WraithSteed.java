package io.github.tmldude.blocksouls.bosses.palerider;

import java.util.UUID;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * The Wraith Steed: a spectral horse bound to whoever blew the whistle. Fast, jumps again in mid-air, never takes
 * fall damage, can't be leashed, bred, saddled or ridden by anyone else. Fades a few seconds after its rider gets off;
 * if it's killed, the whistle goes on a long cooldown.
 *
 * <p>Ridden movement is client-authoritative for the rider (vanilla horse rules), so the air jump runs in
 * {@link #tickRidden} on the rider's client; this class is shared code, so the same logic is there.
 */
public class WraithSteed extends AbstractHorse {
	private int airJumpsUsed;
	private int unriddenTicks;

	public WraithSteed(EntityType<? extends WraithSteed> type, Level level) {
		super(type, level);
		setTamed(true);
	}

	private static PaleRiderConfig.Steed config() {
		return PaleRider.config().steed;
	}

	public static AttributeSupplier.Builder createAttributes() {
		PaleRiderConfig.Steed c = config();
		return createBaseHorseAttributes()
				.add(Attributes.MAX_HEALTH, c.health)
				.add(Attributes.MOVEMENT_SPEED, c.speed)
				.add(Attributes.JUMP_STRENGTH, c.jumpStrength)
				.add(Attributes.SAFE_FALL_DISTANCE, 1024);
	}

	@Override
	protected void randomizeAttributes(net.minecraft.util.RandomSource random) {
		// Every steed is the same: no random stats.
	}

	@Override
	protected void registerGoals() {
		goalSelector.addGoal(0, new FloatGoal(this));
		goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 6.0F));
	}

	// ---------------------------------------------------------------------------------------------------------
	// Owner

	/** Binds the steed to {@code player}. */
	public void bind(Player player) {
		setOwner(player);
		setTamed(true);
	}

	public @Nullable UUID ownerId() {
		EntityReference<LivingEntity> owner = getOwnerReference();
		return owner == null ? null : owner.getUUID();
	}

	public boolean isOwnedBy(Entity entity) {
		UUID owner = ownerId();
		return owner != null && owner.equals(entity.getUUID());
	}

	// ---------------------------------------------------------------------------------------------------------
	// Riding

	/** Always rideable: the spectral steed needs no saddle (and has no saddle slot to take one from). */
	@Override
	public boolean isSaddled() {
		return true;
	}

	@Override
	public InteractionResult mobInteract(Player player, InteractionHand hand) {
		if (isVehicle()) {
			return InteractionResult.PASS;
		}
		if (!level().isClientSide() && !isOwnedBy(player)) {
			return InteractionResult.FAIL;
		}
		doPlayerRide(player);
		return InteractionResult.SUCCESS;
	}

	@Override
	protected boolean canAddPassenger(Entity passenger) {
		// The client doesn't know the owner; the server decides who mounts.
		return getPassengers().isEmpty() && (level().isClientSide() || isOwnedBy(passenger));
	}

	@Override
	protected void tickRidden(Player controller, Vec3 riddenInput) {
		if (isLocalInstanceAuthoritative()) {
			if (onGround()) {
				airJumpsUsed = 0;
			} else if (playerJumpPendingScale > 0) {
				if (airJumpsUsed < config().airJumps) {
					airJumpsUsed++;
					float charge = Math.max(playerJumpPendingScale, (float) config().airJumpMinCharge);
					executeRidersJump(charge, riddenInput);
					playJumpSound();
					airJumpPuff();
				}
				// Spent or not, a charge released in the air doesn't carry over into a jump on landing.
				playerJumpPendingScale = 0;
			}
		}
		super.tickRidden(controller, riddenInput);
	}

	private void airJumpPuff() {
		for (int i = 0; i < 12; i++) {
			double a = i * Math.PI * 2 / 12;
			level().addParticle(ParticleTypes.CLOUD, getX() + Math.cos(a) * 0.8, getY(), getZ() + Math.sin(a) * 0.8, Math.cos(a) * 0.08, -0.05,
					Math.sin(a) * 0.08);
		}
	}

	@Override
	public boolean causeFallDamage(double fallDistance, float damageModifier, DamageSource damageSource) {
		return false;
	}

	// ---------------------------------------------------------------------------------------------------------
	// Never: leash, breed, inventory, food

	@Override
	public boolean canBeLeashed() {
		return false;
	}

	@Override
	public boolean isFood(ItemStack itemStack) {
		return false;
	}

	@Override
	public boolean canMate(Animal partner) {
		return false;
	}

	@Override
	public @Nullable AgeableMob getBreedOffspring(ServerLevel level, AgeableMob partner) {
		return null;
	}

	@Override
	public boolean canUsePortal(boolean ignorePassenger) {
		return false;
	}

	@Override
	public boolean removeWhenFarAway(double distSqr) {
		return false;
	}

	// ---------------------------------------------------------------------------------------------------------
	// Lifetime

	@Override
	public void tick() {
		super.tick();
		if (level().isClientSide()) {
			if (isAlive() && tickCount % 3 == 0) {
				level().addParticle(ParticleTypes.WHITE_ASH, getRandomX(0.8), getRandomY(), getRandomZ(0.8), 0, 0.02, 0);
			}
			return;
		}
		if (!(level() instanceof ServerLevel level) || !isAlive()) {
			return;
		}
		WraithSteeds.claim(this);
		unriddenTicks = isVehicle() ? 0 : unriddenTicks + 1;
		if (unriddenTicks >= config().fadeTicks) {
			fade(level);
		}
	}

	/** Dissolves into mist (no death, no drops). */
	public void fade(ServerLevel level) {
		level.sendParticles(ParticleTypes.CLOUD, getX(), getY() + 1, getZ(), 30, 0.6, 0.6, 0.6, 0.03);
		level.sendParticles(ParticleTypes.SOUL, getX(), getY() + 1, getZ(), 10, 0.4, 0.5, 0.4, 0.02);
		level.playSound(null, getX(), getY(), getZ(), PaleRiderSounds.STEED_FADE, SoundSource.NEUTRAL, 1.0f, 1.2f);
		discard();
	}

	/** Ticks since its rider got off (tests). */
	public int unriddenTicks() {
		return unriddenTicks;
	}

	@Override
	public void die(DamageSource source) {
		boolean wasAlive = !isRemoved() && !dead;
		super.die(source);
		if (wasAlive && level() instanceof ServerLevel level) {
			UUID owner = ownerId();
			ServerPlayer player = owner == null ? null : level.getServer().getPlayerList().getPlayer(owner);
			if (player != null) {
				player.getCooldowns().addCooldown(new ItemStack(WraithSteeds.WHISTLE), config().deathCooldownTicks);
			}
			level.sendParticles(ParticleTypes.SOUL, getX(), getY() + 1, getZ(), 20, 0.5, 0.6, 0.5, 0.03);
		}
	}

	@Override
	protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean killedByPlayer) {
	}

	@Override
	protected boolean shouldDropLoot(ServerLevel level) {
		return false;
	}

	@Override
	public int getBaseExperienceReward(ServerLevel level) {
		return 0;
	}

	// ---------------------------------------------------------------------------------------------------------
	// Sounds: a horse, pitched down and hollow

	@Override
	protected SoundEvent getAmbientSound() {
		return SoundEvents.SKELETON_HORSE_AMBIENT;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.SKELETON_HORSE_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.SKELETON_HORSE_DEATH;
	}

	@Override
	protected @Nullable SoundEvent getAngrySound() {
		return SoundEvents.HORSE_ANGRY;
	}

	@Override
	public float getVoicePitch() {
		return super.getVoicePitch() * 0.8f;
	}
}
