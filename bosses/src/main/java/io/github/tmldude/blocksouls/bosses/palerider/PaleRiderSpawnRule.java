package io.github.tmldude.blocksouls.bosses.palerider;

import java.util.Optional;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.levelgen.Heightmap;

import io.github.tmldude.blocksouls.bosses.api.BossEntity;
import io.github.tmldude.blocksouls.bosses.field.FieldBossRule;
import io.github.tmldude.blocksouls.bosses.field.FieldBossSettings;

/** Roams open grassland at night: a spot 40–60 blocks from a player in plains, meadow or savanna, on dry ground. */
final class PaleRiderSpawnRule implements FieldBossRule {
	private static final int ATTEMPTS = 10;

	@SuppressWarnings("unchecked")
	private static final ResourceKey<Biome>[] BIOMES = new ResourceKey[] {Biomes.PLAINS, Biomes.SUNFLOWER_PLAINS, Biomes.MEADOW, Biomes.SAVANNA,
			Biomes.SAVANNA_PLATEAU, Biomes.WINDSWEPT_SAVANNA};

	@Override
	public Schedule schedule() {
		return Schedule.DAILY;
	}

	@Override
	public FieldBossSettings settings() {
		return PaleRider.config().field;
	}

	@Override
	public BossEntity create(ServerLevel level) {
		return PaleRiders.TYPE.create(level, EntitySpawnReason.EVENT);
	}

	@Override
	public Optional<Spot> find(ServerLevel level, ServerPlayer player, RandomSource random) {
		if (level.dimension() != Level.OVERWORLD || !level.isDarkOutside()) {
			return Optional.empty();
		}
		PaleRiderConfig c = PaleRider.config();
		for (int i = 0; i < ATTEMPTS; i++) {
			double angle = random.nextDouble() * Math.PI * 2;
			double distance = c.spawnMinDistance + random.nextDouble() * Math.max(0, c.spawnMaxDistance - c.spawnMinDistance);
			int x = (int) Math.floor(player.getX() + Math.cos(angle) * distance);
			int z = (int) Math.floor(player.getZ() + Math.sin(angle) * distance);
			if (!level.hasChunkAt(new BlockPos(x, level.getMinY(), z))) {
				continue;
			}
			BlockPos pos = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
			if (grassland(level.getBiome(pos)) && standable(level, pos)) {
				// Faces the player it came for.
				float yaw = (float) (Math.toDegrees(Math.atan2(player.getZ() - z, player.getX() - x)) - 90);
				return Optional.of(new Spot(level, pos, yaw));
			}
		}
		return Optional.empty();
	}

	static boolean grassland(Holder<Biome> biome) {
		for (ResourceKey<Biome> key : BIOMES) {
			if (biome.is(key)) {
				return true;
			}
		}
		return false;
	}

	private static boolean standable(ServerLevel level, BlockPos pos) {
		BlockPos floor = pos.below();
		if (!level.getBlockState(floor).isFaceSturdy(level, floor, Direction.UP) || !level.getFluidState(floor).isEmpty()) {
			return false;
		}
		for (int dy = 0; dy < 4; dy++) {
			BlockPos at = pos.above(dy);
			if (!level.getBlockState(at).getCollisionShape(level, at).isEmpty() || !level.getFluidState(at).isEmpty()) {
				return false;
			}
		}
		return true;
	}
}
