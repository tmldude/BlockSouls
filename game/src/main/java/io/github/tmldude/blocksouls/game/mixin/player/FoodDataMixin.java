package io.github.tmldude.blocksouls.game.mixin.player;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.food.FoodData;

import io.github.tmldude.blocksouls.game.player.boon.rule.BoonRules;

/** Build rules can switch natural (food) regeneration off (the Red Ledger keystone). */
@Mixin(FoodData.class)
abstract class FoodDataMixin {
	@Redirect(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayer;heal(F)V"))
	private void blocksouls$naturalRegen(ServerPlayer player, float amount) {
		if (!BoonRules.blocksNaturalRegen(player)) {
			player.heal(amount);
		}
	}
}
