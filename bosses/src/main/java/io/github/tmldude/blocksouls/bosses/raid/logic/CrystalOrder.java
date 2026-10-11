package io.github.tmldude.blocksouls.bosses.raid.logic;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * The Crystal Order puzzle of the final raid's first phase: the crystals (by slot index) that must be destroyed, in
 * the order the obelisk shows. Destroying the next one advances; destroying any other one is wrong and reshuffles
 * what is left (the wrong crystal re-forms, so it stays in the order). Pure logic, no Minecraft.
 */
public final class CrystalOrder {
	/** What a hit on a crystal did. */
	public enum Result {
		/** It was the next one; more remain. */
		RIGHT,
		/** It was the last one: the order is complete. */
		DONE,
		/** Out of order: the remaining order was reshuffled. */
		WRONG,
		/** Not part of the order (already destroyed, or never in it). */
		IGNORED
	}

	private final List<Integer> remaining;
	private int destroyed;

	public CrystalOrder(List<Integer> order) {
		this.remaining = new ArrayList<>(order);
	}

	/**
	 * Picks {@code count} slots for crystals and orders them randomly. {@code preferred} slots are used first (open
	 * pillars), then {@code fallback} ones (caged pillars), so small groups never have to break a cage.
	 */
	public static CrystalOrder generate(List<Integer> preferred, List<Integer> fallback, int count, Random random) {
		List<Integer> first = new ArrayList<>(preferred);
		List<Integer> second = new ArrayList<>(fallback);
		Collections.shuffle(first, random);
		Collections.shuffle(second, random);
		List<Integer> picked = new ArrayList<>();
		for (List<Integer> pool : List.of(first, second)) {
			for (int slot : pool) {
				if (picked.size() < count && !picked.contains(slot)) {
					picked.add(slot);
				}
			}
		}
		Collections.shuffle(picked, random);
		return new CrystalOrder(picked);
	}

	/** The slot to destroy next, or -1 once the order is complete. */
	public int next() {
		return remaining.isEmpty() ? -1 : remaining.getFirst();
	}

	public boolean isDone() {
		return remaining.isEmpty();
	}

	/** Slots still to destroy, in order. */
	public List<Integer> remaining() {
		return Collections.unmodifiableList(remaining);
	}

	public boolean contains(int slot) {
		return remaining.contains(slot);
	}

	/** How many were destroyed in the right order so far. */
	public int destroyed() {
		return destroyed;
	}

	/** A crystal in {@code slot} was destroyed. */
	public Result hit(int slot, Random random) {
		if (!remaining.contains(slot)) {
			return Result.IGNORED;
		}
		if (remaining.getFirst() == slot) {
			remaining.removeFirst();
			destroyed++;
			return remaining.isEmpty() ? Result.DONE : Result.RIGHT;
		}
		reshuffle(random);
		return Result.WRONG;
	}

	/** Shuffles the remaining order; with two or more left it always comes out different from before. */
	public void reshuffle(Random random) {
		if (remaining.size() < 2) {
			return;
		}
		List<Integer> before = List.copyOf(remaining);
		Collections.shuffle(remaining, random);
		if (remaining.equals(before)) {
			Collections.rotate(remaining, 1);
		}
	}
}
