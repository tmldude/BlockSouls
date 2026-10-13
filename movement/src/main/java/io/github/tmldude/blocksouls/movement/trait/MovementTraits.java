package io.github.tmldude.blocksouls.movement.trait;

import net.minecraft.world.entity.player.Player;

import io.github.tmldude.blocksouls.common.player.Capabilities;
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

	/** Air jumps left to the trait, and none at all without the {@code MULTI_JUMP} verb. */
	public static int airJumps(Player player) {
		return TraitMath.airJumps(PlayerTraits.level(player, PlayerTraits.DOUBLE_JUMP), config().traits, canMultiJump(player));
	}

	/** The player's max stamina ({@code STAMINA_MAX_PENALTY}). */
	public static double staminaMax(Player player) {
		return TraitMath.staminaMax(PlayerTraits.level(player, PlayerTraits.STAMINA_MAX_PENALTY), config().stamina, config().traits);
	}

	// The origin capability gate. Every verb above the universal Souls core (roll, hold-jump, stamina) checks
	// here; a player with no set attached has the v1 verbs, so v1 and classic runs never change.

	public static boolean canClimb(Player player) {
		return Capabilities.has(player, Capabilities.CLIMB);
	}

	public static boolean canSwing(Player player) {
		return Capabilities.has(player, Capabilities.SWING);
	}

	/** Triple-jump chains and air jumps. */
	public static boolean canMultiJump(Player player) {
		return Capabilities.has(player, Capabilities.MULTI_JUMP);
	}

	public static boolean canGlide(Player player) {
		return Capabilities.has(player, Capabilities.GLIDE);
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
