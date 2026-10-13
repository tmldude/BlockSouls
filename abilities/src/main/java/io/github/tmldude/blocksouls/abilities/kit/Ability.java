package io.github.tmldude.blocksouls.abilities.kit;

/** One ability's behavior. The server validates readiness first; the behavior only does the effect. */
@FunctionalInterface
public interface Ability {
	/**
	 * Performs the cast.
	 *
	 * @return whether it went off: {@code false} fizzles (no cooldown, no charge spent), e.g. a pull with no target
	 */
	boolean cast(CastContext ctx);
}
