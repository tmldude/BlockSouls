package io.github.tmldude.blocksouls.mobs.affix;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import io.github.tmldude.blocksouls.common.act.Acts;
import io.github.tmldude.blocksouls.common.floor.Floors;
import io.github.tmldude.blocksouls.common.floor.PlacedFloor;

/** Which floor a spawn is on, for affix rolls (Classic = floor 0). */
public final class MobFloors {
	private MobFloors() {
	}

	/**
	 * @param floor 1-based floor number; 0 in Classic
	 * @param tags  the floor's tags (escalation, pool)
	 */
	public record Here(int floor, Set<String> tags) {
		public static final Here CLASSIC = new Here(0, Set.of());
	}

	/**
	 * The floor whose island {@code pos} is over; else, in a multi-act run, the latest floor placed (or 1); else Classic.
	 */
	public static Here at(ServerLevel level, BlockPos pos) {
		Optional<PlacedFloor> floor = Floors.at(level.getServer(), level.dimension().identifier(), pos);
		if (floor.isPresent()) {
			return new Here(floor.get().floorNumber(), floor.get().tags());
		}
		if (Acts.all(level.getServer()).size() <= 1) {
			return Here.CLASSIC;
		}
		List<PlacedFloor> placed = Floors.all(level.getServer());
		return placed.isEmpty() ? new Here(1, Set.of()) : new Here(Math.max(1, placed.getLast().floorNumber()), Set.of());
	}
}
