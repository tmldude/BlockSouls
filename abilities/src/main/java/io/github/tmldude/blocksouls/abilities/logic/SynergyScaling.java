package io.github.tmldude.blocksouls.abilities.logic;

import java.util.Map;
import java.util.Set;

/** How an ability's power grows with the boon stacks that share its synergy tags. Pure. */
public final class SynergyScaling {
	private SynergyScaling() {
	}

	/** Total stacks of the build's tags that the ability also carries. */
	public static <T> int sharedStacks(Map<T, Integer> build, Set<T> abilityTags) {
		int stacks = 0;
		for (T tag : abilityTags) {
			stacks += Math.max(0, build.getOrDefault(tag, 0));
		}
		return stacks;
	}

	/** {@code 1 + min(sharedStacks · perStack, cap)}. */
	public static <T> double multiplier(Map<T, Integer> build, Set<T> abilityTags, double perStack, double cap) {
		return 1.0 + Math.min(sharedStacks(build, abilityTags) * Math.max(0, perStack), Math.max(0, cap));
	}
}
