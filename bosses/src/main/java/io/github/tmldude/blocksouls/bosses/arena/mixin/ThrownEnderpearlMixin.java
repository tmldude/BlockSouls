package io.github.tmldude.blocksouls.bosses.arena.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl;
import net.minecraft.world.phys.HitResult;

import io.github.tmldude.blocksouls.bosses.arena.ArenaSeal;

/** An ender pearl that would carry its thrower across an engaged arena seal fizzles instead (no teleport, no damage). */
@Mixin(ThrownEnderpearl.class)
abstract class ThrownEnderpearlMixin {
	@Inject(method = "onHit", at = @At("HEAD"), cancellable = true)
	private void blocksouls$arenaSeal(HitResult hitResult, CallbackInfo ci) {
		ThrownEnderpearl pearl = (ThrownEnderpearl) (Object) this;
		if (pearl.level() instanceof ServerLevel && !pearl.isRemoved() && pearl.getOwner() instanceof ServerPlayer player
				&& !ArenaSeal.allowsTeleport(player, pearl.oldPosition(), true)) {
			pearl.discard();
			ci.cancel();
		}
	}
}
