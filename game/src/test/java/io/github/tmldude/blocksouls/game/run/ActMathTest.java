package io.github.tmldude.blocksouls.game.run;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class ActMathTest {
	private static final String OVERWORLD = "minecraft:overworld";
	private static final String NETHER = "minecraft:the_nether";
	private static final String BLIGHTHEART = "blocksouls_world:blightheart";

	@Test
	void majorityOfThePartyDecidesTheAct() {
		assertEquals(2, ActMath.partyAct(Map.of(OVERWORLD, 1, NETHER, 3), List.of(OVERWORLD, NETHER), 1));
		assertEquals(1, ActMath.partyAct(Map.of(OVERWORLD, 5, NETHER, 2), List.of(OVERWORLD, NETHER), 2));
	}

	@Test
	void tiesGoToTheEarlierAct() {
		assertEquals(1, ActMath.partyAct(Map.of(OVERWORLD, 2, NETHER, 2), List.of(OVERWORLD, NETHER), 2));
	}

	@Test
	void nobodyInAnyOpenActKeepsTheCurrentAct() {
		assertEquals(2, ActMath.partyAct(Map.of(), List.of(OVERWORLD, NETHER), 2));
		assertEquals(2, ActMath.partyAct(Map.of("minecraft:the_end", 4), List.of(OVERWORLD, NETHER), 2));
	}

	@Test
	void closedActsDoNotCount() {
		// Players in the Blightheart, but only acts 1-2 are open: the party act stays where it was.
		assertEquals(1, ActMath.partyAct(Map.of(BLIGHTHEART, 3), List.of(OVERWORLD, NETHER), 1));
	}

	@Test
	void aliasedDimensionsBelongToTheHighestOpenActUnlessCurrentMatches() {
		// Acts 1 and 3 both alias the overworld (test setups): going "back" to the overworld after act 3
		// opened reads as act 3, not act 1...
		assertEquals(3, ActMath.partyAct(Map.of(OVERWORLD, 2), List.of(OVERWORLD, NETHER, OVERWORLD), 2));
		// ...but while act 1 is current, the overworld stays act 1 (stability over aliases).
		assertEquals(1, ActMath.partyAct(Map.of(OVERWORLD, 2), List.of(OVERWORLD, NETHER, OVERWORLD), 1));
	}

	@Test
	void soloAndFullPartyBehaveTheSame() {
		for (int players : new int[] {1, 8}) {
			assertEquals(2, ActMath.partyAct(Map.of(NETHER, players), List.of(OVERWORLD, NETHER), 1),
					players + " players move the front");
		}
	}
}
