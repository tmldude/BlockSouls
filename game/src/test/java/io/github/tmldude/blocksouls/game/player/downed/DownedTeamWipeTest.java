package io.github.tmldude.blocksouls.game.player.downed;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class DownedTeamWipeTest {
	/** {@code counts}: in the run and survival/adventure. */
	private record P(boolean counts, boolean downed) {
	}

	private static boolean wipe(P... players) {
		return TeamWipe.isWipe(List.of(players), P::counts, P::downed);
	}

	@Test
	void soloGoingDownIsAWipe() {
		assertTrue(wipe(new P(true, true)));
		assertFalse(wipe(new P(true, false)));
	}

	@Test
	void everyoneWhoCountsMustBeDown() {
		assertTrue(wipe(new P(true, true), new P(true, true)));
		assertFalse(wipe(new P(true, true), new P(true, false)));
	}

	@Test
	void creativeAndSpectatorsDontCount() {
		assertTrue(wipe(new P(true, true), new P(false, false)));
	}

	@Test
	void nobodyCountingIsNotAWipe() {
		assertFalse(wipe());
		assertFalse(wipe(new P(false, false), new P(false, true)));
	}
}
