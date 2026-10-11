package io.github.tmldude.blocksouls.mobs.registry;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

import io.github.tmldude.blocksouls.common.CommonIds;

/**
 * Our own sound event ids. For now {@code sounds.json} points them at re-pitched vanilla events; real sounds
 * drop in later at the same ids.
 */
public final class MobSounds {
	public static final SoundEvent SHAMBLER_AMBIENT = register("entity.shambler.ambient");
	public static final SoundEvent SHAMBLER_HURT = register("entity.shambler.hurt");
	public static final SoundEvent SHAMBLER_DEATH = register("entity.shambler.death");

	public static final SoundEvent CRAWLER_AMBIENT = register("entity.crawler.ambient");
	public static final SoundEvent CRAWLER_HURT = register("entity.crawler.hurt");
	public static final SoundEvent CRAWLER_DEATH = register("entity.crawler.death");
	public static final SoundEvent CRAWLER_LEAP = register("entity.crawler.leap");

	public static final SoundEvent SCREAMER_AMBIENT = register("entity.screamer.ambient");
	public static final SoundEvent SCREAMER_SCREAM = register("entity.screamer.scream");
	public static final SoundEvent SCREAMER_HURT = register("entity.screamer.hurt");
	public static final SoundEvent SCREAMER_DEATH = register("entity.screamer.death");

	public static final SoundEvent BLOATER_AMBIENT = register("entity.bloater.ambient");
	public static final SoundEvent BLOATER_HISS = register("entity.bloater.hiss");
	public static final SoundEvent BLOATER_BURST = register("entity.bloater.burst");
	public static final SoundEvent BLOATER_HURT = register("entity.bloater.hurt");
	public static final SoundEvent BLOATER_DEATH = register("entity.bloater.death");

	public static final SoundEvent BRUTE_AMBIENT = register("entity.brute.ambient");
	public static final SoundEvent BRUTE_SLAM = register("entity.brute.slam");
	public static final SoundEvent BRUTE_WINDUP = register("entity.brute.windup");
	public static final SoundEvent BRUTE_HURT = register("entity.brute.hurt");
	public static final SoundEvent BRUTE_DEATH = register("entity.brute.death");

	public static final SoundEvent MUTANT_BRUTE_AMBIENT = register("entity.mutant_brute.ambient");
	public static final SoundEvent MUTANT_BRUTE_ROAR = register("entity.mutant_brute.roar");
	public static final SoundEvent MUTANT_BRUTE_THROW = register("entity.mutant_brute.throw");
	public static final SoundEvent MUTANT_BRUTE_HURT = register("entity.mutant_brute.hurt");
	public static final SoundEvent MUTANT_BRUTE_DEATH = register("entity.mutant_brute.death");

	public static final SoundEvent INFECTED_PIGLIN_AMBIENT = register("entity.infected_piglin.ambient");
	public static final SoundEvent INFECTED_PIGLIN_HURT = register("entity.infected_piglin.hurt");
	public static final SoundEvent INFECTED_PIGLIN_DEATH = register("entity.infected_piglin.death");

	public static final SoundEvent ASH_HOUND_AMBIENT = register("entity.ash_hound.ambient");
	public static final SoundEvent ASH_HOUND_HURT = register("entity.ash_hound.hurt");
	public static final SoundEvent ASH_HOUND_DEATH = register("entity.ash_hound.death");

	/** Far-off howl the player hears as Nether dread builds. */
	public static final SoundEvent DREAD_HOWL = register("ambient.dread.howl");

	public static final SoundEvent BLOCK_BREAK_HIT = register("entity.wave_mob.break_hit");

	private MobSounds() {
	}

	private static SoundEvent register(String path) {
		Identifier id = CommonIds.id(CommonIds.MOBS, path);
		return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
	}

	/** Forces class loading during init. */
	public static void init() {
	}
}
