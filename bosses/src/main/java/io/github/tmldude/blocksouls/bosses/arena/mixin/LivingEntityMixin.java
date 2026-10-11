package io.github.tmldude.blocksouls.bosses.arena.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import io.github.tmldude.blocksouls.bosses.arena.ArenaSeal;

/** Random teleports (chorus fruit) never land a player across an engaged arena seal; vanilla tries another spot. */
@Mixin(LivingEntity.class)
abstract class LivingEntityMixin {
	@Inject(method = "canRandomlyTeleportTo", at = @At("HEAD"), cancellable = true)
	private void blocksouls$arenaSeal(double x, double y, double z, CallbackInfoReturnable<Boolean> cir) {
		if ((Object) this instanceof ServerPlayer player && !ArenaSeal.allowsTeleport(player, new Vec3(x, y, z), false)) {
			cir.setReturnValue(false);
		}
	}
}
