package io.github.tmldude.blocksouls.game.run;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
	void aliasedDimensionsBelongToTheHighestOpenAct() {
		// Acts 1 and 3 both alias the overworld (test setups; real acts have unique dimensions): the
		// overworld reads as act 3 once act 3 is open, whatever the current act — so opening the next act
		// in the same dimension moves the party forward.
		assertEquals(3, ActMath.partyAct(Map.of(OVERWORLD, 2), List.of(OVERWORLD, NETHER, OVERWORLD), 2));
		assertEquals(3, ActMath.partyAct(Map.of(OVERWORLD, 2), List.of(OVERWORLD, NETHER, OVERWORLD), 1));
		// While only act 1 is open, its dimension is its own.
		assertEquals(1, ActMath.partyAct(Map.of(OVERWORLD, 2), List.of(OVERWORLD), 1));
	}

	@Test
	void soloAndFullPartyBehaveTheSame() {
		for (int players : new int[] {1, 8}) {
			assertEquals(2, ActMath.partyAct(Map.of(NETHER, players), List.of(OVERWORLD, NETHER), 1),
					players + " players move the front");
		}
	}

	// ---- v2-floor5-blight: two sieges in one night

	@Test
	void oneSiegeActsLightAfterTheFirst() {
		assertFalse(ActMath.anotherSiege(0, 1));
		assertFalse(ActMath.anotherSiege(0, 0), "0 reads as one siege");
	}

	@Test
	void twoSiegeActsOweASecond() {
		assertTrue(ActMath.anotherSiege(0, 2), "the first siege leaves the portal dark");
		assertFalse(ActMath.anotherSiege(1, 2), "the second lights it");
	}

	@Test
	void floorFiveHasTwoSiegesByDefault() {
		List<RunConfig.ActSpec> acts = new RunConfig().floorActs;
		for (int i = 0; i < acts.size(); i++) {
			assertEquals(i == 4 ? 2 : 1, acts.get(i).sieges, "floor " + (i + 1));
		}
	}
}
