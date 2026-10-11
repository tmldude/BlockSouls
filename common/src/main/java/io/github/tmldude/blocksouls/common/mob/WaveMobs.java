package io.github.tmldude.blocksouls.common.mob;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import net.minecraft.resources.Identifier;

/** Registry of wave mobs: mobs registers at init, game picks from it. */
public final class WaveMobs {
	private static final Map<Identifier, WaveMobSpec> SPECS = new LinkedHashMap<>();

	private WaveMobs() {
	}

	public static void register(WaveMobSpec spec) {
		if (SPECS.putIfAbsent(spec.id(), spec) != null) {
			throw new IllegalArgumentException("Wave mob already registered: " + spec.id());
		}
	}

	public static Optional<WaveMobSpec> get(Identifier id) {
		return Optional.ofNullable(SPECS.get(id));
	}

	public static Collection<WaveMobSpec> all() {
		return Collections.unmodifiableCollection(SPECS.values());
	}

	/** Specs that can appear in the given wave. */
	public static List<WaveMobSpec> eligible(int wave) {
		return SPECS.values().stream().filter(spec -> spec.minWave() <= wave).toList();
	}
}
