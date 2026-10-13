package io.github.tmldude.blocksouls.game.player.rune;

/** The {@code runes} section of {@code config/blocksouls/game-players.json}. */
public class RunesConfig {
	/** Rune souls per siege survived (a floor in the Floor Loop, a night in Classic). */
	public double soulsPerSiege = 10;
	/** The performance payout grows by this fraction per point of threat reached. */
	public double threatBonusPerThreat = 0.05;
	/** The share of in-run souls left unspent at run end that carries into rune souls (the rest is lost). */
	public double unspentShare = 0.25;
	/** A run that survived at least one siege always pays at least this much. */
	public int minimumPayout = 5;
	/** Multiplies every rune's listed cost (quick global tuning). */
	public double costMultiplier = 1.0;
}
