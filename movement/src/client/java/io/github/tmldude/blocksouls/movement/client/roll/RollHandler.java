package io.github.tmldude.blocksouls.movement.client.roll;

import org.lwjgl.sdl.SDLScancode;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

import io.github.tmldude.blocksouls.common.client.ClientNetworking;
import io.github.tmldude.blocksouls.common.client.Keybinds;
import io.github.tmldude.blocksouls.common.player.PlayerStatus;
import io.github.tmldude.blocksouls.movement.MoveMath;
import io.github.tmldude.blocksouls.movement.MovementConfig;
import io.github.tmldude.blocksouls.movement.roll.FreeRoll;
import io.github.tmldude.blocksouls.movement.roll.Roll;
import io.github.tmldude.blocksouls.movement.roll.RollPayload;
import io.github.tmldude.blocksouls.movement.stamina.StaminaAttachment;

/**
 * Client side of the dodge roll: starts it on key press and drives the movement and the camera. The server decides
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
				|| player.isPassenger() || player.isSpectator() || player.isInLiquid() || PlayerStatus.isDowned(player)
				|| !(FreeRoll.isActive(player) || StaminaAttachment.get(player).canAfford(config.roll.staminaCost))) {
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

	/**
	 * Camera roll in degrees for this frame: leans toward a sideways dodge, easing in and out over the roll.
	 * Positive rotates the view counter-clockwise (the head tilts right).
	 */
	public static float cameraTilt(LocalPlayer player, float partialTicks) {
		if (rollTick < 0 || config.roll.cameraTiltDegrees == 0) {
			return 0;
		}
		// How much of the roll goes to the player's left right now (+1 = straight left).
		double[] left = MoveMath.inputDirection(1, 0, player.getYRot());
		double side = dirX * left[0] + dirZ * left[1];
		return (float) (-side * config.roll.cameraTiltDegrees * Roll.ease(rollTick + partialTicks, config.roll));
	}

	/** Eye height the camera eases toward: crouch height while rolling. */
	public static float eyeHeight(LocalPlayer player, float standing) {
		return rollTick >= 0 && config.roll.lowerEyeHeight ? Math.min(standing, player.getEyeHeight(Pose.CROUCHING)) : standing;
	}
}
