package io.github.tmldude.blocksouls.game.player.party;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** Party math at 1 and 8 players, as every party-dependent rule must be. */
class PartyVoteTest {
	@Test
	void strictMajorityAtEveryPartySize() {
		assertEquals(1, PartyVote.needed(1), "solo decides alone");
		assertEquals(2, PartyVote.needed(2));
		assertEquals(2, PartyVote.needed(3));
		assertEquals(3, PartyVote.needed(4));
		assertEquals(5, PartyVote.needed(8), "a full party needs five");
		assertEquals(1, PartyVote.needed(0), "never zero");
	}

	@Test
	void outcomesResolveAsSoonAsTheyAreDecided() {
		assertEquals(PartyVote.Outcome.PASSED, PartyVote.outcome(1, 1, 0));
		assertEquals(PartyVote.Outcome.FAILED, PartyVote.outcome(1, 0, 1));
		assertEquals(PartyVote.Outcome.OPEN, PartyVote.outcome(8, 4, 0), "four of eight is not yet a majority");
		assertEquals(PartyVote.Outcome.PASSED, PartyVote.outcome(8, 5, 3));
		assertEquals(PartyVote.Outcome.FAILED, PartyVote.outcome(8, 2, 4), "two yes plus two undecided can't reach five");
		assertEquals(PartyVote.Outcome.OPEN, PartyVote.outcome(8, 3, 3), "three yes plus two undecided still can");
	}
}
