package io.github.tmldude.blocksouls.movement.client.climb;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

import io.github.tmldude.blocksouls.movement.MoveMath;
import io.github.tmldude.blocksouls.movement.MovementConfig;
import io.github.tmldude.blocksouls.movement.climb.Climb;
import io.github.tmldude.blocksouls.movement.climb.ClimbWorld;
import io.github.tmldude.blocksouls.movement.client.web.WebHandler;
import io.github.tmldude.blocksouls.movement.stamina.StaminaAttachment;
import io.github.tmldude.blocksouls.movement.trait.MovementTraits;

/** Wall climbing for the local player: push into a wall while airborne to climb; sneak to let go. */
public final class ClimbHandler {
	/** Ticks after a pull-up before climbing resumes, so the boost isn't re-applied every tick. */
	private static final int LEDGE_COOLDOWN_TICKS = 8;

	private static MovementConfig.Climb config;
	private static int ledgeCooldown;

	private ClimbHandler() {
	}

	public static void register(MovementConfig.Climb climbConfig) {
		config = climbConfig;
		ClientTickEvents.END_CLIENT_TICK.register(ClimbHandler::tick);
	}

	private static void tick(Minecraft client) {
		LocalPlayer player = client.player;
		if (player == null || WebHandler.isSwinging()) {
			return;
		}
		if (ledgeCooldown > 0) {
			ledgeCooldown--;
			return;
		}

		Vec2 input = player.input.getMoveVector();
		double[] direction = MoveMath.inputDirection(input.x, input.y, player.getYRot());
		if (direction == null) {
			return;
		}

		double wallTop = ClimbWorld.wallTop(player, direction, config.wallReach);
		if (!Climb.isClimbing(player.onGround(), !Double.isNaN(wallTop), player.isShiftKeyDown(), ClimbWorld.isEligible(player),
				StaminaAttachment.get(player).value(), config)) {
			return;
		}

		Vec3 velocity = player.getDeltaMovement();
		if (Climb.isLedge(wallTop, player.getY(), ClimbWorld.fitsOnTop(player, direction, wallTop), config)) {
			player.setDeltaMovement(direction[0] * config.ledgeBoostForward, Math.max(velocity.y, config.ledgeBoostUp),
					direction[1] * config.ledgeBoostForward);
			ledgeCooldown = LEDGE_COOLDOWN_TICKS;
		} else {
			player.setDeltaMovement(velocity.x, Climb.climbVelocity(velocity.y, config, MovementTraits.climbSpeedMultiplier(player)), velocity.z);
		}
	}
}
