package io.github.tmldude.blocksouls.bosses.logic;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.github.tmldude.blocksouls.bosses.api.AttackStats;
import io.github.tmldude.blocksouls.bosses.ashwyrm.AshwyrmConfig;
import io.github.tmldude.blocksouls.bosses.furnace.FurnaceKnightConfig;
import io.github.tmldude.blocksouls.bosses.palerider.PaleRiderConfig;
import io.github.tmldude.blocksouls.bosses.raid.FinalRaidConfig;
import io.github.tmldude.blocksouls.bosses.stormplate.StormplateConfig;

/**
 * The readability rule (PLAN2 §3h) over every attack every boss ships: a hit worth more than 30% of a 20-health player
 * must wind up for at least 15 ticks. Checked at the attack's base damage times the boss's biggest phase damage
 * multiplier, so phase 2 can't sneak a fast big hit in. Walks the configs reflectively, archetype blocks included, so a
 * new attack is covered the moment it gets a config field.
 */
class ReadabilityTest {
	private static final double PLAYER_HEALTH = 20;

	@Test
	void everyBigHitHasALongWindup() throws Exception {
		List<String> failures = new ArrayList<>();
		int checked = 0;
		for (Object config : List.of(new FurnaceKnightConfig(), new StormplateConfig(), new PaleRiderConfig(), new AshwyrmConfig(),
				new FinalRaidConfig())) {
			double phaseMult = maxPhaseDamage(config);
			List<Named> stats = new ArrayList<>();
			collect(config, config.getClass().getSimpleName(), stats, 0);
			for (Named named : stats) {
				checked++;
				double damage = named.stats.damage * phaseMult;
				if (!Fairness.readable(damage, named.stats.windupTicks, PLAYER_HEALTH)) {
					failures.add(named.path + " (" + damage + " dmg, " + named.stats.windupTicks + " windup)");
				}
			}
		}
		assertTrue(checked > 40, "found only " + checked + " attacks");
		assertTrue(failures.isEmpty(), "unreadable big hits: " + failures);
	}

	private record Named(String path, AttackStats stats) {
	}

	private static void collect(Object object, String path, List<Named> out, int depth) throws IllegalAccessException {
		if (depth > 4) {
			return;
		}
		for (Class<?> type = object.getClass(); type != null && type != Object.class; type = type.getSuperclass()) {
			for (Field field : type.getDeclaredFields()) {
				if (Modifier.isStatic(field.getModifiers())) {
					continue;
				}
				field.setAccessible(true);
				Object value = field.get(object);
				if (value instanceof AttackStats stats) {
					out.add(new Named(path + "." + field.getName(), stats));
				} else if (value != null && value.getClass().getName().startsWith("io.github.tmldude.blocksouls.bosses")) {
					collect(value, path + "." + field.getName(), out, depth + 1);
				}
			}
		}
	}

	/** The largest phase damage multiplier in a boss config (fields like {@code phase2Damage}, {@code finalStandDamage}). */
	private static double maxPhaseDamage(Object config) throws IllegalAccessException {
		double max = 1;
		for (Field field : config.getClass().getDeclaredFields()) {
			String name = field.getName();
			if (field.getType() == double.class && (name.matches("phase\\d+Damage") || name.equals("finalStandDamage"))) {
				field.setAccessible(true);
				max = Math.max(max, field.getDouble(config));
			}
		}
		return max;
	}
}
