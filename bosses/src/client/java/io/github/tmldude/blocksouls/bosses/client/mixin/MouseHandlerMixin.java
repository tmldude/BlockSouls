package io.github.tmldude.blocksouls.bosses.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.MouseHandler;

import io.github.tmldude.blocksouls.bosses.client.lockon.LockOn;

/** Lock-on: ease the camera once per frame after mouse look, and let the wheel cycle targets while locked. */
@Mixin(MouseHandler.class)
abstract class MouseHandlerMixin {
	@Inject(method = "handleAccumulatedMovement", at = @At("TAIL"))
	private void blocksouls_bosses$lockOnFrame(CallbackInfo ci) {
		LockOn.onFrame();
	}

	@Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
	private void blocksouls_bosses$lockOnScroll(long handle, double xoffset, double yoffset, CallbackInfo ci) {
		if (LockOn.onScroll(yoffset)) {
			ci.cancel();
		}
	}
}
