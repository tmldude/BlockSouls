package io.github.tmldude.blocksouls.game.client.player;

import java.util.ArrayList;
import java.util.List;

import io.github.tmldude.blocksouls.game.player.boon.BoonPayloads.BoonView;
import io.github.tmldude.blocksouls.game.player.boon.BoonPayloads.TagView;

/** The local player's boons, curses, trinkets and build line as the server last sent them. */
public final class ClientBoons {
	private static List<BoonView> owned = List.of();
	private static List<BoonView> boonsAndCurses = List.of();
	private static List<BoonView> trinkets = List.of();
	private static List<TagView> buildLine = List.of();

	private ClientBoons() {
	}

	/** Boons and curses (combo parts already left out): the boon row. */
	public static List<BoonView> get() {
		return boonsAndCurses;
	}

	/** Trinkets in slot order: the trinket row. */
	public static List<BoonView> trinkets() {
		return trinkets;
	}

	/** Everything owned, as sent. */
	public static List<BoonView> all() {
		return owned;
	}

	/** Top synergy tags by stacks, best first. */
	public static List<TagView> buildLine() {
		return buildLine;
	}

	static void set(List<BoonView> views) {
		owned = List.copyOf(views);
		List<BoonView> row = new ArrayList<>();
		List<BoonView> slots = new ArrayList<>();
		for (BoonView view : views) {
			(view.isTrinket() ? slots : row).add(view);
		}
		boonsAndCurses = List.copyOf(row);
		trinkets = List.copyOf(slots);
	}

	static void setBuildLine(List<TagView> tags) {
		buildLine = List.copyOf(tags);
	}

	static void reset() {
		set(List.of());
		buildLine = List.of();
	}
}
