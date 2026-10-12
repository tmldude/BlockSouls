package io.github.tmldude.blocksouls.common.floor;

import net.minecraft.core.BlockPos;

/**
 * One filled placement slot on a placed floor: what it is, where, and how big.
 *
 * @param kind   what the slot is for
 * @param tag    kind-specific detail (a node's block id, a structure kind, a crate's loot table, a ring index);
 *               never null, empty when the kind needs none
 * @param pos    the slot's anchor block (ground level at the slot)
 * @param radius how far the slot's footprint reaches from {@code pos} (0 for a point)
 */
public record FloorSlot(FloorSlotKind kind, String tag, BlockPos pos, int radius) {
	public FloorSlot {
		if (tag == null) {
			tag = "";
		}
		pos = pos.immutable();
		radius = Math.max(0, radius);
	}

	/** Whether {@code at} is inside this slot's footprint (horizontally). */
	public boolean contains(BlockPos at) {
		int dx = at.getX() - pos.getX();
		int dz = at.getZ() - pos.getZ();
		return dx * dx + dz * dz <= (long) radius * radius;
	}
}
