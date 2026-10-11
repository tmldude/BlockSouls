package io.github.tmldude.blocksouls.common.mob;

import java.util.Set;

import net.minecraft.resources.Identifier;

import io.github.tmldude.blocksouls.common.run.Difficulty;

/**
 * Everything a wave mob factory needs to know when the game spawns it.
 *
 * @param nightModifiers ids of tonight's modifiers (e.g. fog, sprinters, blood moon)
 * @param breaksBlocks   whether this particular mob may slowly break player-placed blocks
 */
public record WaveContext(int wave, Difficulty difficulty, Set<Identifier> nightModifiers, boolean breaksBlocks) {
}
