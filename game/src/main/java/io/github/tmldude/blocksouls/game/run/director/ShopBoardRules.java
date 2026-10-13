package io.github.tmldude.blocksouls.game.run.director;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jspecify.annotations.Nullable;

/**
 * The pure rules of the shopkeeper's forge and brew boards (docs/decisions/v2-boards.md): which entries a
 * floor shows, whether an inventory holds a recipe's ingredients, which enchant spec a floor uses and an
 * item's next gear tier. No Minecraft classes, so the offline unit tests run them directly.
 */
public final class ShopBoardRules {
	private ShopBoardRules() {
	}

	public enum Board {
		FORGE, BREW;

		/** {@code "forge"} / {@code "brew"} (any case), or null. */
		public static @Nullable Board parse(@Nullable String name) {
			if (name == null) {
				return null;
			}
			return switch (name.trim().toLowerCase(java.util.Locale.ROOT)) {
				case "forge" -> FORGE;
				case "brew" -> BREW;
				default -> null;
			};
		}

		public String id() {
			return name().toLowerCase(java.util.Locale.ROOT);
		}
	}

	/** Where the player stands: the floor number (the act ordinal off a floor) and the floor's tags. */
	public record FloorView(int floor, Set<String> tags) {
		public FloorView {
			tags = Set.copyOf(tags);
		}
	}

	/** Floors and tags an entry, enchant spec or anything else floor-bound shows on. */
	public record Placement(int minFloor, int maxFloor, Set<String> floorTags) {
		public static final Placement EVERYWHERE = new Placement(1, 99, Set.of());

		public Placement {
			floorTags = Set.copyOf(floorTags);
		}

		/** In range, and (with tags) the floor has at least one of them. */
		public boolean shows(FloorView view) {
			if (view.floor() < minFloor || view.floor() > maxFloor) {
				return false;
			}
			if (floorTags.isEmpty()) {
				return true;
			}
			for (String tag : floorTags) {
				if (view.tags().contains(tag)) {
					return true;
				}
			}
			return false;
		}
	}

	/**
	 * One recipe on a board. {@code units} has one entry per item the recipe consumes: the item ids that can
	 * fill it (a tag ingredient has several).
	 */
	public record Entry(String recipe, Board board, String result, int resultCount, List<List<String>> units, Placement placement,
			boolean hidden) {
		public Entry {
			units = units.stream().map(List::copyOf).toList();
		}

		/** The units grouped into kinds (same options) with their counts, in first-seen order. */
		public List<Kind> kinds() {
			Map<List<String>, Integer> counts = new LinkedHashMap<>();
			for (List<String> unit : units) {
				counts.merge(unit, 1, Integer::sum);
			}
			List<Kind> out = new ArrayList<>();
			counts.forEach((options, count) -> out.add(new Kind(options, count)));
			return out;
		}
	}

	/** An ingredient kind: any of {@code options}, {@code count} times. */
	public record Kind(List<String> options, int count) {
	}

	/** The entries of one board shown on {@code view}, in the order given. */
	public static List<Entry> visible(List<Entry> entries, Board board, FloorView view) {
		List<Entry> out = new ArrayList<>();
		for (Entry entry : entries) {
			if (entry.board() == board && !entry.hidden() && entry.placement().shows(view)) {
				out.add(entry);
			}
		}
		return out;
	}

	/**
	 * Which board a recipe goes on when its entry doesn't say: a board tag on the result, then brew for
	 * consumables (food, consumable component, ammo), else forge.
	 */
	public static Board classify(@Nullable Board fromEntry, boolean taggedBrew, boolean taggedForge, boolean consumable) {
		if (fromEntry != null) {
			return fromEntry;
		}
		if (taggedBrew) {
			return Board.BREW;
		}
		if (taggedForge) {
			return Board.FORGE;
		}
		return consumable ? Board.BREW : Board.FORGE;
	}

	/**
	 * Fills every unit from {@code inventory} (item id → count): the item each unit takes, in unit order, or
	 * null when the inventory can't cover them all. Most-constrained units go first, with backtracking, so a
	 * tag unit never steals the only item a narrower unit needed.
	 */
	public static @Nullable List<String> match(List<List<String>> units, Map<String, Integer> inventory) {
		Integer[] order = new Integer[units.size()];
		for (int i = 0; i < order.length; i++) {
			order[i] = i;
		}
		java.util.Arrays.sort(order, Comparator.comparingInt(i -> units.get(i).size()));
		Map<String, Integer> left = new HashMap<>(inventory);
		String[] picked = new String[units.size()];
		return fill(units, order, 0, left, picked) ? List.of(picked) : null;
	}

	private static boolean fill(List<List<String>> units, Integer[] order, int at, Map<String, Integer> left, String[] picked) {
		if (at == order.length) {
			return true;
		}
		int unit = order[at];
		for (String option : units.get(unit)) {
			int have = left.getOrDefault(option, 0);
			if (have <= 0) {
				continue;
			}
			left.put(option, have - 1);
			picked[unit] = option;
			if (fill(units, order, at + 1, left, picked)) {
				return true;
			}
			left.put(option, have);
		}
		return false;
	}

	/** What a unit-matched recipe still lacks: per kind, how many are missing (only kinds short of their count). */
	public static Map<List<String>, Integer> missing(List<Kind> kinds, Map<String, Integer> inventory) {
		Map<List<String>, Integer> out = new LinkedHashMap<>();
		Map<String, Integer> left = new HashMap<>(inventory);
		List<Kind> sorted = new ArrayList<>(kinds);
		sorted.sort(Comparator.comparingInt(kind -> kind.options().size()));
		for (Kind kind : sorted) {
			int need = kind.count();
			for (String option : kind.options()) {
				int take = Math.min(need, left.getOrDefault(option, 0));
				left.merge(option, -take, Integer::sum);
				need -= take;
			}
			if (need > 0) {
				out.put(kind.options(), need);
			}
		}
		return out;
	}

	/** A floor's forge enchant service. */
	public record EnchantSpec(String source, Placement placement, String metal, int metalCount, int souls, List<String> enchantments) {
		public EnchantSpec {
			enchantments = List.copyOf(enchantments);
		}
	}

	/** The spec for {@code view}: shown specs with floor tags beat ones without, then the highest minFloor, then load order. */
	public static @Nullable EnchantSpec enchantSpec(List<EnchantSpec> specs, FloorView view) {
		EnchantSpec best = null;
		for (EnchantSpec spec : specs) {
			if (!spec.placement().shows(view)) {
				continue;
			}
			if (best == null || rank(spec) > rank(best)) {
				best = spec;
			}
		}
		return best;
	}

	private static long rank(EnchantSpec spec) {
		return (spec.placement().floorTags().isEmpty() ? 0 : 1_000L) + spec.placement().minFloor();
	}

	/** The item after {@code item} in the first chain (load order) where it has a next tier, or null. */
	public static @Nullable String nextTier(List<List<String>> chains, String item) {
		for (List<String> chain : chains) {
			int at = chain.indexOf(item);
			if (at >= 0 && at + 1 < chain.size()) {
				return chain.get(at + 1);
			}
		}
		return null;
	}

	/** The level an enchant roll sets: 1 when new, +1 when already there, or 0 when it's already at max. */
	public static int enchantLevel(int current, int max) {
		if (current <= 0) {
			return 1;
		}
		return current < max ? current + 1 : 0;
	}

	/** Entries per board page: two per row on the five rows above the bottom bar. */
	public static final int PER_PAGE = 10;

	public static int pages(int entries) {
		return Math.max(1, (entries + PER_PAGE - 1) / PER_PAGE);
	}
}
