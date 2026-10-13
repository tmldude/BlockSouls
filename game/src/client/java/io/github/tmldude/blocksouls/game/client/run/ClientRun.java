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

	/**
	 * The v2 Director's state (expedition only; {@link DirectorView#EMPTY} otherwise).
	 *
	 * @param daySecondsLeft seconds left on the act's day timer, or -1 when no day is running
	 * @param daySeconds     the running day's full length (0 when none)
	 * @param stress         the party's smoothed stress, 0–1
	 * @param mood           the pacer's mood: {@code build}, {@code lull} or {@code spike}
	 * @param objective      the running siege objective's id ({@code supply_drop}, {@code volatile_core},
	 *                       {@code fleeing_zombie}, {@code generator}), or empty; its name is the lang key
	 *                       {@code objective.blocksouls_game.<id>}
	 * @param progress       the objective's progress, 0–1
	 */
	public record DirectorView(int daySecondsLeft, int daySeconds, float stress, String mood, String objective, float progress) {
		public static final DirectorView EMPTY = new DirectorView(-1, 0, 0, "build", "", 0);
	}

	private static View view = View.EMPTY;
	private static DirectorView director = DirectorView.EMPTY;

	private ClientRun() {
	}

	public static View get() {
		return view;
	}

	public static void set(View newView) {
		view = newView;
	}

	public static DirectorView director() {
		return director;
	}

	public static void setDirector(DirectorView newDirector) {
		director = newDirector;
	}

	/**
	 * The day/night phase of the player's own dimension (0–23999): the overworld clock under a sun, the run's
	 * synced synthetic cycle everywhere else, or -1 when neither applies (no run, classic off-world).
	 */
	public static int cyclePhase() {
		return ClientCycle.phaseForCurrentDimension();
	}
}
