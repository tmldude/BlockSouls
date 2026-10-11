package io.github.tmldude.blocksouls.movement.trait;

import net.minecraft.world.entity.player.Player;

import io.github.tmldude.blocksouls.common.player.PlayerTraits;
import io.github.tmldude.blocksouls.movement.BlockSoulsMovement;
import io.github.tmldude.blocksouls.movement.MovementConfig;

/**
 * A player's trait-scaled movement numbers. Traits are synced to their owner, so this works on the server and for
 * the local player on the client.
 */
public final class MovementTraits {
	private MovementTraits() {
	}

	private static MovementConfig config() {
		return BlockSoulsMovement.CONFIG;
	}

	public static int airJumps(Player player) {
		return TraitMath.airJumps(PlayerTraits.level(player, PlayerTraits.DOUBLE_JUMP), config().traits);
	}

	public static int rollIframeEnd(Player player) {
		return TraitMath.rollIframeEnd(PlayerTraits.level(player, PlayerTraits.ROLL_IFRAMES), config().roll, config().traits);
	}

	public static double webRange(Player player) {
		return TraitMath.webRange(PlayerTraits.level(player, PlayerTraits.WEB_REACH), config().web, config().traits);
	}

	public static double climbSpeedMultiplier(Player player) {
		return TraitMath.climbSpeedMultiplier(PlayerTraits.level(player, PlayerTraits.CLIMB_SPEED), config().traits);
	}

	public static double staminaRegenMultiplier(Player player) {
		return TraitMath.staminaRegenMultiplier(PlayerTraits.level(player, PlayerTraits.STAMINA_REGEN), config().traits);
	}

	public static boolean canFly(Player player) {
		return PlayerTraits.has(player, PlayerTraits.FLIGHT);
	}
}
