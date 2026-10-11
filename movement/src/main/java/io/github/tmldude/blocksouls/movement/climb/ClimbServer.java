package io.github.tmldude.blocksouls.movement.climb;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Input;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import io.github.tmldude.blocksouls.movement.MoveMath;
import io.github.tmldude.blocksouls.movement.MovementConfig;
import io.github.tmldude.blocksouls.movement.stamina.StaminaAttachment;
import io.github.tmldude.blocksouls.movement.stamina.StaminaState;
import io.github.tmldude.blocksouls.movement.web.WebServer;

/**
 * Mirrors the client's climb check from the player's last sent input and drains stamina while
 * climbing. The climbing movement itself is client-side, like all vanilla player movement.
 */
public final class ClimbServer {
	private static MovementConfig config;

	private ClimbServer() {
	}

	public static void register(MovementConfig movementConfig) {
		config = movementConfig;
		ServerTickEvents.END_SERVER_TICK.register(ClimbServer::tick);
	}

	private static void tick(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			Input input = player.getLastClientInput();
			double[] direction = MoveMath.inputDirection(input.forward(), input.backward(), input.left(), input.right(), player.getYRot());
			if (direction == null || WebServer.isSwinging(player)) {
				continue;
			}

			StaminaState stamina = StaminaAttachment.get(player);
			boolean touchingWall = !Double.isNaN(ClimbWorld.wallTop(player, direction, config.climb.wallReach));
			if (Climb.isClimbing(player.onGround(), touchingWall, input.shift(), ClimbWorld.isEligible(player), stamina.value(), config.climb)) {
				StaminaAttachment.set(player, stamina.spend(Climb.staminaPerTick(config.climb), config.stamina));
			}
		}
	}
}
