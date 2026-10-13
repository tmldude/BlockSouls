package io.github.tmldude.blocksouls.game.mixin.player;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

import io.github.tmldude.blocksouls.game.player.boon.rule.BoonRules;

/** Build rules can scale the healing a player receives (the Hemorrhage curse). */
@Mixin(LivingEntity.class)
abstract class LivingEntityMixin {
	@ModifyVariable(method = "heal", at = @At("HEAD"), argsOnly = true)
	private float blocksouls$healMultiplier(float amount) {
		if ((Object) this instanceof ServerPlayer player) {
			return amount * BoonRules.healMultiplier(player);
		}
		return amount;
	}
}
