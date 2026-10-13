package io.github.tmldude.blocksouls.game.player.classes;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * A class definition from {@code data/<ns>/classes/<path>.json}: a movement signature (verbs added on top of the
 * universal v1 kit), a kit, passives and drawbacks, a signature ability id for the abilities module, rule tweaks
 * (slam landing, hover, revive speed, ...) and weighted synergy tags.
 *
 * @param id               {@code <ns>:<path>}
 * @param icon             a GUI sprite id ({@code <ns>:class/<name>}) or an item id, drawn on the select screen
 * @param capabilities     signature movement verbs granted on top of the universal kit (the kit itself is never gated)
 * @param kit              items handed over once when the class is picked
 * @param passives         effects that are the class's upside
 * @param drawbacks        effects that are its cost
 * @param signatureAbility the ability id the abilities module binds to this class (inert until it ships)
 * @param synergyTags      {@code tag → weight} for relic offers
 */
public record PlayerClass(String id, String icon, Set<String> capabilities, List<KitEntry> kit, List<ClassEffect> passives,
		List<ClassEffect> drawbacks, Optional<String> signatureAbility, Map<String, Integer> synergyTags, ClassRules rules) {
	public PlayerClass {
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
	public List<ClassEffect> effects() {
		return java.util.stream.Stream.concat(passives.stream(), drawbacks.stream()).toList();
	}

	public String path() {
		return id.substring(id.indexOf(':') + 1);
	}
}
