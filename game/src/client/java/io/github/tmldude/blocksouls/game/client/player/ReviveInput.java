package io.github.tmldude.blocksouls.game.client.player;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.EntityHitResult;

import io.github.tmldude.blocksouls.common.client.ClientNetworking;
import io.github.tmldude.blocksouls.common.player.PlayerStatus;
import io.github.tmldude.blocksouls.game.player.PlayerSystem;
import io.github.tmldude.blocksouls.game.player.downed.RevivePayloads;

/**
 * Holding use with the crosshair on a downed player revives them: tells the server when it starts and stops.
 * The start is re-sent every half second so an attempt the server cancelled (out of range) resumes on its own.
 */
final class ReviveInput {
	private static final int RESEND_TICKS = 10;

	private static int target = -1;
	private static int resendTimer;

	private ReviveInput() {
	}

	static void tick(Minecraft minecraft) {
		int wanted = wantedTarget(minecraft);
		if (wanted != target) {
			if (target != -1 && wanted == -1) {
				ClientNetworking.sendToServer(RevivePayloads.Stop.INSTANCE);
			}
			target = wanted;
			resendTimer = 0;
		}

		if (target != -1 && --resendTimer <= 0) {
			ClientNetworking.sendToServer(new RevivePayloads.Start(target));
			resendTimer = RESEND_TICKS;
		}
	}

	static void reset() {
		target = -1;
	}

	private static int wantedTarget(Minecraft minecraft) {
		LocalPlayer self = minecraft.player;
		if (self == null || minecraft.gui.screen() != null || !minecraft.options.keyUse.isDown()
				|| PlayerStatus.isDowned(self) || self.isSpectator()) {
			return -1;
		}
		if (minecraft.hitResult instanceof EntityHitResult hit && hit.getEntity() instanceof Player other
				&& PlayerStatus.isDowned(other)
				&& self.distanceTo(other) <= PlayerSystem.CONFIG.downed.reviveRange + 0.5) {
			return other.getId();
		}
		return -1;
	}
}
