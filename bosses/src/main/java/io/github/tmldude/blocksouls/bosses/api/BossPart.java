package io.github.tmldude.blocksouls.bosses.api;

import org.jspecify.annotations.Nullable;

import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/**
 * An extra hitbox of a boss (a weak point like a dragon's head), on the same mechanism as vanilla's
 * {@code EnderDragonPart}: never added to the level, positioned by its boss every tick, and found by attacks and
 * bullets through {@link BossParts}. Damage goes to the boss times {@link #damageMultiplier()}.
 *
 * <p>Create parts in the boss constructor with {@link BossEntity#addPart}.
 */
public class BossPart extends Entity {
	private final BossEntity boss;
	private final String partName;
	private final EntityDimensions size;
	private final double damageMultiplier;
	private final Vec3 offset;

	/**
	 * @param offset part center relative to the boss's feet in the boss's frame: x = left, y = up, z = forward
	 */
	BossPart(BossEntity boss, String name, float width, float height, double damageMultiplier, Vec3 offset) {
		super(boss.getType(), boss.level());
		this.boss = boss;
		this.partName = name;
		this.size = EntityDimensions.scalable(width, height);
		this.damageMultiplier = damageMultiplier;
		this.offset = offset;
		refreshDimensions();
	}

	public BossEntity boss() {
		return boss;
	}

	public String partName() {
		return partName;
	}

	public double damageMultiplier() {
		return damageMultiplier;
	}

	/** Moves the part to its offset around the boss's current position and yaw. */
	void follow() {
		double yaw = Math.toRadians(boss.getYRot());
		double sin = Math.sin(yaw);
		double cos = Math.cos(yaw);
		// Minecraft yaw 0 faces +z; "left" is +x at yaw 0.
		double x = offset.x * cos - offset.z * sin;
		double z = offset.x * sin + offset.z * cos;
		xo = getX();
		yo = getY();
		zo = getZ();
		setPos(boss.getX() + x, boss.getY() + offset.y - size.height() / 2, boss.getZ() + z);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder entityData) {
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
	}

	@Override
	public boolean isPickable() {
		return boss.isAlive();
	}

	@Override
	public boolean isAlive() {
		return boss.isAlive();
	}

	@Override
	public @Nullable ItemStack getPickResult() {
		return boss.getPickResult();
	}

	@Override
	public final boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		return !isInvulnerableToBase(source) && boss.hurtFromPart(level, this, source, damage);
	}

	@Override
	public boolean is(Entity other) {
		return this == other || boss == other;
	}

	@Override
	public Packet<ClientGamePacketListener> getAddEntityPacket(ServerEntity serverEntity) {
		throw new UnsupportedOperationException();
	}

	@Override
	public EntityDimensions getDimensions(Pose pose) {
		return size;
	}

	@Override
	public boolean shouldBeSaved() {
		return false;
	}
}
