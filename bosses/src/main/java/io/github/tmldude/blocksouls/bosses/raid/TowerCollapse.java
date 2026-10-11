package io.github.tmldude.blocksouls.bosses.raid;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import io.github.tmldude.blocksouls.bosses.api.Telegraphs;

/**
 * The obsidian towers come down when the dragon hits the ground (start of phase 2): one after another, each first
 * rumbles with dust at its foot, then crumbles from the top down, throwing chunks of obsidian. They stay down (the
 * island is the open arena for the rest of the raid). Only for the End island; works from the pillar positions in
 * {@link RaidArena}.
 */
final class TowerCollapse {
	/** Ticks of rumbling at the foot before a tower starts to fall. */
	private static final int RUMBLE_TICKS = 30;

	private record Tower(int x, int z, int radius, int top) {
	}

	private final List<Tower> towers = new ArrayList<>();
	private final int floorY;
	private final int stagger;
	private int clock;

	TowerCollapse(RaidArena arena, int stagger) {
		this.floorY = arena.floorY();
		this.stagger = Math.max(1, stagger);
		for (int i = 0; i < arena.crystals().size(); i++) {
			Vec3 top = arena.crystals().get(i);
			towers.add(new Tower((int) Math.floor(top.x), (int) Math.floor(top.z), arena.spikeRadii().get(i), (int) top.y));
		}
	}

	boolean done() {
		return clock > towers.size() * stagger + RUMBLE_TICKS + 120;
	}

	void tick(ServerLevel level) {
		clock++;
		for (int i = 0; i < towers.size(); i++) {
			Tower tower = towers.get(i);
			int t = clock - i * stagger;
			if (t < 0) {
				continue;
			}
			Vec3 foot = new Vec3(tower.x + 0.5, floorY, tower.z + 0.5);
			if (t < RUMBLE_TICKS) {
				if (t % 4 == 0) {
					Telegraphs.circle(level, new BlockParticleOption(ParticleTypes.BLOCK, Blocks.OBSIDIAN.defaultBlockState()), foot, tower.radius + 1.5);
					level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, true, true, foot.x, foot.y + 0.5, foot.z, 4, tower.radius, 0.2, tower.radius, 0.01);
				}
				if (t == 0) {
					level.playSound(null, foot.x, foot.y, foot.z, SoundEvents.ANCIENT_DEBRIS_BREAK, SoundSource.BLOCKS, 6f, 0.4f);
				}
				continue;
			}
			// Two layers per tick, from the top down to just above the island.
			int y = tower.top - (t - RUMBLE_TICKS) * 2;
			for (int layer = 0; layer < 2; layer++) {
				int row = y - layer;
				if (row <= floorY - 1 || row > tower.top) {
					continue;
				}
				clearLayer(level, tower, row);
			}
			if (y > floorY && (t - RUMBLE_TICKS) % 6 == 0) {
				level.playSound(null, foot.x, y, foot.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, 4f, 0.5f + level.getRandom().nextFloat() * 0.2f);
			}
		}
	}

	private static void clearLayer(ServerLevel level, Tower tower, int y) {
		int r = tower.radius + 2;
		for (int dx = -r; dx <= r; dx++) {
			for (int dz = -r; dz <= r; dz++) {
				BlockPos pos = new BlockPos(tower.x + dx, y, tower.z + dz);
				BlockState state = level.getBlockState(pos);
				if (!(state.is(Blocks.OBSIDIAN) || state.is(Blocks.IRON_BARS) || state.is(Blocks.BEDROCK) || state.is(Blocks.FIRE))) {
					continue;
				}
				// A few chunks fly off as falling blocks; the rest crumble to dust.
				if (state.is(Blocks.OBSIDIAN) && level.getRandom().nextInt(10) == 0) {
					FallingBlockEntity chunk = FallingBlockEntity.fall(level, pos, state);
					chunk.setDeltaMovement((level.getRandom().nextDouble() - 0.5) * 0.6, 0.2, (level.getRandom().nextDouble() - 0.5) * 0.6);
					chunk.disableDrop();
				} else {
					level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
					if (level.getRandom().nextInt(4) == 0) {
						level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 4, 0.4,
								0.4, 0.4, 0.1);
					}
				}
			}
		}
	}
}
