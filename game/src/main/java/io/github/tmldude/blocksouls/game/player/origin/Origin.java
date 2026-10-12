package io.github.tmldude.blocksouls.game.player.origin;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * An origin (class) definition from {@code data/<ns>/origins/<path>.json}: a movement signature (capability set),
 * a kit, passives and drawbacks, a signature ability id for the abilities module, and weighted synergy tags.
 *
 * @param id               {@code <ns>:<path>}
 * @param icon             a GUI sprite id ({@code <ns>:origin/<name>}) or an item id, drawn on the select screen
 * @param capabilities     the movement verbs this origin holds; everything else is gated off
 * @param kit              items handed over once when the origin is picked
 * @param passives         effects that are the origin's upside
 * @param drawbacks        effects that are its cost
 * @param signatureAbility the ability id the abilities module binds to this origin (inert until it ships)
 * @param synergyTags      {@code tag → weight} for relic offers
 */
public record Origin(String id, String icon, Set<String> capabilities, List<KitEntry> kit, List<OriginEffect> passives,
		List<OriginEffect> drawbacks, Optional<String> signatureAbility, Map<String, Integer> synergyTags, OriginRules rules) {
	public Origin {
		capabilities = Set.copyOf(capabilities);
		kit = List.copyOf(kit);
		passives = List.copyOf(passives);
		drawbacks = List.copyOf(drawbacks);
		synergyTags = Map.copyOf(synergyTags);
	}

	/** One kit line: a plain item stack, or a loot table rolled once (for gear that needs perks rolled). */
	public record KitEntry(Optional<String> item, int count, Optional<String> lootTable) {
		public static KitEntry item(String item, int count) {
			return new KitEntry(Optional.of(item), count, Optional.empty());
		}

		public static KitEntry loot(String table) {
			return new KitEntry(Optional.empty(), 1, Optional.of(table));
		}
	}

	/** Passives then drawbacks, in order; the index into this list keys attribute modifier ids. */
	public List<OriginEffect> effects() {
		return java.util.stream.Stream.concat(passives.stream(), drawbacks.stream()).toList();
	}

	public String path() {
		return id.substring(id.indexOf(':') + 1);
	}
}
