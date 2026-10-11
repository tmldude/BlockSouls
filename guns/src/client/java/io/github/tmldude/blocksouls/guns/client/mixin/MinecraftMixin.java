package io.github.tmldude.blocksouls.guns.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.Minecraft;

import io.github.tmldude.blocksouls.guns.client.GunClient;

/** With a gun in hand, attack fires instead of hitting or mining. */
@Mixin(Minecraft.class)
abstract class MinecraftMixin {
	@Inject(method = "handleKeybinds", at = @At("HEAD"))
	private void blocksouls_guns$captureAttackClicks(CallbackInfo ci) {
		GunClient.captureAttackClicks();
	}

	@Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
	private void blocksouls_guns$noMeleeWithGun(CallbackInfoReturnable<Boolean> cir) {
		if (GunClient.blocksAttack()) {
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "continueAttack", at = @At("HEAD"), cancellable = true)
	private void blocksouls_guns$noMiningWithGun(boolean down, CallbackInfo ci) {
		if (GunClient.blocksAttack()) {
			ci.cancel();
		}
	}
}
