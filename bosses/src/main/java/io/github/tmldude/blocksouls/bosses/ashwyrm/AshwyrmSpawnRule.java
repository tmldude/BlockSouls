package io.github.tmldude.blocksouls.bosses.ashwyrm;

import java.util.Optional;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;

import io.github.tmldude.blocksouls.bosses.api.BossEntity;
import io.github.tmldude.blocksouls.bosses.field.FieldBossRule;
import io.github.tmldude.blocksouls.bosses.field.FieldBossSettings;

/**
 * Once per in-game day (on the daily roll): samples loaded columns 48–128 blocks around a player and lands on the
 * highest open ground among them that is a mountain/hill biome or simply high ({@code highGroundY}).
 */
final class AshwyrmSpawnRule implements FieldBossRule {
	@Override
	public Schedule schedule() {
		return Schedule.DAILY;
	}

	@Override
	public FieldBossSettings settings() {
		return Ashwyrm.config().field;
	}

	@Override
	public BossEntity create(ServerLevel level) {
		return Ashwyrms.TYPE.create(level, EntitySpawnReason.EVENT);
	}

	@Override
	public Optional<Spot> find(ServerLevel level, ServerPlayer player, RandomSource random) {
		if (level.dimension() != Level.OVERWORLD) {
			return Optional.empty();
		}
		AshwyrmConfig c = Ashwyrm.config();
		BlockPos best = null;
		for (int i = 0; i < c.spawnSamples; i++) {
			double a = random.nextDouble() * Math.PI * 2;
			double d = c.spawnMinDistance + random.nextDouble() * Math.max(0, c.spawnMaxDistance - c.spawnMinDistance);
			int x = (int) Math.floor(player.getX() + Math.cos(a) * d);
			int z = (int) Math.floor(player.getZ() + Math.sin(a) * d);
			if (!level.hasChunk(x >> 4, z >> 4)) {
				continue;
			}
			int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
			if (best != null && y <= best.getY()) {
				continue;
			}
			BlockPos pos = new BlockPos(x, y, z);
			if (level.isInWorldBounds(pos) && high(level, pos, c) && open(level, pos)) {
				best = pos;
			}
		}
		if (best == null) {
			return Optional.empty();
		}
		// Face the player it came for.
		float yaw = (float) (Math.toDegrees(Math.atan2(player.getZ() - best.getZ(), player.getX() - best.getX())) - 90);
		return Optional.of(new Spot(level, best, yaw));
	}

	private static boolean high(ServerLevel level, BlockPos pos, AshwyrmConfig c) {
		if (pos.getY() >= c.highGroundY) {
			return true;
		}
		Holder<Biome> biome = level.getBiome(pos);
		return biome.is(BiomeTags.IS_MOUNTAIN) || biome.is(BiomeTags.IS_HILL);
	}

	/** A sturdy dry floor, room for the body (3×3, 5 high) and open sky. */
	private static boolean open(ServerLevel level, BlockPos pos) {
		BlockPos floor = pos.below();
		if (!level.getBlockState(floor).isFaceSturdy(level, floor, Direction.UP) || !level.getFluidState(floor).isEmpty()) {
			return false;
		}
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				for (int dy = dx == 0 && dz == 0 ? 0 : 1; dy < 5; dy++) {
					BlockPos at = pos.offset(dx, dy, dz);
					if (!level.getBlockState(at).getCollisionShape(level, at).isEmpty() || !level.getFluidState(at).isEmpty()) {
						return false;
					}
				}
			}
		}
		return level.canSeeSky(pos.above(5));
	}
}
