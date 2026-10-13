package io.github.tmldude.blocksouls.mobs.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

import io.github.tmldude.blocksouls.mobs.affix.MobAffixes;

/** Frosted: less damage from projectiles and bullets (Fabric has no event that changes the amount). */
@Mixin(LivingEntity.class)
abstract class LivingEntityAffixMixin {
	@ModifyVariable(method = "hurtServer", at = @At("HEAD"), argsOnly = true)
	private float blocksouls_mobs$frostedProjectiles(float damage, ServerLevel level, DamageSource source) {
		return damage * MobAffixes.damageFactor((LivingEntity) (Object) this, source);
	}
}
