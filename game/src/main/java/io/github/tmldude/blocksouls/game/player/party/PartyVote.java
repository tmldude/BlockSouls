package io.github.tmldude.blocksouls.game.player.party;

/** Pure party-decision math: how many yes votes open a party relic (and any other majority call). */
public final class PartyVote {
	private PartyVote() {
	}

	/** A strict majority of the voters: 1 of 1, 2 of 2, 2 of 3, 3 of 4, ... 5 of 8. */
	public static int needed(int voters) {
		return Math.max(1, voters) / 2 + 1;
	}

	/** Whether the vote is decided, given the tallies and the voters who haven't voted yet. */
	public static Outcome outcome(int voters, int yes, int no) {
		int needed = needed(voters);
		if (yes >= needed) {
			return Outcome.PASSED;
		}
		int undecided = Math.max(0, voters - yes - no);
		return yes + undecided < needed ? Outcome.FAILED : Outcome.OPEN;
	}

	public enum Outcome {
		OPEN,
		PASSED,
		FAILED
	}
}
