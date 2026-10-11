package io.github.tmldude.blocksouls.bosses.stormplate;

import java.util.Optional;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;

import io.github.tmldude.blocksouls.bosses.api.BossEntity;
import io.github.tmldude.blocksouls.bosses.field.FieldBossRule;
import io.github.tmldude.blocksouls.bosses.field.FieldBossSettings;

/**
 * Rolled once per in-game day. On a hit, looks for flat, open, grassy ground in the overworld 36-60 blocks from a
 * player (the ruin's whole footprint within a few blocks of level, sky above it), and the knight appears there with
 * its ruin, facing the player.
 */
final class StormplateSpawnRule implements FieldBossRule {
	private static final int TRIES = 10;

	@Override
	public Schedule schedule() {
		return Schedule.DAILY;
	}

	@Override
	public FieldBossSettings settings() {
		return StormplateKnight.config().field;
	}

	@Override
	public BossEntity create(ServerLevel level) {
		return StormplateKnights.TYPE.create(level, EntitySpawnReason.EVENT);
	}

	@Override
	public Optional<Spot> find(ServerLevel level, ServerPlayer player, RandomSource random) {
		if (level.dimension() != Level.OVERWORLD) {
			return Optional.empty();
		}
		StormplateConfig c = StormplateKnight.config();
		for (int i = 0; i < TRIES; i++) {
			double a = random.nextDouble() * Math.PI * 2;
			double r = c.spawnMinDistance + random.nextDouble() * Math.max(0, c.spawnMaxDistance - c.spawnMinDistance);
			int x = (int) Math.floor(player.getX() + Math.cos(a) * r);
			int z = (int) Math.floor(player.getZ() + Math.sin(a) * r);
			BlockPos spot = flatOpenGround(level, x, z, c.ruinRadius, c.spawnMaxSlope);
			if (spot != null) {
				float yaw = (float) Math.toDegrees(Math.atan2(player.getZ() - z, player.getX() - x)) - 90;
				return Optional.of(new Spot(level, spot, yaw));
			}
		}
		return Optional.empty();
	}

	/** The standing spot at (x, z) if the ground there is grass/dirt under open sky and the ruin's footprint is level enough. */
	static @Nullable BlockPos flatOpenGround(ServerLevel level, int x, int z, int radius, int maxSlope) {
		if (!level.hasChunk(x >> 4, z >> 4)) {
			return null;
		}
		int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
		BlockPos feet = new BlockPos(x, y, z);
		if (!StormplateRuin.isSoil(level.getBlockState(feet.below())) || !level.canSeeSky(feet) || !level.getFluidState(feet).isEmpty()) {
			return null;
		}
		// Sample two rings: the pillars' circle and halfway in.
		for (double ring : new double[] {radius * 0.5, radius}) {
			for (int k = 0; k < 12; k++) {
				double a = k * Math.PI * 2 / 12;
				int sx = x + (int) Math.round(Math.cos(a) * ring);
				int sz = z + (int) Math.round(Math.sin(a) * ring);
				if (!level.hasChunk(sx >> 4, sz >> 4)) {
					return null;
				}
				int sy = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, sx, sz);
				if (Math.abs(sy - y) > maxSlope || !level.getFluidState(new BlockPos(sx, sy - 1, sz)).isEmpty()) {
					return null;
				}
			}
		}
		return feet;
	}
}
