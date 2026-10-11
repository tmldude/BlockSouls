package io.github.tmldude.blocksouls.bosses.furnace;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.BuiltinStructures;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.phys.Vec3;

import io.github.tmldude.blocksouls.bosses.api.BossEntity;
import io.github.tmldude.blocksouls.bosses.field.FieldBossRule;
import io.github.tmldude.blocksouls.bosses.field.FieldBossSettings;

/**
 * Wakes when a player in the Nether comes within {@code bastionTriggerRange} of a bastion's center, and stands at the
 * side of the bastion facing that player (its "entrance" from where they approach).
 */
final class FurnaceKnightSpawnRule implements FieldBossRule {
	@Override
	public Schedule schedule() {
		return Schedule.WATCH;
	}

	@Override
	public FieldBossSettings settings() {
		return FurnaceKnight.config().field;
	}

	@Override
	public boolean roaming() {
		return false;
	}

	@Override
	public BossEntity create(ServerLevel level) {
		return FurnaceKnights.TYPE.create(level, EntitySpawnReason.EVENT);
	}

	@Override
	public Optional<Spot> find(ServerLevel level, ServerPlayer player, RandomSource random) {
		if (level.dimension() != Level.NETHER) {
			return Optional.empty();
		}
		StructureStart bastion = nearestBastion(level, player.blockPosition(), FurnaceKnight.config().bastionTriggerRange);
		if (bastion == null) {
			return Optional.empty();
		}
		BoundingBox box = bastion.getBoundingBox();
		BlockPos center = box.getCenter();
		Vec3 toPlayer = new Vec3(player.getX() - center.getX(), 0, player.getZ() - center.getZ());
		if (toPlayer.lengthSqr() < 1) {
			toPlayer = new Vec3(1, 0, 0);
		}
		Vec3 dir = toPlayer.normalize();
		// Walk from the center toward the player until we leave the bastion's box: that edge is the "entrance".
		double edge = 0;
		while (edge < 96 && box.isInside(BlockPos.containing(center.getX() + dir.x * edge, center.getY(), center.getZ() + dir.z * edge))) {
			edge += 1;
		}
		BlockPos spot = null;
		for (double d = edge - 2; d <= edge + 10 && spot == null; d += 2) {
			int x = (int) Math.floor(center.getX() + dir.x * d);
			int z = (int) Math.floor(center.getZ() + dir.z * d);
			spot = standable(level, x, z, (int) player.getY());
		}
		for (double d = edge - 4; d >= edge / 2 && spot == null; d -= 2) {
			int x = (int) Math.floor(center.getX() + dir.x * d);
			int z = (int) Math.floor(center.getZ() + dir.z * d);
			spot = standable(level, x, z, (int) player.getY());
		}
		if (spot == null) {
			return Optional.empty();
		}
		// Face outward, toward the approaching player.
		float yaw = (float) (Math.toDegrees(Math.atan2(dir.z, dir.x)) - 90);
		return Optional.of(new Spot(level, spot, yaw));
	}

	/** The nearest bastion whose center is within {@code range} blocks (only searches loaded chunks). */
	static @Nullable StructureStart nearestBastion(ServerLevel level, BlockPos pos, double range) {
		Structure bastion = level.registryAccess().lookupOrThrow(Registries.STRUCTURE).getValue(BuiltinStructures.BASTION_REMNANT);
		if (bastion == null) {
			return null;
		}
		int chunks = (int) Math.ceil(range / 16) + 1;
		ChunkPos origin = ChunkPos.containing(pos);
		Set<StructureStart> seen = new HashSet<>();
		StructureStart best = null;
		double bestDist = range * range;
		for (int cx = -chunks; cx <= chunks; cx++) {
			for (int cz = -chunks; cz <= chunks; cz++) {
				int x = origin.x() + cx;
				int z = origin.z() + cz;
				if (!level.hasChunk(x, z)) {
					continue;
				}
				for (StructureStart start : level.structureManager().startsForStructure(x, z, bastion)) {
					if (!start.isValid() || !seen.add(start)) {
						continue;
					}
					BlockPos center = start.getBoundingBox().getCenter();
					double dx = center.getX() - pos.getX();
					double dz = center.getZ() - pos.getZ();
					double dist = dx * dx + dz * dz;
					if (dist <= bestDist) {
						bestDist = dist;
						best = start;
					}
				}
			}
		}
		return best;
	}

	/** Solid floor with a 2×2, 4-high gap above it, searched from {@code nearY + 10} down to {@code nearY - 16}. */
	private static @Nullable BlockPos standable(ServerLevel level, int x, int z, int nearY) {
		for (int y = nearY + 10; y >= nearY - 16; y--) {
			BlockPos pos = new BlockPos(x, y, z);
			if (!level.hasChunkAt(pos) || !level.isInWorldBounds(pos)) {
				continue;
			}
			if (fits(level, pos)) {
				return pos;
			}
		}
		return null;
	}

	private static boolean fits(ServerLevel level, BlockPos pos) {
		for (int dx = 0; dx <= 1; dx++) {
			for (int dz = 0; dz <= 1; dz++) {
				BlockPos floor = pos.offset(dx, -1, dz);
				if (!level.getBlockState(floor).isFaceSturdy(level, floor, Direction.UP)) {
					return false;
				}
				for (int dy = 0; dy < 4; dy++) {
					BlockPos at = pos.offset(dx, dy, dz);
					if (!level.getBlockState(at).getCollisionShape(level, at).isEmpty() || !level.getFluidState(at).isEmpty()) {
						return false;
					}
				}
			}
		}
		return true;
	}
}
