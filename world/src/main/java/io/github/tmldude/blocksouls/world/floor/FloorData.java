package io.github.tmldude.blocksouls.world.floor;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import io.github.tmldude.blocksouls.common.CommonIds;
import io.github.tmldude.blocksouls.common.floor.FloorSlot;
import io.github.tmldude.blocksouls.common.floor.FloorSlotKind;
import io.github.tmldude.blocksouls.common.floor.PlacedFloor;

/**
 * The run's placed floors (persisted with the world): every floor placed this run, which crates were opened and which
 * exit portals are lit. The grid cell counter never resets, so a new run's floors never land on an old run's islands.
 */
public final class FloorData extends SavedData {
	public static final SavedDataType<FloorData> TYPE = new SavedDataType<>(CommonIds.id(CommonIds.WORLD, "floors"), FloorData::new,
			CompoundTag.CODEC.xmap(FloorData::load, FloorData::save), null);

	final List<PlacedFloor> floors = new ArrayList<>();
	/** Crate chests already opened once (packed positions). */
	final Set<Long> openedCrates = new HashSet<>();
	/** Acts whose exit portal is lit. */
	final Set<Integer> litPortals = new HashSet<>();
	/** The next free grid cell in the floor dimension (never reset). */
	int nextCell;
	/** The run's floor seed (0 = not picked yet). */
	long runSeed;

	public static FloorData get(MinecraftServer server) {
		return server.getDataStorage().computeIfAbsent(TYPE);
	}

	/** A new run: forget the old run's floors (their islands stay in the dimension, unused). */
	void reset(long seed) {
		floors.clear();
		openedCrates.clear();
		litPortals.clear();
		runSeed = seed;
		setDirty();
	}

	private static FloorData load(CompoundTag tag) {
		FloorData data = new FloorData();
		data.nextCell = tag.getIntOr("nextCell", 0);
		data.runSeed = tag.getLongOr("runSeed", 0);
		for (long pos : tag.getLongArray("openedCrates").orElse(new long[0])) {
			data.openedCrates.add(pos);
		}
		for (int act : tag.getIntArray("litPortals").orElse(new int[0])) {
			data.litPortals.add(act);
		}
		ListTag list = tag.getListOrEmpty("floors");
		for (int i = 0; i < list.size(); i++) {
			PlacedFloor floor = readFloor(list.getCompoundOrEmpty(i));
			if (floor != null) {
				data.floors.add(floor);
			}
		}
		return data;
	}

	private CompoundTag save() {
		CompoundTag tag = new CompoundTag();
		tag.putInt("nextCell", nextCell);
		tag.putLong("runSeed", runSeed);
		tag.put("openedCrates", new LongArrayTag(openedCrates.stream().mapToLong(Long::longValue).toArray()));
		tag.put("litPortals", new IntArrayTag(litPortals.stream().mapToInt(Integer::intValue).toArray()));
		ListTag list = new ListTag();
		for (PlacedFloor floor : floors) {
			list.add(writeFloor(floor));
		}
		tag.put("floors", list);
		return tag;
	}

	private static CompoundTag writeFloor(PlacedFloor floor) {
		CompoundTag tag = new CompoundTag();
		tag.putInt("act", floor.actOrdinal());
		tag.putInt("number", floor.floorNumber());
		tag.putString("pool", floor.pool().toString());
		tag.putString("template", floor.template().toString());
		tag.putString("dimension", floor.dimension().toString());
		tag.putLong("camp", floor.camp().asLong());
		tag.putInt("radius", floor.radius());
		tag.putInt("groundY", floor.groundY());
		tag.putLong("seed", floor.seed());
		ListTag tags = new ListTag();
		floor.tags().forEach(t -> tags.add(StringTag.valueOf(t)));
		tag.put("tags", tags);
		ListTag slots = new ListTag();
		for (FloorSlot slot : floor.slots()) {
			CompoundTag s = new CompoundTag();
			s.putString("kind", slot.kind().name());
			s.putString("tag", slot.tag());
			s.putLong("pos", slot.pos().asLong());
			s.putInt("radius", slot.radius());
			slots.add(s);
		}
		tag.put("slots", slots);
		return tag;
	}

	private static PlacedFloor readFloor(CompoundTag tag) {
		Identifier pool = Identifier.tryParse(tag.getStringOr("pool", ""));
		Identifier template = Identifier.tryParse(tag.getStringOr("template", ""));
		Identifier dimension = Identifier.tryParse(tag.getStringOr("dimension", ""));
		if (pool == null || template == null || dimension == null) {
			return null;
		}
		Set<String> tags = new HashSet<>();
		ListTag tagList = tag.getListOrEmpty("tags");
		for (int i = 0; i < tagList.size(); i++) {
			tagList.getString(i).ifPresent(tags::add);
		}
		List<FloorSlot> slots = new ArrayList<>();
		ListTag slotList = tag.getListOrEmpty("slots");
		for (int i = 0; i < slotList.size(); i++) {
			CompoundTag s = slotList.getCompoundOrEmpty(i);
			try {
				slots.add(new FloorSlot(FloorSlotKind.valueOf(s.getStringOr("kind", "")), s.getStringOr("tag", ""),
						BlockPos.of(s.getLongOr("pos", 0)), s.getIntOr("radius", 0)));
			} catch (IllegalArgumentException ignored) {
				// a slot kind this build doesn't know; skip it
			}
		}
		return new PlacedFloor(tag.getIntOr("act", 1), tag.getIntOr("number", 1), pool, template, dimension,
				BlockPos.of(tag.getLongOr("camp", 0)), tag.getIntOr("radius", 16), tag.getIntOr("groundY", 80), tag.getLongOr("seed", 0),
				tags, slots);
	}
}
