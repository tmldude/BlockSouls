package io.github.tmldude.blocksouls.bosses.stormplate;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * The ruined arena the Stormplate Knight brings with it: a disc of cracked stone-brick floor with a ring of broken
 * pillars and scattered rubble. Placed by code when the knight appears and left standing after the fight.
 *
 * <p>It only ever touches natural surface: air and loose plants become pillars and rubble, grass/dirt becomes floor.
 * Anything else (stone, logs, water, and every block a player placed that isn't dirt) is left exactly as it was.
 */
public final class StormplateRuin {
	private StormplateRuin() {
	}

	/** Air or loose plant growth (grass, ferns, flowers, snow layers): free space a pillar or rubble may fill. */
	public static boolean isOpen(BlockState state) {
		if (state.isAir()) {
			return true;
		}
		if (!state.getFluidState().isEmpty() || state.is(BlockTags.FIRE)) {
			return false;
		}
		return state.is(BlockTags.REPLACEABLE) || state.is(BlockTags.FLOWERS) || state.is(BlockTags.SMALL_FLOWERS) || state.is(Blocks.SNOW);
	}

	/** Grass and dirt surface: may become ruin floor. */
	public static boolean isSoil(BlockState state) {
		return state.is(BlockTags.DIRT) || state.is(Blocks.GRASS_BLOCK);
	}

	/** Everything the ruin may replace. */
	public static boolean isNatural(BlockState state) {
		return isOpen(state) || isSoil(state);
	}

	/**
	 * Builds the ruin around {@code center} (the knight's feet).
	 *
	 * @param radius  outer radius of the floor; the pillars stand just inside it
	 * @param maxStep columns whose ground is further than this above/below the center's are left alone
	 * @return every position changed
	 */
	public static List<BlockPos> build(ServerLevel level, BlockPos center, int radius, int pillars, double floorShare, int maxStep,
			RandomSource random) {
		List<BlockPos> changed = new ArrayList<>();
		int floorY = center.getY() - 1;

		// Floor: cracked stone bricks over the grass, denser toward the middle, a solid dais under the knight.
		for (int dx = -radius; dx <= radius; dx++) {
			for (int dz = -radius; dz <= radius; dz++) {
				double dist = Math.sqrt(dx * dx + dz * dz);
				if (dist > radius + 0.5) {
					continue;
				}
				BlockPos ground = ground(level, center.getX() + dx, center.getZ() + dz, floorY, maxStep);
				if (ground == null) {
					continue;
				}
				double share = dist <= 3 ? 1 : dist >= radius - 1 ? floorShare * 0.5 : floorShare + (1 - floorShare) * (1 - dist / radius) * 0.5;
				boolean ring = Math.abs(dist - (radius - 4)) < 0.6;
				if (!ring && random.nextDouble() >= share) {
					continue;
				}
				BlockState floor;
				if (dx == 0 && dz == 0) {
					floor = Blocks.CHISELED_STONE_BRICKS.defaultBlockState();
				} else if (dist <= 2.5 || ring) {
					floor = random.nextFloat() < 0.75 ? Blocks.STONE_BRICKS.defaultBlockState() : Blocks.CRACKED_STONE_BRICKS.defaultBlockState();
				} else {
					floor = floorBlock(random);
				}
				clearPlants(level, ground.above(), changed);
				replace(level, ground, floor, changed, false);
			}
		}

		// A ring of broken 2x2 pillars, each column snapped off at its own height.
		for (int i = 0; i < pillars; i++) {
			double angle = (i + random.nextDouble() * 0.4 - 0.2) * Math.PI * 2 / pillars;
			double r = radius - 2.5;
			int px = center.getX() + (int) Math.round(Math.cos(angle) * r);
			int pz = center.getZ() + (int) Math.round(Math.sin(angle) * r);
			int height = 2 + random.nextInt(6);
			for (int ox = 0; ox <= 1; ox++) {
				for (int oz = 0; oz <= 1; oz++) {
					BlockPos ground = ground(level, px + ox, pz + oz, floorY, maxStep);
					if (ground == null) {
						continue;
					}
					replace(level, ground, Blocks.STONE_BRICKS.defaultBlockState(), changed, false);
					int h = Math.max(1, height - random.nextInt(3));
					for (int y = 1; y <= h; y++) {
						if (!replace(level, ground.above(y), y == h && random.nextFloat() < 0.3 ? topSlab() : pillarBlock(random), changed, true)) {
							break;
						}
					}
				}
			}
			// Fallen chunks at the pillar's foot.
			for (int k = 0; k < 2 + random.nextInt(3); k++) {
				rubble(level, px + random.nextInt(5) - 2, pz + random.nextInt(5) - 2, floorY, maxStep, random, changed);
			}
		}

		// Scattered rubble across the floor.
		for (int k = 0; k < radius; k++) {
			double angle = random.nextDouble() * Math.PI * 2;
			double r = 4 + random.nextDouble() * (radius - 5);
			rubble(level, center.getX() + (int) Math.round(Math.cos(angle) * r), center.getZ() + (int) Math.round(Math.sin(angle) * r), floorY,
					maxStep, random, changed);
		}
		return changed;
	}

	/**
	 * The top block of the ground at (x, z), skipping air and plants from {@code floorY + maxStep + 2} down, or null when
	 * it's out of the step range, not loaded, or not soil/stone (water, leaves...).
	 */
	static @Nullable BlockPos ground(ServerLevel level, int x, int z, int floorY, int maxStep) {
		for (int y = floorY + maxStep + 2; y >= floorY - maxStep; y--) {
			BlockPos pos = new BlockPos(x, y, z);
			if (!level.hasChunkAt(pos) || !level.isInWorldBounds(pos)) {
				return null;
			}
			BlockState state = level.getBlockState(pos);
			if (isOpen(state)) {
				continue;
			}
			// Ground must be something you can stand on; leaves or water mean "not open ground".
			return y <= floorY + maxStep && state.getFluidState().isEmpty() && !state.is(BlockTags.LEAVES) ? pos : null;
		}
		return null;
	}

	private static void rubble(ServerLevel level, int x, int z, int floorY, int maxStep, RandomSource random, List<BlockPos> changed) {
		BlockPos ground = ground(level, x, z, floorY, maxStep);
		if (ground == null) {
			return;
		}
		BlockState state = switch (random.nextInt(4)) {
			case 0 -> Blocks.COBBLESTONE.defaultBlockState();
			case 1 -> Blocks.MOSSY_COBBLESTONE.defaultBlockState();
			default -> Blocks.STONE_BRICK_SLAB.defaultBlockState();
		};
		replace(level, ground.above(), state, changed, true);
	}

	private static BlockState floorBlock(RandomSource random) {
		float r = random.nextFloat();
		if (r < 0.45f) {
			return Blocks.CRACKED_STONE_BRICKS.defaultBlockState();
		}
		if (r < 0.75f) {
			return Blocks.STONE_BRICKS.defaultBlockState();
		}
		if (r < 0.92f) {
			return Blocks.MOSSY_STONE_BRICKS.defaultBlockState();
		}
		return Blocks.COBBLESTONE.defaultBlockState();
	}

	private static BlockState pillarBlock(RandomSource random) {
		float r = random.nextFloat();
		if (r < 0.45f) {
			return Blocks.STONE_BRICKS.defaultBlockState();
		}
		if (r < 0.8f) {
			return Blocks.CRACKED_STONE_BRICKS.defaultBlockState();
		}
		return Blocks.MOSSY_STONE_BRICKS.defaultBlockState();
	}

	private static BlockState topSlab() {
		return Blocks.STONE_BRICK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM);
	}

	/** Removes loose plants standing on a floor block that's about to change (tall grass is two blocks). */
	private static void clearPlants(ServerLevel level, BlockPos above, List<BlockPos> changed) {
		for (int y = 0; y < 2; y++) {
			BlockPos pos = above.above(y);
			BlockState state = level.getBlockState(pos);
			if (state.isAir() || !isOpen(state)) {
				return;
			}
			replace(level, pos, Blocks.AIR.defaultBlockState(), changed, true);
		}
	}

	/**
	 * Sets {@code pos} if what's there is natural: open space when {@code open}, else soil (or open space).
	 *
	 * @return whether it was placed
	 */
	private static boolean replace(ServerLevel level, BlockPos pos, BlockState state, List<BlockPos> changed, boolean open) {
		if (!level.hasChunkAt(pos) || !level.isInWorldBounds(pos)) {
			return false;
		}
		BlockState existing = level.getBlockState(pos);
		if (open ? !isOpen(existing) : !isNatural(existing)) {
			return false;
		}
		if (existing.getBlock() instanceof DoublePlantBlock) {
			// Don't leave half a double plant behind: clear the other half without drops.
			level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_SUPPRESS_DROPS);
		}
		level.setBlock(pos, state, Block.UPDATE_ALL);
		changed.add(pos.immutable());
		return true;
	}
}
