package io.github.tmldude.blocksouls.game.player.origin;

/** The {@code origins} section of {@code config/blocksouls/game-players.json}. */
public class OriginsConfig {
	/**
	 * Off = the classic shape: nobody picks an origin, everyone keeps every v1 movement verb. On = every player
	 * gets an Origin Sigil at run start and picks a class; solo runs get {@link #soloDefault} right away.
	 */
	public boolean enabled = false;
	/** The origin a solo player starts with when {@link #soloAutoPick} is on (they can still re-pick with the sigil). */
	public String soloDefault = "blocksouls_game:drifter";
	public boolean soloAutoPick = true;
	/** Hand an Origin Sigil to every player at run start (and to late joiners without an origin). */
	public boolean giveSigilAtRunStart = true;
	/** Ticks between re-applying origin effects that something removed (milk, boon refreshes). */
	public int reapplyIntervalTicks = 20;
	/** Conditional effects (heals in rain, slowed in water) are held this many ticks past each re-apply. */
	public int conditionalEffectGraceTicks = 10;
	/** Bloodbound's blood mist on roll: radius (blocks), how long it lingers (ticks) and the blindness it applies (ticks). */
	public double rollMistRadius = 2.5;
	public int rollMistDurationTicks = 40;
	public int rollMistBlindnessTicks = 40;
	/** Bloodbound's hearts per boss kill stop stacking past this many kills. */
	public int bossKillHeartsCap = 10;
}
