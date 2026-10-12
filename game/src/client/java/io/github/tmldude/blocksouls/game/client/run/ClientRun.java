package io.github.tmldude.blocksouls.game.client.run;

import java.util.Set;

import net.minecraft.resources.Identifier;

import io.github.tmldude.blocksouls.common.run.RunState;

/**
 * The client's copy of the run, for the HUD and sky effects. The game-loop session fills it from its sync payload;
 * the game-players session's HUD reads it. v2 additions: the threat counter ({@code state().threat()}), the act
 * ({@link View#actOrdinal()} of {@link View#actCount()}, {@link View#actName()}), the portal charge countdown, and
 * the day/night cycle phase ({@link #cyclePhase()} — sun or synthetic, whichever drives the player's dimension).
 */
public final class ClientRun {
	/**
	 * @param secondsToNext     seconds until the next wave starts (during a break or before dusk), or -1 when not
	 *                          counting down
	 * @param mobsLeft          wave mobs still alive in the current wave, or -1 outside a wave
	 * @param actOrdinal        the party's act, 1-based (1 in classic)
	 * @param actName           the act's display name
	 * @param actCount          acts in the run (1 in classic)
	 * @param highestOpenAct    highest act whose portal is lit
	 * @param chargeSecondsLeft seconds left on the running portal charge, or -1 when none
	 */
	public record View(RunState state, int secondsToNext, int mobsLeft, Set<Identifier> nightModifiers,
			int actOrdinal, String actName, int actCount, int highestOpenAct, int chargeSecondsLeft) {
		public static final View EMPTY = new View(RunState.NOT_STARTED, -1, -1, Set.of(), 1, "", 1, 1, -1);
	}

	private static View view = View.EMPTY;

	private ClientRun() {
	}

	public static View get() {
		return view;
	}

	public static void set(View newView) {
		view = newView;
	}

	/**
	 * The day/night phase of the player's own dimension (0–23999): the overworld clock under a sun, the run's
	 * synced synthetic cycle everywhere else, or -1 when neither applies (no run, classic off-world).
	 */
	public static int cyclePhase() {
		return ClientCycle.phaseForCurrentDimension();
	}
}
