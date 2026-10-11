package io.github.tmldude.blocksouls.bosses.raid;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.dimension.end.EnderDragonFight;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.EndSpikeFeature;
import net.minecraft.world.phys.Vec3;

/**
 * Where the final raid happens: the island center and surface, the crystal spots (pillar tops), the obelisk that
 * shows the Crystal Order (the exit portal's bedrock column), the perch and the plate spots. Built from the End's
 * generated pillars ({@link #fromEnd}) or from scripted positions in tests ({@link #of}).
 *
 * @param center        island center at surface level (horizontal center of the fight)
 * @param floorY        y of the first air block above the island surface
 * @param crystals      crystal spots (where the crystal entity stands)
 * @param caged         per crystal spot: whether its pillar is caged (used only when the open ones run out)
 * @param obelisk       top of the obelisk: where crystals beam during the show, and where progress rings light up
 * @param perch         where the dragon lands for its DPS window (its feet)
 * @param plates        candidate plate centers (block positions of the plate surface), best spread first
 * @param scale         flight distances (orbits, approach paths, lanes) are multiplied by this: 1 on the End island,
 *                      smaller for cramped arenas (game tests, a dragon summoned outside the End)
 */
public record RaidArena(BlockPos center, int floorY, List<Vec3> crystals, List<Boolean> caged, Vec3 obelisk, Vec3 perch, List<BlockPos> plates,
		double scale) {
	public RaidArena {
		crystals = List.copyOf(crystals);
		caged = List.copyOf(caged);
		plates = List.copyOf(plates);
	}

	public static RaidArena of(BlockPos center, List<Vec3> crystals, Vec3 obelisk, Vec3 perch, List<BlockPos> plates, double scale) {
		List<Boolean> caged = new ArrayList<>();
		for (int i = 0; i < crystals.size(); i++) {
			caged.add(false);
		}
		return new RaidArena(center, center.getY(), crystals, caged, obelisk, perch, plates, scale);
	}

	/** The vanilla main island around {@code origin} (the fight origin, normally 0,0). */
	public static RaidArena fromEnd(ServerLevel level, BlockPos origin) {
		FinalRaidConfig config = FinalRaids.CONFIG;
		BlockPos podium = EnderDragonFight.getPodiumLocation(origin);
		// The surface just beside the podium's bowl.
		int floorY = surface(level, podium.offset(7, 0, 0), 64);
		BlockPos center = new BlockPos(podium.getX(), floorY, podium.getZ());

		List<Vec3> crystals = new ArrayList<>();
		List<Boolean> caged = new ArrayList<>();
		for (EndSpikeFeature.EndSpike spike : EndSpikeFeature.getSpikesForLevel(level)) {
			crystals.add(new Vec3(spike.getCenterX() + 0.5, spike.getHeight() + 1, spike.getCenterZ() + 0.5));
			caged.add(spike.isGuarded());
		}

		// The podium's center column (inactive portal: 4 bedrock above the bowl).
		int columnTop = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, podium.getX(), podium.getZ());
		Vec3 obelisk = new Vec3(podium.getX() + 0.5, Math.max(columnTop, floorY + 3), podium.getZ() + 0.5);
		Vec3 perch = new Vec3(podium.getX() + 0.5, floorY, podium.getZ() + 0.5);

		// Four plate spots on the island between the podium and the pillars, offset from the pillar ring's angles.
		List<BlockPos> plates = new ArrayList<>();
		for (int i = 0; i < 4; i++) {
			// 1st and 2nd opposite each other, then the other two (so two plates are always far apart).
			double angle = Math.PI / 4 + (i % 2) * Math.PI + (i / 2) * Math.PI / 2;
			double r = config.plateDistance;
			int x = podium.getX() + (int) Math.round(Math.cos(angle) * r);
			int z = podium.getZ() + (int) Math.round(Math.sin(angle) * r);
			int y = surface(level, new BlockPos(x, 0, z), floorY);
			plates.add(new BlockPos(x, y - 1, z));
		}
		return new RaidArena(center, floorY, crystals, caged, obelisk, perch, plates, 1.0);
	}

	/** First air y above the surface at {@code pos}'s column, or {@code fallback} over the void. */
	private static int surface(ServerLevel level, BlockPos pos, int fallback) {
		level.getChunkAt(pos);
		int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos.getX(), pos.getZ());
		return y <= level.getMinY() + 1 ? fallback : y;
	}

	/** Island center at surface level, as a position. */
	public Vec3 centerVec() {
		return new Vec3(center.getX() + 0.5, floorY, center.getZ() + 0.5);
	}

	/** {@code distance} at this arena's {@link #scale}. */
	public double scaled(double distance) {
		return distance * scale;
	}

	/** A point on the circle of {@code radius} around the center at {@code height} above the surface (both scaled). */
	public Vec3 orbit(double angle, double radius, double height) {
		double r = scaled(radius);
		return new Vec3(center.getX() + 0.5 + Math.cos(angle) * r, floorY + scaled(height), center.getZ() + 0.5 + Math.sin(angle) * r);
	}
}
