package io.github.tmldude.blocksouls.mobs.entity;

import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

import io.github.tmldude.blocksouls.common.CommonTags;
import io.github.tmldude.blocksouls.mobs.BlockSoulsMobs;
import io.github.tmldude.blocksouls.mobs.registry.MobEntities;

/** A chunk of rubble thrown by the Mutant Brute. Hurts what it hits, shatters on impact, never breaks blocks. */
public class Debris extends ThrowableItemProjectile {
	public Debris(EntityType<? extends Debris> type, Level level) {
		super(type, level);
	}

	public Debris(Level level, LivingEntity owner) {
		super(MobEntities.DEBRIS, owner, level, new ItemStack(Items.COBBLESTONE));
	}

	@Override
	protected Item getDefaultItem() {
		return Items.COBBLESTONE;
	}

	@Override
	protected double getDefaultGravity() {
		return 0.05;
	}

	@Override
	protected void onHitEntity(EntityHitResult hitResult) {
		super.onHitEntity(hitResult);
		Entity entity = hitResult.getEntity();
		if (level() instanceof ServerLevel level && !entity.is(CommonTags.WAVE_MOBS)) {
			entity.hurtServer(level, damageSources().thrown(this, getOwner()), (float) BlockSoulsMobs.CONFIG.mutantBrute.debrisDamage);
		}
	}

	@Override
	protected void onHit(HitResult hitResult) {
		super.onHit(hitResult);
		if (level() instanceof ServerLevel level) {
			level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.COBBLESTONE.defaultBlockState()),
					getX(), getY(), getZ(), 20, 0.3, 0.3, 0.3, 0.1);
			discard();
		}
	}
}
