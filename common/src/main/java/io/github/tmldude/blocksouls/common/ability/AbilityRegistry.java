package io.github.tmldude.blocksouls.common.ability;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import net.minecraft.resources.Identifier;

/**
 * Every ability the pack knows, by id. The abilities module registers its kit from its initializer (on both sides,
 * so clients can draw icons and cooldowns); other modules only read. Re-registering an id replaces the definition
 * (a config reload re-applies cooldowns this way).
 */
public final class AbilityRegistry {
	private static final Map<Identifier, AbilityDefinition> ABILITIES = Collections.synchronizedMap(new LinkedHashMap<>());

	private AbilityRegistry() {
	}

	public static AbilityDefinition register(AbilityDefinition definition) {
		ABILITIES.put(definition.id(), definition);
		return definition;
	}

	public static @Nullable AbilityDefinition get(@Nullable Identifier id) {
		return id == null ? null : ABILITIES.get(id);
	}

	public static boolean isKnown(Identifier id) {
		return ABILITIES.containsKey(id);
	}

	/** Every registered ability, in registration order. */
	public static Collection<AbilityDefinition> all() {
		synchronized (ABILITIES) {
			return List.copyOf(ABILITIES.values());
		}
	}

	/** Registered abilities that fit {@code slot}, in registration order. */
	public static List<AbilityDefinition> bySlot(AbilitySlot slot) {
		List<AbilityDefinition> out = new ArrayList<>();
		for (AbilityDefinition definition : all()) {
			if (definition.slot() == slot) {
				out.add(definition);
			}
		}
		return out;
	}
}
