package io.github.tmldude.blocksouls.bosses.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

import io.github.tmldude.blocksouls.bosses.api.BossParts;

/** Attack and interact packets that name a boss part resolve to it (vanilla only knows dragon parts). */
@Mixin(ServerLevel.class)
abstract class ServerLevelMixin {
	@Inject(method = "getEntityOrPart", at = @At("RETURN"), cancellable = true)
	private void blocksouls_bosses$findPart(int id, CallbackInfoReturnable<Entity> cir) {
		if (cir.getReturnValue() == null) {
			Entity part = BossParts.byId((ServerLevel) (Object) this, id);
			if (part != null) {
				cir.setReturnValue(part);
			}
		}
	}
}
