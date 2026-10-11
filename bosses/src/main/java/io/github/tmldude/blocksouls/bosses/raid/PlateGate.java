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
import io.github.tmldude.blocksouls.bosses.raid.logic.PlateHold;
import io.github.tmldude.blocksouls.bosses.raid.logic.RaidMath;
import io.github.tmldude.blocksouls.common.player.PlayerStatus;

/**
 * Phase 2+, "Grounding". Patches of glowing obsidian set into the island. Armed plates shimmer violet; one with a
 * player on it burns white and rings. While {@link RaidMath#requiredPlates} plates are held together the ring inside
 * each one fills; full, chains of light snap from every plate to the dragon and drag it down to its perch. After it
 * takes off again the plates go dark for a while (rearm).
 */
final class PlateGate {
	private static final DustParticleOptions ARMED = new DustParticleOptions(0x9B4DFF, 1.4f);
	private static final DustParticleOptions HELD = new DustParticleOptions(0xFFFFFF, 1.6f);
	private static final DustParticleOptions DARK = new DustParticleOptions(0x2A1048, 1.0f);

	/** A block the plates replaced, restored when they go away (also after a restart). */
	record Replaced(BlockPos pos, BlockState state) {
		static final Codec<Replaced> CODEC = RecordCodecBuilder.create(i -> i.group(
				BlockPos.CODEC.fieldOf("pos").forGetter(Replaced::pos),
				BlockState.CODEC.fieldOf("state").forGetter(Replaced::state)).apply(i, Replaced::new));
	}

	private final FinalDragon dragon;
	private final List<BlockPos> plates = new ArrayList<>();
	private final Map<BlockPos, BlockState> replaced = new LinkedHashMap<>();
	private final PlateHold hold = new PlateHold();
	private int rearm;
	private int lastHeld;

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

	boolean armed() {
		return placed() && rearm <= 0;
	}

	int lastHeld() {
		return lastHeld;
	}

	float progress() {
		return hold.progress(holdTicks());
	}

	private int holdTicks() {
		return dragon.phase() >= 2 ? config().plateHoldTicksPhase3 : config().plateHoldTicks;
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
		hold.reset();
		rearm = 40;
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
		hold.reset();
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

	/** The plates stay dark for {@code ticks}. */
	void disarm(int ticks) {
		rearm = ticks;
		hold.reset();
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

	/**
	 * One tick: draws the plates and runs the hold timer.
	 *
	 * @param canPull whether the dragon may be pulled down now (flying, not staggered)
	 * @return true when the hold completed: start the pull
	 */
	boolean tick(ServerLevel level, int players, boolean canPull) {
		if (!placed()) {
			return false;
		}
		if (rearm > 0) {
			rearm--;
			if (rearm == 0) {
				for (BlockPos plate : plates) {
					Vec3 c = Vec3.atBottomCenterOf(plate.above());
					level.playSound(null, c.x, c.y, c.z, FinalRaidSounds.PLATE_HUM, SoundSource.HOSTILE, 2f, 1f);
				}
			}
		}
		int held = 0;
		boolean[] occupied = new boolean[plates.size()];
		for (int i = 0; i < plates.size(); i++) {
			occupied[i] = occupied(level, plates.get(i));
			if (occupied[i]) {
				held++;
			}
		}
		boolean complete = false;
		if (armed() && canPull) {
			if (held > lastHeld) {
				level.playSound(null, dragon.arena().centerVec().x, dragon.arena().floorY(), dragon.arena().centerVec().z, FinalRaidSounds.PLATE_HELD,
						SoundSource.HOSTILE, 1.5f, 0.8f + 0.2f * held);
			}
			complete = hold.tick(held, RaidMath.requiredPlates(players), holdTicks());
		} else {
			hold.reset();
		}
		lastHeld = held;
		if (level.getGameTime() % 3 == 0) {
			draw(level, occupied);
		}
		return complete;
	}

	private void draw(ServerLevel level, boolean[] occupied) {
		double r = config().plateRadius + 0.5;
		for (int i = 0; i < plates.size(); i++) {
			Vec3 c = Vec3.atBottomCenterOf(plates.get(i).above());
			if (!armed()) {
				Telegraphs.circle(level, DARK, c, r);
				continue;
			}
			Telegraphs.circle(level, occupied[i] ? HELD : ARMED, c, r);
			if (occupied[i]) {
				// The inner ring fills toward the pull.
				float progress = progress();
				if (progress > 0.05f) {
					Telegraphs.circle(level, HELD, c, r * progress);
				}
				level.sendParticles(ParticleTypes.END_ROD, true, true, c.x, c.y + 0.2, c.z, 2, 0.3, 0.1, 0.3, 0.05);
			} else {
				// An unheld armed plate calls for someone: a slow column.
				level.sendParticles(ARMED, true, true, c.x, c.y + 1 + (level.getGameTime() % 30) / 10.0, c.z, 1, 0.1, 0.1, 0.1, 0);
			}
		}
	}

	/** Chains of light from every plate to {@code to} (drawn during the pull). */
	void chains(ServerLevel level, Vec3 to) {
		for (BlockPos plate : plates) {
			Vec3 from = Vec3.atBottomCenterOf(plate.above());
			Vec3 delta = to.subtract(from);
			int points = (int) Math.min(120, delta.length() * 2);
			for (int i = 0; i <= points; i++) {
				// Links: bright every other half block.
				if (i % 2 == 0) {
					Vec3 p = from.add(delta.scale(i / (double) points));
					level.sendParticles(ParticleTypes.END_ROD, true, true, p.x, p.y, p.z, 1, 0, 0, 0, 0);
				}
			}
		}
	}
}
