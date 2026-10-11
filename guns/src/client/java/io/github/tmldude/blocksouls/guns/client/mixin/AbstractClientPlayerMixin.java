package io.github.tmldude.blocksouls.guns.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;

import io.github.tmldude.blocksouls.guns.client.GunClient;
import io.github.tmldude.blocksouls.guns.item.GunItem;

/** Aiming down sights zooms by the gun's {@code adsZoom} (first person only). */
@Mixin(AbstractClientPlayer.class)
abstract class AbstractClientPlayerMixin {
	@Inject(method = "getFieldOfViewModifier", at = @At("RETURN"), cancellable = true)
	private void blocksouls_guns$adsZoom(boolean firstPerson, float effectScale, CallbackInfoReturnable<Float> cir) {
		AbstractClientPlayer self = (AbstractClientPlayer) (Object) this;
		if (firstPerson && self == Minecraft.getInstance().player && GunClient.isAiming() && self.getUseItem().getItem() instanceof GunItem gun) {
			cir.setReturnValue(cir.getReturnValueF() / (float) gun.stats().adsZoom());
		}
	}
}
