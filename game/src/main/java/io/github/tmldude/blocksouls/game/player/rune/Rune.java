package io.github.tmldude.blocksouls.game.player.rune;

import java.util.List;
import java.util.Set;

import io.github.tmldude.blocksouls.game.player.classes.ClassEffect;

/**
 * A rune: one permanent unlock in a class's tree ({@code data/<ns>/runes/<path>.json}). Bought once with rune
 * souls (the meta currency paid out at run end) and kept across runs; it applies whenever the player plays its
 * class: small perks (effects, attributes, traits), movement verbs (the Ranger's wings) and ability mods (ids the
 * abilities module reads).
 *
 * @param id           {@code <ns>:<path>}
 * @param playerClass  the class whose tree it sits in
 * @param cost         rune souls
 * @param requires     rune ids that must be unlocked first (tree edges)
 * @param capabilities movement verbs granted while playing the class
 * @param effects      perks applied while playing the class
 * @param abilityMods  ability-mod ids for the abilities module (inert here)
 */
public record Rune(String id, String playerClass, int cost, List<String> requires, Set<String> capabilities, List<ClassEffect> effects,
		List<String> abilityMods) {
	public Rune {
		requires = List.copyOf(requires);
		capabilities = Set.copyOf(capabilities);
		effects = List.copyOf(effects);
		abilityMods = List.copyOf(abilityMods);
	}

	public String path() {
		return id.substring(id.indexOf(':') + 1);
	}
}
