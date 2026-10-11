package io.github.tmldude.blocksouls.game.client.run;

import java.util.Set;

import net.minecraft.resources.Identifier;

import io.github.tmldude.blocksouls.common.run.RunState;

/**
 * The client's copy of the run, for the HUD and sky effects. The game-loop session fills it from its sync payload;
 * the game-players session's HUD reads it.
 */
public final class ClientRun {
	/**
	 * @param secondsToNext seconds until the next wave starts (during a break or before dusk), or -1 when not counting
	 *                      down
	 * @param mobsLeft      wave mobs still alive in the current wave, or -1 outside a wave
	 */
	public record View(RunState state, int secondsToNext, int mobsLeft, Set<Identifier> nightModifiers) {
		public static final View EMPTY = new View(RunState.NOT_STARTED, -1, -1, Set.of());
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
}
