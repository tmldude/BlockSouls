package io.github.tmldude.blocksouls.game.mixin.player;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

import io.github.tmldude.blocksouls.game.player.classes.PlayerClasses;
import io.github.tmldude.blocksouls.game.player.downed.DownedPlay;

/**
 * Downed gameplay: damage dealt by a downed player (the crawl pistol) is scaled before it's applied. Classes: the
 * class's heal multiplier (the Striker's 75% healing) scales every heal on the player, on top of build rules.
 */
@Mixin(LivingEntity.class)
abstract class LivingEntityDamageMixin {
	@ModifyVariable(method = "hurtServer", at = @At("HEAD"), argsOnly = true)
	private float blocksouls$scaleDownedDamage(float amount, ServerLevel level, DamageSource source) {
		return DownedPlay.scaleDamage(source, amount);
	}

	@ModifyVariable(method = "heal", at = @At("HEAD"), argsOnly = true)
	private float blocksouls$classHealMultiplier(float amount) {
		if ((Object) this instanceof ServerPlayer player) {
			return amount * (float) PlayerClasses.rules(player).healMultiplier();
		}
		return amount;
	}
}
