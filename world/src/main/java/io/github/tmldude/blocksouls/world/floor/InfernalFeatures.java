package io.github.tmldude.blocksouls.world.floor;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;

import io.github.tmldude.blocksouls.world.BlockSoulsWorld;

/**
 * The template features the Infernal floor added (v2-floor2-infernal), all no-ops on templates without them: lava
 * channels and their bridges, basalt spires, eternal fires and the island's biome (sky, fog, ash, ambience).
 */
final class InfernalFeatures {
	private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
	/** Low bridges: a crimson-plank deck one block over the lava (the rise sinks it). */
	static final BlockState LOW_DECK = Blocks.CRIMSON_PLANKS.defaultBlockState();
	/** The high bridge: a blackstone arch level with the ground (the rise leaves it standing). */
	static final BlockState HIGH_DECK = Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState();

	private final FloorBuilder builder;
	private final ServerLevel level;
	private final FloorTemplate template;
	private final FloorPlanner.FloorPlan plan;
	private final BlockPos centre;
	private final RandomSource random;

	InfernalFeatures(FloorBuilder builder, ServerLevel level, FloorTemplate template, FloorPlanner.FloorPlan plan, BlockPos centre,
			RandomSource random) {
		this.builder = builder;
		this.level = level;
		this.template = template;
		this.plan = plan;
		this.centre = centre;
		this.random = random;
	}

	/** The lava surface's y on a floor with channels. */
	static int lavaY(int groundY, LavaChannels.Spec spec) {
		return groundY - Math.max(1, spec.lavaDepth);
	}

	/**
	 * Cuts the channels: every channel column is walled and floored with the base block (so the lava can't leak out of
	 * a thin edge), cleared above the lava and filled with lava sources; then the bridges go across.
	 */
	void channelsAndBridges() {
		LavaChannels.Plan channels = builder.channels;
		if (channels == null) {
			return;
		}
		int lava = lavaY(plan.groundY(), template.channels);
		BlockState base = FloorBuilder.block(template.terrain.base, Blocks.BLACKSTONE);
		BlockState lavaBlock = Blocks.LAVA.defaultBlockState();
		int radius = plan.radius();
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int x = -radius; x <= radius; x++) {
			for (int z = -radius; z <= radius; z++) {
				if (x * x + z * z > radius * radius || channels.distance(x, z) > 1) {
					continue;
				}
				// The channel and a one-block lip: solid from two under the lava up to the lava (lip: up to the bank).
				for (int y = lava - 2; y <= lava; y++) {
					if (level.getBlockState(pos.set(centre.getX() + x, y, centre.getZ() + z)).isAir()) {
						level.setBlock(pos, base, FLAGS);
					}
				}
			}
		}
		for (int x = -radius; x <= radius; x++) {
			for (int z = -radius; z <= radius; z++) {
				if (x * x + z * z > radius * radius || !channels.inChannel(x, z)) {
					continue;
				}
				int h = builder.height(x, z);
				for (int y = lava + 1; y <= h; y++) {
					level.setBlock(pos.set(centre.getX() + x, y, centre.getZ() + z), Blocks.AIR.defaultBlockState(), FLAGS);
				}
				level.setBlock(pos.set(centre.getX() + x, lava, centre.getZ() + z), lavaBlock, FLAGS);
				level.setBlock(pos.set(centre.getX() + x, lava - 1, centre.getZ() + z), Blocks.MAGMA_BLOCK.defaultBlockState(), FLAGS);
			}
		}
		for (LavaChannels.Bridge bridge : channels.bridges()) {
			bridge(bridge, lava, channels);
		}
	}

	/** Lays one bridge's deck over every channel column it covers (plus rails on the low ones). */
	private void bridge(LavaChannels.Bridge bridge, int lava, LavaChannels.Plan channels) {
		int reach = (int) Math.ceil(bridge.halfLength() + bridge.halfWidth()) + 1;
		int cx = (int) Math.round(bridge.x());
		int cz = (int) Math.round(bridge.z());
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int x = cx - reach; x <= cx + reach; x++) {
			for (int z = cz - reach; z <= cz + reach; z++) {
				if (!bridge.covers(x, z) || !channels.inChannel(x, z)) {
					continue;
				}
				int y = bridge.high() ? plan.groundY() : lava + 1;
				level.setBlock(pos.set(centre.getX() + x, y, centre.getZ() + z), bridge.high() ? HIGH_DECK : LOW_DECK, FLAGS);
				if (bridge.high()) {
					// The arch: the deck rests on a pillar of the same brick under its middle row.
					double across = -(x - bridge.x()) * bridge.az() + (z - bridge.z()) * bridge.ax();
					if (Math.abs(across) < 0.6) {
						for (int below = lava; below < y; below++) {
							level.setBlock(pos.set(centre.getX() + x, below, centre.getZ() + z), HIGH_DECK, FLAGS);
						}
					}
				}
			}
		}
	}

	/** Basalt spires and eternal fires, outside slots and channels. */
	void decorate() {
		LavaChannels.Plan channels = builder.channels;
		double reach = plan.radius() * 0.85;
		int placed = 0;
		for (int attempt = 0; placed < template.spires && attempt < template.spires * 20; attempt++) {
			int[] at = point(reach);
			if (builder.nearSlot(at[0], at[1], 3) || (channels != null && channels.distance(at[0], at[1]) < 3)) {
				continue;
			}
			BlockPos pos = builder.surface(at[0], at[1]);
			int height = 2 + random.nextInt(5);
			for (int y = 0; y < height; y++) {
				level.setBlock(pos.above(y), Blocks.BASALT.defaultBlockState(), FLAGS);
				if (y < height - 2 && random.nextInt(3) == 0) {
					level.setBlock(pos.above(y).relative(net.minecraft.core.Direction.Plane.HORIZONTAL.getRandomDirection(random)),
							Blocks.BASALT.defaultBlockState(), FLAGS);
				}
			}
			placed++;
		}
		placed = 0;
		for (int attempt = 0; placed < template.fires && attempt < template.fires * 20; attempt++) {
			int[] at = point(reach);
			if (builder.nearSlot(at[0], at[1], 4) || (channels != null && channels.distance(at[0], at[1]) < 2)) {
				continue;
			}
			BlockPos pos = builder.surface(at[0], at[1]);
			if (level.getBlockState(pos).isAir()) {
				level.setBlock(pos.below(), Blocks.NETHERRACK.defaultBlockState(), FLAGS);
				level.setBlock(pos, Blocks.FIRE.defaultBlockState(), FLAGS);
				placed++;
			}
		}
	}

	private int[] point(double reach) {
		double angle = random.nextDouble() * Math.PI * 2;
		double distance = Math.sqrt(random.nextDouble()) * reach;
		return new int[] {(int) Math.round(Math.cos(angle) * distance), (int) Math.round(Math.sin(angle) * distance)};
	}
}
