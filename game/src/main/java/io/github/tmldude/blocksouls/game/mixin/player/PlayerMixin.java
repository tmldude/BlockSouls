package io.github.tmldude.blocksouls.game.mixin.player;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;

import io.github.tmldude.blocksouls.common.player.PlayerStatus;

/** Downed players crawl, and mobs neither target nor keep targeting them. Runs on both sides. */
@Mixin(Player.class)
abstract class PlayerMixin {
	@Inject(method = "getDesiredPose", at = @At("HEAD"), cancellable = true)
	private void blocksouls$crawlWhileDowned(CallbackInfoReturnable<Pose> cir) {
		Player self = (Player) (Object) this;
		if (PlayerStatus.isDowned(self) && !self.isSleeping()) {
			cir.setReturnValue(Pose.SWIMMING);
		}
	}

	@Inject(method = "canBeSeenAsEnemy", at = @At("HEAD"), cancellable = true)
	private void blocksouls$notAnEnemyWhileDowned(CallbackInfoReturnable<Boolean> cir) {
		if (PlayerStatus.isDowned((Player) (Object) this)) {
			cir.setReturnValue(false);
		}
	}
}
