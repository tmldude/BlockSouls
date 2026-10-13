package io.github.tmldude.blocksouls.movement.glide;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;

import io.github.tmldude.blocksouls.movement.MovementConfig;

/**
 * Where updrafts are, on either side (the client adds the lift, the server could verify it). The only source
 * today is the dusk thermal over open ground ({@link Glide#isThermal}); the Skyrift's permanent updrafts plug in
 * here when that dimension lands.
 */
public final class Updrafts {
	private Updrafts() {
	}

	/** Whether the player is inside an updraft right now. */
	public static boolean isInUpdraft(Player player, MovementConfig.Glide config) {
		Level level = player.level();
		BlockPos pos = player.blockPosition();
		int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING, pos.getX(), pos.getZ());
		return Glide.isThermal(level.getDefaultClockTime(), level.dimensionType().hasSkyLight(), level.canSeeSky(pos), pos.getY() - ground, config);
	}
}
