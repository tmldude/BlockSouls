package io.github.tmldude.blocksouls.game.run;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import io.github.tmldude.blocksouls.common.CommonIds;
import io.github.tmldude.blocksouls.common.run.Difficulty;
import io.github.tmldude.blocksouls.common.run.RunPhase;
import io.github.tmldude.blocksouls.common.run.RunState;
import io.github.tmldude.blocksouls.game.run.clock.SyntheticCycle;

/** Everything about the run that must survive a restart (world-level saved data). */
public final class RunData extends SavedData {
	public static final SavedDataType<RunData> TYPE = new SavedDataType<>(CommonIds.id(RunIds.NAMESPACE, "run"), RunData::new,
			CompoundTag.CODEC.xmap(RunData::load, RunData::save), null);

	RunPhase phase = RunPhase.NOT_STARTED;
	int wave;
	int night;
	boolean endless;
	Difficulty difficulty = Difficulty.NORMAL;
	long runSeed;
	/** Game time (overworld) when the run started / ended. */
	long startGameTime;
	long endGameTime;
	final Set<UUID> gotKit = new LinkedHashSet<>();
	/** Starter Pistols are gone for good (wave {@code starterPistolLastWave} ended). */
	boolean pistolsBroken;
	final List<String> bossesKilled = new ArrayList<>();
	final Set<String> nightModifiers = new LinkedHashSet<>();
	final Map<String, Integer> rosterOffsets = new HashMap<>();

	/** Whether the overworld clock was in the night window last tick (for dusk/dawn edges). */
	boolean wasNight;
	/** The current night was counted (someone was online at dusk), so dawn fires DAWN. */
	boolean nightCounted;
	/** The dusk cue already played for the coming night. */
	boolean duskCuePlayed;
	/** Waves started this night, and this night's budget. */
	int wavesThisNight;
	int nightBudget;

	/** Current wave bookkeeping. */
	int waveTotal;
	int waveSpawned;
	int waveElites;
	final Set<UUID> waveMobs = new LinkedHashSet<>();
	/** Elites that outlived their wave and keep hunting until killed. */
	final Set<UUID> hunters = new LinkedHashSet<>();
	/** Game time when the current break ends (phase WAVE_BREAK). */
	long breakEndsAt;
	/** The last player to kill a wave mob this wave (reward chest goes near them). */
	UUID lastKiller;

	/** The run paused the overworld clock because the server is empty. */
	boolean clockPausedByRun;

	// ---------------------------------------------------------------- v2 expedition spine

	/** The act the party is in (1-based; expedition shape). */
	int actOrdinal = 1;
	/** The highest act whose entry portal is lit (acts up to here can be traveled to). */
	int highestOpenAct = 1;
	/** Act ids the party has entered at least once this run (ACT_ENTERED fires once per act). */
	final Set<String> actsEntered = new LinkedHashSet<>();
	/** The act whose exit portal is charging (0 = no charge running). */
	int chargingAct;
	/** Game time (overworld) when the running portal charge completes. */
	long chargeEndsAt;
	/** The run's synthetic day/night cycle for dimensions without a sun. */
	final SyntheticCycle synthetic = new SyntheticCycle();

	public RunState state() {
		return new RunState(phase, wave, night, endless, difficulty);
	}

	boolean isActiveRun() {
		return phase != RunPhase.NOT_STARTED && phase != RunPhase.OVER;
	}

	public static RunData get(MinecraftServer server) {
		return server.getDataStorage().computeIfAbsent(TYPE);
	}

	/** Resets everything for a fresh run (keeps nothing from the previous one). */
	void reset() {
		phase = RunPhase.NOT_STARTED;
		wave = 0;
		night = 0;
		endless = false;
		difficulty = Difficulty.NORMAL;
		runSeed = 0;
		startGameTime = 0;
		endGameTime = 0;
		gotKit.clear();
		pistolsBroken = false;
		bossesKilled.clear();
		nightModifiers.clear();
		rosterOffsets.clear();
		duskCuePlayed = false;
		nightCounted = false;
		wavesThisNight = 0;
		nightBudget = 0;
		clearWave();
		hunters.clear();
		breakEndsAt = 0;
		actOrdinal = 1;
		highestOpenAct = 1;
		actsEntered.clear();
		chargingAct = 0;
		chargeEndsAt = 0;
		synthetic.reset();
		setDirty();
	}

	void clearWave() {
		waveTotal = 0;
		waveSpawned = 0;
		waveElites = 0;
		waveMobs.clear();
		lastKiller = null;
		setDirty();
	}

	private static RunData load(CompoundTag tag) {
		RunData data = new RunData();
		data.phase = parsePhase(tag.getStringOr("phase", RunPhase.NOT_STARTED.name()));
		data.wave = tag.getIntOr("wave", 0);
		data.night = tag.getIntOr("night", 0);
		data.endless = tag.getBooleanOr("endless", false);
		CompoundTag diff = tag.getCompoundOrEmpty("difficulty");
		data.difficulty = new Difficulty(diff.getStringOr("id", "normal"), diff.getDoubleOr("waveSize", 1), diff.getDoubleOr("mobHealth", 1),
				diff.getDoubleOr("mobDamage", 1));
		data.runSeed = tag.getLongOr("runSeed", 0);
		data.startGameTime = tag.getLongOr("startGameTime", 0);
		data.endGameTime = tag.getLongOr("endGameTime", 0);
		readUuids(tag.getListOrEmpty("gotKit"), data.gotKit);
		data.pistolsBroken = tag.getBooleanOr("pistolsBroken", false);
		readStrings(tag.getListOrEmpty("bossesKilled"), data.bossesKilled);
		readStrings(tag.getListOrEmpty("nightModifiers"), data.nightModifiers);
		CompoundTag offsets = tag.getCompoundOrEmpty("rosterOffsets");
		for (String key : offsets.keySet()) {
			data.rosterOffsets.put(key, offsets.getIntOr(key, 0));
		}
		data.wasNight = tag.getBooleanOr("wasNight", false);
		data.duskCuePlayed = tag.getBooleanOr("duskCuePlayed", false);
		data.nightCounted = tag.getBooleanOr("nightCounted", false);
		data.wavesThisNight = tag.getIntOr("wavesThisNight", 0);
		data.nightBudget = tag.getIntOr("nightBudget", 0);
		data.waveTotal = tag.getIntOr("waveTotal", 0);
		data.waveSpawned = tag.getIntOr("waveSpawned", 0);
		data.waveElites = tag.getIntOr("waveElites", 0);
		readUuids(tag.getListOrEmpty("waveMobs"), data.waveMobs);
		readUuids(tag.getListOrEmpty("hunters"), data.hunters);
		data.breakEndsAt = tag.getLongOr("breakEndsAt", 0);
		String killer = tag.getStringOr("lastKiller", "");
		data.lastKiller = killer.isEmpty() ? null : parseUuid(killer);
		data.clockPausedByRun = tag.getBooleanOr("clockPausedByRun", false);
		data.actOrdinal = Math.max(1, tag.getIntOr("actOrdinal", 1));
		data.highestOpenAct = Math.max(data.actOrdinal, tag.getIntOr("highestOpenAct", 1));
		readStrings(tag.getListOrEmpty("actsEntered"), data.actsEntered);
		data.chargingAct = tag.getIntOr("chargingAct", 0);
		data.chargeEndsAt = tag.getLongOr("chargeEndsAt", 0);
		data.synthetic.load(tag.getLongOr("synthTicks", 0), tag.getFloatOr("synthRemainder", 0),
				tag.getBooleanOr("synthStarted", false));
		return data;
	}

	private CompoundTag save() {
		CompoundTag tag = new CompoundTag();
		tag.putString("phase", phase.name());
		tag.putInt("wave", wave);
		tag.putInt("night", night);
		tag.putBoolean("endless", endless);
		CompoundTag diff = new CompoundTag();
		diff.putString("id", difficulty.id());
		diff.putDouble("waveSize", difficulty.waveSize());
		diff.putDouble("mobHealth", difficulty.mobHealth());
		diff.putDouble("mobDamage", difficulty.mobDamage());
		tag.put("difficulty", diff);
		tag.putLong("runSeed", runSeed);
		tag.putLong("startGameTime", startGameTime);
		tag.putLong("endGameTime", endGameTime);
		tag.put("gotKit", writeStrings(gotKit.stream().map(UUID::toString).toList()));
		tag.putBoolean("pistolsBroken", pistolsBroken);
		tag.put("bossesKilled", writeStrings(bossesKilled));
		tag.put("nightModifiers", writeStrings(nightModifiers));
		CompoundTag offsets = new CompoundTag();
		rosterOffsets.forEach(offsets::putInt);
		tag.put("rosterOffsets", offsets);
		tag.putBoolean("wasNight", wasNight);
		tag.putBoolean("duskCuePlayed", duskCuePlayed);
		tag.putBoolean("nightCounted", nightCounted);
		tag.putInt("wavesThisNight", wavesThisNight);
		tag.putInt("nightBudget", nightBudget);
		tag.putInt("waveTotal", waveTotal);
		tag.putInt("waveSpawned", waveSpawned);
		tag.putInt("waveElites", waveElites);
		tag.put("waveMobs", writeStrings(waveMobs.stream().map(UUID::toString).toList()));
		tag.put("hunters", writeStrings(hunters.stream().map(UUID::toString).toList()));
		tag.putLong("breakEndsAt", breakEndsAt);
		tag.putString("lastKiller", lastKiller == null ? "" : lastKiller.toString());
		tag.putBoolean("clockPausedByRun", clockPausedByRun);
		tag.putInt("actOrdinal", actOrdinal);
		tag.putInt("highestOpenAct", highestOpenAct);
		tag.put("actsEntered", writeStrings(actsEntered));
		tag.putInt("chargingAct", chargingAct);
		tag.putLong("chargeEndsAt", chargeEndsAt);
		tag.putLong("synthTicks", synthetic.saveTotalTicks());
		tag.putFloat("synthRemainder", synthetic.saveRemainder());
		tag.putBoolean("synthStarted", synthetic.started());
		return tag;
	}

	private static RunPhase parsePhase(String name) {
		try {
			return RunPhase.valueOf(name);
		} catch (IllegalArgumentException e) {
			return RunPhase.NOT_STARTED;
		}
	}

	private static UUID parseUuid(String value) {
		try {
			return UUID.fromString(value);
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	private static void readStrings(ListTag list, java.util.Collection<String> into) {
		for (int i = 0; i < list.size(); i++) {
			list.getString(i).ifPresent(into::add);
		}
	}

	private static void readUuids(ListTag list, Set<UUID> into) {
		for (int i = 0; i < list.size(); i++) {
			list.getString(i).map(RunData::parseUuid).ifPresent(into::add);
		}
	}

	private static ListTag writeStrings(java.util.Collection<String> values) {
		ListTag list = new ListTag();
		for (String value : values) {
			Tag tag = StringTag.valueOf(value);
			list.add(tag);
		}
		return list;
	}
}
