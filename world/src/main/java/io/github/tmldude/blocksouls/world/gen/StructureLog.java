package io.github.tmldude.blocksouls.world.gen;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import io.github.tmldude.blocksouls.world.BlockSoulsWorld;

/**
 * Where wrecks and checkpoints generated (overworld only), so {@code /bs world locate} can find them. Features are
 * code-placed rather than registered structures, so vanilla's locate can't. Only generated chunks are known.
 */
public class StructureLog extends SavedData {
	public enum Kind implements StringRepresentable {
		WRECK, CHECKPOINT;

		public static final Codec<Kind> CODEC = StringRepresentable.fromEnum(Kind::values);

		@Override
		public String getSerializedName() {
			return name().toLowerCase(Locale.ROOT);
		}
	}

	public record Entry(Kind kind, BlockPos pos) {
		static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
				Kind.CODEC.fieldOf("kind").forGetter(Entry::kind),
				BlockPos.CODEC.fieldOf("pos").forGetter(Entry::pos)).apply(i, Entry::new));
	}

	public static final Codec<StructureLog> CODEC = Entry.CODEC.listOf().xmap(StructureLog::new, log -> log.snapshot());
	public static final SavedDataType<StructureLog> TYPE = new SavedDataType<>(BlockSoulsWorld.id("structures"), StructureLog::new, CODEC, null);

	private final List<Entry> entries;

	public StructureLog() {
		this(List.of());
	}

	private StructureLog(List<Entry> entries) {
		this.entries = new ArrayList<>(entries);
	}

	/** Called from worldgen threads. */
	public static void record(ServerLevel level, Kind kind, BlockPos pos) {
		if (level.dimension() != Level.OVERWORLD) {
			return;
		}
		StructureLog log = get(level);
		synchronized (log) {
			log.entries.add(new Entry(kind, pos.immutable()));
		}
		log.setDirty();
	}

	public static StructureLog get(ServerLevel level) {
		// Data storage lookups aren't thread safe; worldgen threads all funnel through this lock.
		synchronized (StructureLog.class) {
			return level.getServer().getDataStorage().computeIfAbsent(TYPE);
		}
	}

	public synchronized List<Entry> snapshot() {
		return List.copyOf(entries);
	}

	public Optional<Entry> nearest(Kind kind, BlockPos from) {
		return snapshot().stream().filter(e -> e.kind() == kind)
				.min(Comparator.comparingDouble(e -> e.pos().distSqr(from)));
	}
}
