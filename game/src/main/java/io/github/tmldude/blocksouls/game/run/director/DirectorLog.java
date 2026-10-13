package io.github.tmldude.blocksouls.game.run.director;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * The Director's decision log ({@code /bs director log}): why a night felt the way it did. A bounded ring of
 * one-line entries stamped with run seconds. Pure.
 */
public final class DirectorLog {
	public enum Kind {
		STRESS,
		MOOD,
		COMPOSITION,
		FLANK,
		PACING,
		VIOLATION,
		DAY,
		EVENT,
		OBJECTIVE,
		ECONOMY,
		LOOT
	}

	public record Entry(long second, Kind kind, String text) {
		public String line() {
			return String.format("[%4d:%02d] %-11s %s", second / 60, second % 60, kind, text);
		}
	}

	private final Deque<Entry> entries = new ArrayDeque<>();
	private int capacity;
	private int violations;

	public DirectorLog(int capacity) {
		this.capacity = Math.max(1, capacity);
	}

	public void setCapacity(int newCapacity) {
		capacity = Math.max(1, newCapacity);
		trim();
	}

	public void add(long second, Kind kind, String text) {
		entries.addLast(new Entry(second, kind, text));
		if (kind == Kind.VIOLATION) {
			violations++;
		}
		trim();
	}

	private void trim() {
		while (entries.size() > capacity) {
			entries.removeFirst();
		}
	}

	/** The newest {@code count} entries, oldest first, optionally of one kind. */
	public List<Entry> tail(int count, Kind only) {
		List<Entry> matching = new ArrayList<>();
		for (Entry entry : entries) {
			if (only == null || entry.kind() == only) {
				matching.add(entry);
			}
		}
		return List.copyOf(matching.subList(Math.max(0, matching.size() - Math.max(0, count)), matching.size()));
	}

	public int size() {
		return entries.size();
	}

	/** Pacing-budget violations logged this run. */
	public int violations() {
		return violations;
	}

	public void clear() {
		entries.clear();
		violations = 0;
	}
}
