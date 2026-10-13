package io.github.tmldude.blocksouls.abilities.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

class SynergyScalingTest {
	@Test
	void onlySharedTagsCount() {
		Map<String, Integer> build = Map.of("fire", 3, "on_kill", 2, "heal", 1);
		assertEquals(3, SynergyScaling.sharedStacks(build, Set.of("fire")));
		assertEquals(5, SynergyScaling.sharedStacks(build, Set.of("fire", "on_kill")));
		assertEquals(0, SynergyScaling.sharedStacks(build, Set.of("storm")));
	}

	@Test
	void multiplierGrowsPerStackAndCaps() {
		Map<String, Integer> build = Map.of("fire", 3);
		assertEquals(1.24, SynergyScaling.multiplier(build, Set.of("fire"), 0.08, 0.6), 1e-9);
		assertEquals(1.6, SynergyScaling.multiplier(Map.of("fire", 50), Set.of("fire"), 0.08, 0.6), 1e-9);
		assertEquals(1.0, SynergyScaling.multiplier(build, Set.of("storm"), 0.08, 0.6), 1e-9);
	}
}
