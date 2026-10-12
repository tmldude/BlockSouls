package io.github.tmldude.blocksouls.common.floor;

import java.util.List;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;

/**
 * A floor the world has placed for a run: which template, where it sits in the floor dimension, and
 * every slot it filled. Immutable; the world keeps the live record and other modules read it through
 * {@link Floors}.
 *
 * @param actOrdinal  the act it was placed for
 * @param floorNumber its number in the run (1 = first)
 * @param pool        the template pool it came from
 * @param template    the template it was varied from
 * @param dimension   the dimension it sits in (the floor dimension)
 * @param camp        the camp anchor block (ground level at the centre): where the party arrives
 * @param radius      the island's radius from the camp (blocks)
 * @param groundY     the island's nominal ground level
 * @param seed        the variation seed
 * @param tags        the template's tags plus the escalation tags applied for this floor number
 * @param slots       every filled slot, camp first
 */
public record PlacedFloor(int actOrdinal, int floorNumber, Identifier pool, Identifier template, Identifier dimension,
		BlockPos camp, int radius, int groundY, long seed, Set<String> tags, List<FloorSlot> slots) {
	public PlacedFloor {
		camp = camp.immutable();
		tags = Set.copyOf(tags);
		slots = List.copyOf(slots);
	}

	/** The slots of one kind, in placement order. */
	public List<FloorSlot> slots(FloorSlotKind kind) {
		return slots.stream().filter(slot -> slot.kind() == kind).toList();
	}

	/** Whether {@code pos} is over the island (within the radius of the camp, horizontally). */
	public boolean contains(BlockPos pos) {
		long dx = pos.getX() - camp.getX();
		long dz = pos.getZ() - camp.getZ();
		return dx * dx + dz * dz <= (long) radius * radius;
	}

	public boolean hasTag(String tag) {
		return tags.contains(tag);
	}
}
