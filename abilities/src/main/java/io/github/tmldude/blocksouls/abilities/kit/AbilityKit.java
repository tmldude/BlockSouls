package io.github.tmldude.blocksouls.abilities.kit;

import java.util.Map;

import org.jspecify.annotations.Nullable;

import net.minecraft.resources.Identifier;

import io.github.tmldude.blocksouls.abilities.AbilityIds;

/** Ability id → behavior. */
public final class AbilityKit {
	private static final Map<Identifier, Ability> BEHAVIORS = Map.ofEntries(
			Map.entry(AbilityIds.FRAG, Tacticals::frag),
			Map.entry(AbilityIds.WEB_BOMB, Tacticals::webBomb),
			Map.entry(AbilityIds.FIRE_WALL, Tacticals::fireWall),
			Map.entry(AbilityIds.HEALING_DART, Tacticals::healingDart),
			Map.entry(AbilityIds.GROUND_POUND, Signatures::groundPound),
			Map.entry(AbilityIds.WEB_PULL, Signatures::webPull),
			Map.entry(AbilityIds.EMBER_WAVE, Signatures::emberWave),
			Map.entry(AbilityIds.GEYSER, Signatures::geyser),
			Map.entry(AbilityIds.TAUNT_SLAM, Signatures::tauntSlam),
			Map.entry(AbilityIds.FAN_THE_HAMMER, Signatures::fanTheHammer),
			Map.entry(AbilityIds.OVERCLOCK, Signatures::overclock),
			Map.entry(AbilityIds.TRANSFUSION, Signatures::transfusion),
			Map.entry(AbilityIds.STORM_BARRAGE, Ultimates::stormBarrage),
			Map.entry(AbilityIds.MELEE_FRENZY, Ultimates::meleeFrenzy),
			Map.entry(AbilityIds.PARTY_OVERSHIELD, Ultimates::partyOvershield));

	private AbilityKit() {
	}

	public static @Nullable Ability behavior(Identifier id) {
		return BEHAVIORS.get(id);
	}
}
