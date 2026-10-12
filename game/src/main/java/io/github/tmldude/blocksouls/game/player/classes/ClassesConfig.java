package io.github.tmldude.blocksouls.game.player.classes;

/** The {@code classes} section of {@code config/blocksouls/game-players.json}. */
public class ClassesConfig {
	/**
	 * Off = the classic shape: nobody picks a class. On = every player gets a Class Sigil at run start and picks a
	 * class; solo runs get {@link #soloDefault} right away. The universal movement kit is on either way.
	 */
	public boolean enabled = false;
	/** The class a solo player starts with when {@link #soloAutoPick} is on (they can still re-pick with the sigil). */
	public String soloDefault = "blocksouls_game:striker";
	public boolean soloAutoPick = true;
	/** Hand a Class Sigil to every player at run start (and to late joiners without a class). */
	public boolean giveSigilAtRunStart = true;
	/** Ticks between re-applying playerClass effects that something removed (milk, boon refreshes). */
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
