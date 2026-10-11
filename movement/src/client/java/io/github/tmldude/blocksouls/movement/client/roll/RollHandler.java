package io.github.tmldude.blocksouls.movement.client.roll;

import org.lwjgl.sdl.SDLScancode;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

import io.github.tmldude.blocksouls.common.client.ClientNetworking;
import io.github.tmldude.blocksouls.common.client.Keybinds;
import io.github.tmldude.blocksouls.movement.MovementConfig;
import io.github.tmldude.blocksouls.movement.roll.Roll;
import io.github.tmldude.blocksouls.movement.roll.RollPayload;
import io.github.tmldude.blocksouls.movement.stamina.StaminaAttachment;

/**
 * Client side of the dodge roll: starts it on key press and drives the movement. The server decides
 * stamina and i-frames; the client checks the same conditions first so it rarely rolls without them.
 */
public final class RollHandler {
	private static MovementConfig config;
	private static KeyMapping key;
	/** Ticks into the current roll, or -1 when not rolling. */
	private static int rollTick = -1;
	private static double dirX;
	private static double dirZ;

	private RollHandler() {
	}

	public static void register(MovementConfig movementConfig) {
		config = movementConfig;
		key = Keybinds.keyboard("dodge_roll", SDLScancode.SDL_SCANCODE_LALT);
		ClientTickEvents.END_CLIENT_TICK.register(RollHandler::tick);
	}

	/** Ticks into the local player's current roll, or -1 when not rolling. */
	public static int rollTick() {
		return rollTick;
	}

	private static void tick(Minecraft client) {
		LocalPlayer player = client.player;
		if (player == null) {
			rollTick = -1;
			return;
		}

		while (key.consumeClick()) {
			tryStart(player);
		}

		if (rollTick >= 0) {
			double speed = Roll.speedAt(rollTick, config.roll);
			Vec3 velocity = player.getDeltaMovement();
			player.setDeltaMovement(dirX * speed, velocity.y, dirZ * speed);
			rollTick = Roll.isActive(rollTick + 1, config.roll) ? rollTick + 1 : -1;
		}
	}

	private static void tryStart(LocalPlayer player) {
		if (!config.roll.enabled || rollTick >= 0 || !player.onGround() || player.getAbilities().flying
				|| player.isPassenger() || player.isSpectator() || player.isInLiquid()
				|| !StaminaAttachment.get(player).canAfford(config.roll.staminaCost)) {
			return;
		}
		// No roll at all on servers without the mod: no i-frames or stamina would back it.
		if (!ClientNetworking.sendToServer(RollPayload.INSTANCE)) {
			return;
		}

		Vec2 input = player.input.getMoveVector();
		double[] direction = Roll.direction(input.x, input.y, player.getYRot());
		dirX = direction[0];
		dirZ = direction[1];
		rollTick = 0;
	}
}
