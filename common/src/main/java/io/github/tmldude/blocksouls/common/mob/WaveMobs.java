package io.github.tmldude.blocksouls.common.mob;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import net.minecraft.resources.Identifier;

/** Registry of wave mobs: mobs registers at init, game picks from it. */
public final class WaveMobs {
	/** Floor escalation tag (world's floors 2+): elites join the roster from the floor's first wave. */
	public static final String MORE_ELITES = "more_elites";
	/** Floor escalation tag (world's floors 5+): elites always carry an affix. */
	public static final String ELITE_AFFIXES = "elite_affixes";
	/**
	 * Entity tag ({@code Entity.addTag}) marking a mob the Director sent as a flanker: mobs gives it a goal that
	 * circles to the side its target isn't facing before closing in.
	 */
	public static final String FLANKER_TAG = "blocksouls_flanker";

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

	/**
	 * Specs that can appear in the given wave on an Expedition floor: {@link #eligible(int)}, narrowed to specs whose
	 * {@code minFloor} has been reached and whose {@code tags} the floor carries. With the {@link #MORE_ELITES}
	 * escalation tag, elites skip their {@code minWave}.
	 *
	 * @param floor     1-based floor number
	 * @param floorTags the floor's tags (its escalation tags, {@code floor_<n>}, pool tags)
	 */
	public static List<WaveMobSpec> eligible(int wave, int floor, Set<String> floorTags) {
		return SPECS.values().stream().filter(spec -> eligible(spec, wave, floor, floorTags)).toList();
	}

	/** The floor-roster rule for one spec (see {@link #eligible(int, int, Set)}). */
	public static boolean eligible(WaveMobSpec spec, int wave, int floor, Set<String> floorTags) {
		boolean waveOk = spec.minWave() <= wave || spec.elite() && floorTags.contains(MORE_ELITES);
		return waveOk && spec.minFloor() <= floor && floorTags.containsAll(spec.tags());
	}
}
