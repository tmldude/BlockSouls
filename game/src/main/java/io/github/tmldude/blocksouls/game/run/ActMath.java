package io.github.tmldude.blocksouls.game.run;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The act machine's numbers as pure functions (no Minecraft classes), so they can be unit-tested. */
public final class ActMath {
	private ActMath() {
	}

	/**
	 * The act the party is in: the open act whose dimension holds the most counted (non-downed,
	 * non-spectator) players; ties between dimensions go to the earlier act; nobody in any open act's
	 * dimension keeps the current act. When several open acts share a dimension (aliased test setups —
	 * real acts have unique dimensions), the dimension belongs to the <em>highest</em> open act with it:
	 * opening the next act in the same dimension moves the party forward.
	 *
	 * @param playersPerDimension counted players by dimension id
	 * @param openActDimensions   dimension id per open act, index 0 = act 1
	 * @param currentOrdinal      the current act (1-based)
	 * @return the 1-based ordinal of the party's act
	 */
	public static int partyAct(Map<String, Integer> playersPerDimension, List<String> openActDimensions, int currentOrdinal) {
		Map<String, Integer> owner = new LinkedHashMap<>();
		for (int ordinal = 1; ordinal <= openActDimensions.size(); ordinal++) {
			owner.put(openActDimensions.get(ordinal - 1), ordinal);
		}
		int bestOrdinal = currentOrdinal;
		int bestCount = 0;
		for (Map.Entry<String, Integer> entry : owner.entrySet()) {
			int count = playersPerDimension.getOrDefault(entry.getKey(), 0);
			if (count > bestCount || (count == bestCount && count > 0 && entry.getValue() < bestOrdinal)) {
				bestOrdinal = entry.getValue();
				bestCount = count;
			}
		}
		return bestCount > 0 ? bestOrdinal : currentOrdinal;
	}
}
