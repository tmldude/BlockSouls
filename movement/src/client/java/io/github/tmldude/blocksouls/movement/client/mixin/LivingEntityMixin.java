package io.github.tmldude.blocksouls.movement.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.LivingEntity;

import io.github.tmldude.blocksouls.movement.client.jump.JumpHandler;

@Mixin(LivingEntity.class)
abstract class LivingEntityMixin {
	@Inject(method = "jumpFromGround", at = @At("TAIL"))
	private void blocksouls$afterJump(CallbackInfo ci) {
		if ((Object) this instanceof LocalPlayer player) {
			JumpHandler.onJump(player);
		}
	}
}
