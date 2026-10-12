package io.github.tmldude.blocksouls.game.mixin.player;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

import io.github.tmldude.blocksouls.game.player.downed.DownedPlay;

/** Downed gameplay: damage dealt by a downed player (the crawl pistol) is scaled before it's applied. */
@Mixin(LivingEntity.class)
abstract class LivingEntityDamageMixin {
	@ModifyVariable(method = "hurtServer", at = @At("HEAD"), argsOnly = true)
	private float blocksouls$scaleDownedDamage(float amount, ServerLevel level, DamageSource source) {
		return DownedPlay.scaleDamage(source, amount);
	}
}
