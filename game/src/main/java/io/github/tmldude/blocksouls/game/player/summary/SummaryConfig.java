package io.github.tmldude.blocksouls.game.player.summary;

/** The {@code summary} section of {@code config/blocksouls/game-players.json}: the run summary v2 cards. */
public class SummaryConfig {
	/** Off = only the v1 title + chat summary; no cards, no player line in the run log. */
	public boolean enabled = true;
	/** Open the card screen on every client at run end (off = cards are still logged). */
	public boolean showScreen = true;
	/** How often (ticks) the build line is sampled during a run; the card shows the peak build. */
	public int buildSampleIntervalTicks = 100;
	/** Synergy tags shown per card. */
	public int buildTags = 3;
	/** Cards per row on the summary screen; 8 players at 4 per row = two rows. */
	public int columns = 4;
	/** Append one JSON line with every card to {@code blocksouls-runs.log}. */
	public boolean logCards = true;
}
