package io.github.tmldude.blocksouls.movement.climb;

import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;

import io.github.tmldude.blocksouls.common.player.PlayerStatus;
import io.github.tmldude.blocksouls.movement.trait.MovementTraits;

/** World queries for wall climbing, shared by the client (prediction) and server (stamina). */
public final class ClimbWorld {
	/** How far past the wall face the player has to fit to stand on top of it. */
	private static final double STAND_ON_TOP_REACH = 0.4;

	private ClimbWorld() {
	}

	/**
	 * Top Y of the wall the player is pushing into, or NaN if there's no solid block within
	 * {@code reach} in direction {x, z}. Only blocks level with the player's body count.
	 */
	public static double wallTop(Player player, double[] direction, double reach) {
		// Shave the top and bottom so the floor and ceiling never count as a wall.
		AABB probe = player.getBoundingBox().deflate(0, 0.05, 0).move(direction[0] * reach, 0, direction[1] * reach);
		double top = Double.NaN;
		for (VoxelShape shape : player.level().getBlockCollisions(player, probe)) {
			if (!shape.isEmpty()) {
				top = Double.isNaN(top) ? shape.max(Direction.Axis.Y) : Math.max(top, shape.max(Direction.Axis.Y));
			}
		}
		return top;
	}

	/** Whether the player would fit standing on top of a wall whose top is at {@code wallTop}. */
	public static boolean fitsOnTop(Player player, double[] direction, double wallTop) {
		AABB box = player.getBoundingBox();
		AABB standing = box.move(direction[0] * STAND_ON_TOP_REACH, wallTop - box.minY + 0.01, direction[1] * STAND_ON_TOP_REACH);
		return player.level().noCollision(player, standing);
	}

	/** Climbing needs the {@code CLIMB} verb (origins); the rest is the usual "not already moving some other way". */
	public static boolean isEligible(Player player) {
		return MovementTraits.canClimb(player)
				&& !player.getAbilities().flying
				&& !player.isFallFlying()
				&& !player.isInLiquid()
				&& !player.onClimbable()
				&& !player.isPassenger()
				&& !player.isSpectator()
				&& !PlayerStatus.isDowned(player);
	}
}
