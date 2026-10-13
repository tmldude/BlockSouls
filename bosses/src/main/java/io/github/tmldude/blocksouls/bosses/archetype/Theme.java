package io.github.tmldude.blocksouls.bosses.archetype;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.sounds.SoundEvent;

/**
 * How an archetype looks and sounds on a given boss: its telegraph colors, the particle of the hit itself, and the
 * windup and impact sounds. One per boss, so a ring from the Furnace Knight burns and one from Stormplate crackles.
 *
 * @param outline telegraph edge
 * @param fill    telegraph fill as the windup runs out
 * @param burst   the live hitbox (ring front, pool surface, beam, orb body)
 * @param windup  played when the windup starts
 * @param impact  played when the hitbox goes live
 */
public record Theme(ParticleOptions outline, ParticleOptions fill, ParticleOptions burst, SoundEvent windup, SoundEvent impact) {
}
