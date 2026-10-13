package io.github.tmldude.blocksouls.game.run.director;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import io.github.tmldude.blocksouls.common.CommonIds;

/**
 * The Director's persisted day-loop state: the day timer, the camp, the horn's buff for the coming siege, cursed
 * chests' modifier bumps, soul wagers, altar payments, and which acts already had their event and mini-boss.
 * Stress, the influence map and the pacing budget are live-only (they rebuild in seconds); so are a running
 * objective and pylon challenge, which a restart cancels.
 */
public final class DirectorData extends SavedData {
	public static final SavedDataType<DirectorData> TYPE = new SavedDataType<>(CommonIds.id(CommonIds.GAME, "director"), DirectorData::new,
			CompoundTag.CODEC.xmap(DirectorData::load, DirectorData::save), null);

	/** Ticks left on the day timer (counts down only while players are online); 0 when no day is running. */
	int dayTicksLeft;
	/** The running day's full length in ticks (for the horn's skipped share and the HUD). */
	int dayTicks;
	/** The act the day timer belongs to. */
	int dayAct;
	@Nullable String campDimension;
	long campPos;
	/** The act the camp was placed for. */
	int campAct;
	/** Wave-drop tier steps the horn bought for the coming siege. */
	int hornTierShift;
	/** Relics per player owed when the coming siege completes (the overclock horn). */
	int siegeRelics;
	/** Modifier tiers cursed chests added for the next siege. */
	int pendingBumps;
	/** Souls staked per player on the running siege. */
	final Map<UUID, Integer> wagers = new HashMap<>();
	/** Hearts paid at the blood altar per player this run (removed when the run ends). */
	final Map<UUID, Integer> altarHearts = new HashMap<>();
	/** Acts whose random event / mini-boss was already placed. */
	final Set<Integer> eventActs = new HashSet<>();
	final Set<Integer> miniBossActs = new HashSet<>();
	/** Seed for this run's shop stock and event rolls. */
	long seed;

	public static DirectorData get(MinecraftServer server) {
		return server.getDataStorage().computeIfAbsent(TYPE);
	}

	void reset(long newSeed) {
		dayTicksLeft = 0;
		dayTicks = 0;
		dayAct = 0;
		campDimension = null;
		campPos = 0;
		campAct = 0;
		hornTierShift = 0;
		siegeRelics = 0;
		pendingBumps = 0;
		wagers.clear();
		eventActs.clear();
		miniBossActs.clear();
		seed = newSeed;
		setDirty();
	}

	public boolean dayRunning() {
		return dayTicksLeft > 0;
	}

	public int daySecondsLeft() {
		return (dayTicksLeft + 19) / 20;
	}

	public int daySeconds() {
		return dayTicks / 20;
	}

	public @Nullable BlockPos camp() {
		return campDimension == null ? null : BlockPos.of(campPos);
	}

	public @Nullable String campDimension() {
		return campDimension;
	}

	private static DirectorData load(CompoundTag tag) {
		DirectorData data = new DirectorData();
		data.dayTicksLeft = tag.getIntOr("dayTicksLeft", 0);
		data.dayTicks = tag.getIntOr("dayTicks", 0);
		data.dayAct = tag.getIntOr("dayAct", 0);
		String dimension = tag.getStringOr("campDimension", "");
		data.campDimension = dimension.isEmpty() ? null : dimension;
		data.campPos = tag.getLongOr("campPos", 0);
		data.campAct = tag.getIntOr("campAct", 0);
		data.hornTierShift = tag.getIntOr("hornTierShift", 0);
		data.siegeRelics = tag.getIntOr("siegeRelics", 0);
		data.pendingBumps = tag.getIntOr("pendingBumps", 0);
		readCounts(tag.getCompoundOrEmpty("wagers"), data.wagers);
		readCounts(tag.getCompoundOrEmpty("altarHearts"), data.altarHearts);
		for (int act : tag.getIntArray("eventActs").orElse(new int[0])) {
			data.eventActs.add(act);
		}
		for (int act : tag.getIntArray("miniBossActs").orElse(new int[0])) {
			data.miniBossActs.add(act);
		}
		data.seed = tag.getLongOr("seed", 0);
		return data;
	}

	private CompoundTag save() {
		CompoundTag tag = new CompoundTag();
		tag.putInt("dayTicksLeft", dayTicksLeft);
		tag.putInt("dayTicks", dayTicks);
		tag.putInt("dayAct", dayAct);
		tag.putString("campDimension", campDimension == null ? "" : campDimension);
		tag.putLong("campPos", campPos);
		tag.putInt("campAct", campAct);
		tag.putInt("hornTierShift", hornTierShift);
		tag.putInt("siegeRelics", siegeRelics);
		tag.putInt("pendingBumps", pendingBumps);
		tag.put("wagers", writeCounts(wagers));
		tag.put("altarHearts", writeCounts(altarHearts));
		tag.put("eventActs", new IntArrayTag(eventActs.stream().mapToInt(Integer::intValue).toArray()));
		tag.put("miniBossActs", new IntArrayTag(miniBossActs.stream().mapToInt(Integer::intValue).toArray()));
		tag.putLong("seed", seed);
		return tag;
	}

	private static void readCounts(CompoundTag tag, Map<UUID, Integer> into) {
		for (String key : tag.keySet()) {
			try {
				into.put(UUID.fromString(key), tag.getIntOr(key, 0));
			} catch (IllegalArgumentException ignored) {
				// A hand-edited key; skip it.
			}
		}
	}

	private static CompoundTag writeCounts(Map<UUID, Integer> counts) {
		CompoundTag tag = new CompoundTag();
		counts.forEach((id, count) -> tag.putInt(id.toString(), count));
		return tag;
	}
}
