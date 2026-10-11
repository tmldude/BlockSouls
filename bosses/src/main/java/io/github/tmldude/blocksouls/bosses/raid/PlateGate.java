package io.github.tmldude.blocksouls.bosses.raid;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import io.github.tmldude.blocksouls.bosses.api.Telegraphs;
import io.github.tmldude.blocksouls.bosses.raid.logic.RaidMath;
import io.github.tmldude.blocksouls.common.player.PlayerStatus;

/**
 * Phase 3's plates: patches of glowing obsidian set into the island. A plate with a player on it burns white and rings;
 * while {@link RaidMath#requiredPlates} plates are held together, chains of light run from every held plate to the
 * obelisk and the crystals of the order unseal. Step off and they seal again.
 */
final class PlateGate {
	private static final DustParticleOptions ARMED = new DustParticleOptions(0x9B4DFF, 1.4f);
	private static final DustParticleOptions HELD = new DustParticleOptions(0xFFFFFF, 1.6f);

	/** A block the plates replaced, restored when they go away (also after a restart). */
	record Replaced(BlockPos pos, BlockState state) {
		static final Codec<Replaced> CODEC = RecordCodecBuilder.create(i -> i.group(
				BlockPos.CODEC.fieldOf("pos").forGetter(Replaced::pos),
				BlockState.CODEC.fieldOf("state").forGetter(Replaced::state)).apply(i, Replaced::new));
	}

	private final FinalDragon dragon;
	private final List<BlockPos> plates = new ArrayList<>();
	private final Map<BlockPos, BlockState> replaced = new LinkedHashMap<>();
	private int lastHeld;
	private boolean open;

	PlateGate(FinalDragon dragon) {
		this.dragon = dragon;
	}

	private static FinalRaidConfig config() {
		return FinalRaids.CONFIG;
	}

	List<BlockPos> plates() {
		return plates;
	}

	boolean placed() {
		return !plates.isEmpty();
	}

	int lastHeld() {
		return lastHeld;
	}

	/** Enough plates held right now: the crystals are open. */
	boolean open() {
		return open;
	}

	/** Sets the plates for {@code players} into the island (replacing any earlier ones). */
	void place(ServerLevel level, int players) {
		remove(level);
		int count = Math.min(RaidMath.plateSpots(players), dragon.arena().plates().size());
		int r = config().plateRadius;
		for (int i = 0; i < count; i++) {
			BlockPos center = dragon.arena().plates().get(i);
			for (int dx = -r; dx <= r; dx++) {
				for (int dz = -r; dz <= r; dz++) {
					BlockPos pos = center.offset(dx, 0, dz);
					BlockState state = level.getBlockState(pos);
					if (level.getBlockEntity(pos) != null) {
						continue;
					}
					replaced.putIfAbsent(pos.immutable(), state);
					level.setBlock(pos, Blocks.CRYING_OBSIDIAN.defaultBlockState(), Block.UPDATE_CLIENTS);
				}
			}
			plates.add(center);
		}
		open = false;
		lastHeld = 0;
	}

	/** Puts the island back the way it was. */
	void remove(ServerLevel level) {
		for (var entry : replaced.entrySet()) {
			if (level.getBlockState(entry.getKey()).is(Blocks.CRYING_OBSIDIAN)) {
				level.setBlock(entry.getKey(), entry.getValue(), Block.UPDATE_CLIENTS);
			}
		}
		replaced.clear();
		plates.clear();
		open = false;
		lastHeld = 0;
	}

	List<Replaced> saved() {
		return replaced.entrySet().stream().map(e -> new Replaced(e.getKey(), e.getValue())).toList();
	}

	/** After a restart: the fight resets, so the saved plates go away. */
	void restore(ServerLevel level, List<Replaced> saved) {
		for (Replaced r : saved) {
			replaced.putIfAbsent(r.pos(), r.state());
		}
		remove(level);
	}

	/** Whether someone stands on the plate centered at {@code plate}. */
	boolean occupied(ServerLevel level, BlockPos plate) {
		double reach = config().plateRadius + 0.5;
		Vec3 c = Vec3.atBottomCenterOf(plate.above());
		for (ServerPlayer player : level.players()) {
			if (player.isSpectator() || !player.isAlive() || PlayerStatus.isDowned(player)) {
				continue;
			}
			double dx = player.getX() - c.x;
			double dz = player.getZ() - c.z;
			double dy = player.getY() - c.y;
			if (Math.abs(dx) <= reach && Math.abs(dz) <= reach && dy > -0.6 && dy < 2.5) {
				return true;
			}
		}
		return false;
	}

	/** One tick: who stands where, draws the plates (and the chains while open). Returns whether the crystals are open. */
	boolean tick(ServerLevel level, int players) {
		if (!placed()) {
			open = false;
			return false;
		}
		int held = 0;
		boolean[] occupied = new boolean[plates.size()];
		for (int i = 0; i < plates.size(); i++) {
			occupied[i] = occupied(level, plates.get(i));
			if (occupied[i]) {
				held++;
			}
		}
		if (held > lastHeld) {
			Vec3 c = dragon.arena().centerVec();
			level.playSound(null, c.x, c.y, c.z, FinalRaidSounds.PLATE_HELD, SoundSource.HOSTILE, 1.5f, 0.8f + 0.2f * held);
		}
		lastHeld = held;
		// Never more than are placed (a small test arena may have fewer spots than a big party needs).
		boolean nowOpen = held >= Math.min(RaidMath.requiredPlates(players), plates.size());
		if (nowOpen && !open) {
			Vec3 c = dragon.arena().obelisk();
			level.playSound(null, c.x, c.y, c.z, FinalRaidSounds.CHAINS, SoundSource.HOSTILE, 4f, 0.8f);
		}
		open = nowOpen;
		if (level.getGameTime() % 3 == 0) {
			double r = config().plateRadius + 0.5;
			for (int i = 0; i < plates.size(); i++) {
				Vec3 c = Vec3.atBottomCenterOf(plates.get(i).above());
				Telegraphs.circle(level, occupied[i] ? HELD : ARMED, c, r);
				if (occupied[i]) {
					level.sendParticles(ParticleTypes.END_ROD, true, true, c.x, c.y + 0.2, c.z, 2, 0.3, 0.1, 0.3, 0.05);
					if (open) {
						chain(level, c, dragon.arena().obelisk());
					}
				} else {
					// An unheld plate calls for someone: a slow rising mote.
					level.sendParticles(ARMED, true, true, c.x, c.y + 1 + (level.getGameTime() % 30) / 10.0, c.z, 1, 0.1, 0.1, 0.1, 0);
				}
			}
		}
		return open;
	}

	/** A chain of light from {@code from} to {@code to}. */
	private static void chain(ServerLevel level, Vec3 from, Vec3 to) {
		Vec3 delta = to.subtract(from);
		int points = (int) Math.min(120, delta.length() * 2);
		for (int i = 0; i <= points; i += 2) {
			Vec3 p = from.add(delta.scale(i / (double) Math.max(1, points)));
			level.sendParticles(ParticleTypes.END_ROD, true, true, p.x, p.y, p.z, 1, 0, 0, 0, 0);
		}
	}
}
