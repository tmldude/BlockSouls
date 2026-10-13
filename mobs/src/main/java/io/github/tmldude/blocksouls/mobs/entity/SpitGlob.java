package io.github.tmldude.blocksouls.mobs.entity;

import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

import io.github.tmldude.blocksouls.common.CommonTags;
import io.github.tmldude.blocksouls.mobs.BlockSoulsMobs;
import io.github.tmldude.blocksouls.mobs.MobsConfig;
import io.github.tmldude.blocksouls.mobs.registry.MobEntities;

/** The Spitter's glob of bile: arcs, poisons what it hits, splats on anything. Never breaks blocks. */
public class SpitGlob extends ThrowableItemProjectile {
	public SpitGlob(EntityType<? extends SpitGlob> type, Level level) {
		super(type, level);
	}

	public SpitGlob(Level level, LivingEntity owner) {
		super(MobEntities.SPIT_GLOB, owner, level, new ItemStack(Items.SLIME_BALL));
	}

	@Override
	protected Item getDefaultItem() {
		return Items.SLIME_BALL;
	}

	@Override
	protected double getDefaultGravity() {
		return 0.04;
	}

	@Override
	protected void onHitEntity(EntityHitResult hitResult) {
		super.onHitEntity(hitResult);
		Entity entity = hitResult.getEntity();
		if (level() instanceof ServerLevel level && !entity.is(CommonTags.WAVE_MOBS)) {
			MobsConfig.Spitter config = BlockSoulsMobs.CONFIG.spitter;
			if (entity.hurtServer(level, damageSources().mobProjectile(this, getOwner() instanceof LivingEntity owner ? owner : null),
					(float) config.spitDamage) && config.poisonSeconds > 0 && entity instanceof LivingEntity living) {
				living.addEffect(new MobEffectInstance(MobEffects.POISON, config.poisonSeconds * 20, 0), getOwner());
			}
		}
	}

	@Override
	protected void onHit(HitResult hitResult) {
		super.onHit(hitResult);
		if (level() instanceof ServerLevel level) {
			level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, Items.SLIME_BALL), getX(), getY(), getZ(), 16, 0.25, 0.25, 0.25, 0.08);
			discard();
		}
	}
}
