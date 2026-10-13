package io.github.tmldude.blocksouls.game.player.classes;

import java.util.List;
import java.util.Set;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * What a player's playerClass attachment stores: the class id, which classes' kits they were handed this run (so a
 * re-pick never farms a kit twice) and boss kills (Bloodbound's hearts).
 */
public record ClassState(String classId, Set<String> kitsGiven, int bossKills) {
	public static final Codec<ClassState> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.STRING.fieldOf("class").forGetter(ClassState::classId),
			Codec.STRING.listOf().optionalFieldOf("kits_given", List.of()).forGetter(s -> List.copyOf(s.kitsGiven())),
			Codec.INT.optionalFieldOf("boss_kills", 0).forGetter(ClassState::bossKills))
			.apply(i, (id, kits, kills) -> new ClassState(id, Set.copyOf(kits), kills)));

	public ClassState {
		kitsGiven = Set.copyOf(kitsGiven);
	}

	public ClassState withClass(String newClass) {
		return new ClassState(newClass, kitsGiven, bossKills);
	}

	public ClassState withKitGiven(String kitOrigin) {
		Set<String> kits = new java.util.HashSet<>(kitsGiven);
		kits.add(kitOrigin);
		return new ClassState(classId, kits, bossKills);
	}

	public ClassState withBossKills(int kills) {
		return new ClassState(classId, kitsGiven, kills);
	}
}
