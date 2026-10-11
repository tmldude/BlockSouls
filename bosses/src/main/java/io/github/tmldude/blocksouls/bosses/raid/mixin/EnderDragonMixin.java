package io.github.tmldude.blocksouls.bosses.raid.mixin;

import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.level.dimension.end.EnderDragonFight;

import io.github.tmldude.blocksouls.bosses.raid.FinalRaidEnd;

/**
 * The end of the final raid's phase 1: the Ender Dragon of the End fight doesn't rise in light and burst when it dies;
 * it falls, and the final dragon takes its place ({@link FinalRaidEnd#vanillaDragonFell}). Dragons outside a dragon
 * fight (summoned elsewhere) die as usual.
 */
@Mixin(EnderDragon.class)
abstract class EnderDragonMixin {
	@Shadow
	private @Nullable EnderDragonFight dragonFight;

	@Inject(method = "tickDeath", at = @At("HEAD"), cancellable = true)
	private void blocksouls$fall(CallbackInfo ci) {
		EnderDragon self = (EnderDragon) (Object) this;
		if (FinalRaidEnd.enabled() && dragonFight != null && !self.isRemoved() && self.level() instanceof ServerLevel level) {
			FinalRaidEnd.vanillaDragonFell(level, self, dragonFight);
			ci.cancel();
		}
	}
}
