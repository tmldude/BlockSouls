package io.github.tmldude.blocksouls.game.player.origin;

import java.util.List;
import java.util.Set;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * What a player's origin attachment stores: the origin id, which origins' kits they were handed this run (so a
 * re-pick never farms a kit twice) and boss kills (Bloodbound's hearts).
 */
public record OriginState(String origin, Set<String> kitsGiven, int bossKills) {
	public static final Codec<OriginState> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.STRING.fieldOf("origin").forGetter(OriginState::origin),
			Codec.STRING.listOf().optionalFieldOf("kits_given", List.of()).forGetter(s -> List.copyOf(s.kitsGiven())),
			Codec.INT.optionalFieldOf("boss_kills", 0).forGetter(OriginState::bossKills))
			.apply(i, (origin, kits, kills) -> new OriginState(origin, Set.copyOf(kits), kills)));

	public OriginState {
		kitsGiven = Set.copyOf(kitsGiven);
	}

	public OriginState withOrigin(String newOrigin) {
		return new OriginState(newOrigin, kitsGiven, bossKills);
	}

	public OriginState withKitGiven(String kitOrigin) {
		Set<String> kits = new java.util.HashSet<>(kitsGiven);
		kits.add(kitOrigin);
		return new OriginState(origin, kits, bossKills);
	}

	public OriginState withBossKills(int kills) {
		return new OriginState(origin, kitsGiven, kills);
	}
}
