package io.github.tmldude.blocksouls.abilities.kit;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

import io.github.tmldude.blocksouls.abilities.AbilitiesConfig;
import io.github.tmldude.blocksouls.common.ability.AbilityDefinition;

/**
 * Everything a behavior needs for one cast.
 *
 * @param power     synergy multiplier on damage / healing (1 with no matching boon stacks)
 * @param healScale party curve for healing amounts ({@code PartyScaling.abilityHeal})
 * @param party     clamped party size (players in this dimension)
 */
public record CastContext(ServerPlayer player, ServerLevel level, AbilitiesConfig config, AbilityDefinition definition,
		double power, double healScale, int party) {

	public Vec3 eye() {
		return player.getEyePosition();
	}

	public Vec3 look() {
		return player.getLookAngle();
	}

	public Vec3 feet() {
		return player.position();
	}

	public void sound(Vec3 at, SoundEvent sound, float volume, float pitch) {
		level.playSound(null, at.x, at.y, at.z, sound, SoundSource.PLAYERS, volume, pitch);
	}
}
