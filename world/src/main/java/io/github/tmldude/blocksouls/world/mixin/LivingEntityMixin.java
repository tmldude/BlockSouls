package io.github.tmldude.blocksouls.world.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

import io.github.tmldude.blocksouls.world.armor.SetBonuses;

@Mixin(LivingEntity.class)
abstract class LivingEntityMixin {
	/** Scrap-plate set bonus: cuts incoming explosion damage. */
	@ModifyVariable(method = "hurtServer", at = @At("HEAD"), argsOnly = true)
	private float blocksouls_world$scrapPlateBlastResistance(float damage, ServerLevel level, DamageSource source, float original) {
		return SetBonuses.modifyIncoming((LivingEntity) (Object) this, source, damage);
	}
}
