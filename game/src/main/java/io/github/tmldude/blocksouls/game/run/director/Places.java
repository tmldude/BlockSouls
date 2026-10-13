package io.github.tmldude.blocksouls.game.run.director;

import java.util.List;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.Heightmap;

import io.github.tmldude.blocksouls.common.act.Act;
import io.github.tmldude.blocksouls.game.run.DirectorHooks;

/** Where the Director puts things: surface spots near a point or on a ring around it. */
final class Places {
	private static final int ATTEMPTS = 12;

	private Places() {
	}

	/** The surface (standing spot) at {@code x, z}, or null over the void or water. */
	static @Nullable BlockPos surface(ServerLevel level, int x, int z) {
		BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, 0, z));
		if (top.getY() <= level.getMinY() + 1) {
			return null;
		}
		if (!level.getFluidState(top.below()).isEmpty() || !level.getBlockState(top.below()).isSolid()) {
			return null;
		}
		return top;
	}

	/** A surface spot within {@code radius} of {@code centre} (the centre's column first), or null. */
	static @Nullable BlockPos surfaceNear(ServerLevel level, BlockPos centre, int radius, RandomSource random) {
		BlockPos pos = surface(level, centre.getX(), centre.getZ());
		for (int i = 0; pos == null && i < ATTEMPTS && radius > 0; i++) {
			pos = surface(level, centre.getX() + random.nextInt(2 * radius + 1) - radius, centre.getZ() + random.nextInt(2 * radius + 1) - radius);
		}
		return pos;
	}

	/** A surface spot about {@code distance} blocks from {@code centre} in a random direction, or null. */
	static @Nullable BlockPos ring(ServerLevel level, BlockPos centre, int distance, RandomSource random) {
		for (int i = 0; i < ATTEMPTS; i++) {
			double angle = random.nextDouble() * Math.PI * 2;
			double d = distance * (1 - 0.25 * i / ATTEMPTS);
			BlockPos pos = surface(level, centre.getX() + (int) Math.round(Math.cos(angle) * d), centre.getZ() + (int) Math.round(Math.sin(angle) * d));
			if (pos != null) {
				return pos;
			}
		}
		return null;
	}

	/** The level an act is in, or the party's level when its dimension isn't loaded. */
	static ServerLevel levelOf(MinecraftServer server, Act act) {
		ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, act.dimension()));
		return level != null ? level : DirectorHooks.level(server);
	}

	/** The camp, the first player, or the level's spawn: where the Director anchors the day. */
	static BlockPos anchor(MinecraftServer server, ServerLevel level) {
		DirectorData data = DirectorData.get(server);
		BlockPos camp = data.camp();
		if (camp != null && level.dimension().identifier().toString().equals(data.campDimension())) {
			return camp;
		}
		List<ServerPlayer> players = DirectorHooks.activePlayers(server);
		return players.isEmpty() ? level.getRespawnData().pos() : players.get(0).blockPosition();
	}

	static double distanceSq(BlockPos a, double x, double z) {
		double dx = a.getX() + 0.5 - x;
		double dz = a.getZ() + 0.5 - z;
		return dx * dx + dz * dz;
	}
}
