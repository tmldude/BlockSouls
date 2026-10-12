package io.github.tmldude.blocksouls.bosses.raid;

import net.minecraft.sounds.SoundEvent;

import io.github.tmldude.blocksouls.bosses.registry.BossSounds;

/** The final raid's sounds: the Hollow Sovereign's voice, its crystals and plates (original, see {@code boss_sounds.py}). */
public final class FinalRaidSounds {
	public static final SoundEvent MUSIC = BossSounds.register("music.final_dragon");
	public static final SoundEvent AMBIENT = BossSounds.register("entity.final_dragon.ambient");
	public static final SoundEvent HURT = BossSounds.register("entity.final_dragon.hurt");
	public static final SoundEvent DEATH = BossSounds.register("entity.final_dragon.death");
	public static final SoundEvent FLAP = BossSounds.register("entity.final_dragon.flap");
	public static final SoundEvent ROAR = BossSounds.register("entity.final_dragon.roar");
	/** The dive's 1.2 s warning. */
	public static final SoundEvent SCREECH = BossSounds.register("entity.final_dragon.screech");
	public static final SoundEvent BREATH = BossSounds.register("entity.final_dragon.breath");
	public static final SoundEvent GUST = BossSounds.register("entity.final_dragon.gust");
	public static final SoundEvent TAIL = BossSounds.register("entity.final_dragon.tail");
	public static final SoundEvent ORB = BossSounds.register("entity.final_dragon.orb");
	public static final SoundEvent ORB_POP = BossSounds.register("entity.final_dragon.orb_pop");
	public static final SoundEvent METEOR = BossSounds.register("entity.final_dragon.meteor");
	public static final SoundEvent LAND = BossSounds.register("entity.final_dragon.land");
	/** A hit that the shield (or the height) mostly turned away. */
	public static final SoundEvent DEFLECT = BossSounds.register("entity.final_dragon.deflect");
	public static final SoundEvent ENRAGE = BossSounds.register("entity.final_dragon.enrage");
	/** Sovereign's End building: replayed every 2 s, higher as the cast nears completion. */
	public static final SoundEvent CAST = BossSounds.register("entity.final_dragon.cast");
	/** One note of the obelisk's sequence (pitch rises with the position in the order). */
	public static final SoundEvent CRYSTAL_CHIME = BossSounds.register("entity.raid_crystal.chime");
	public static final SoundEvent CRYSTAL_SHATTER = BossSounds.register("entity.raid_crystal.shatter");
	public static final SoundEvent CRYSTAL_WRONG = BossSounds.register("entity.raid_crystal.wrong");
	public static final SoundEvent CRYSTAL_REFORM = BossSounds.register("entity.raid_crystal.reform");
	public static final SoundEvent SHIELD_BREAK = BossSounds.register("entity.final_dragon.shield_break");
	public static final SoundEvent PLATE_HUM = BossSounds.register("block.raid_plate.hum");
	public static final SoundEvent PLATE_HELD = BossSounds.register("block.raid_plate.held");
	public static final SoundEvent CHAINS = BossSounds.register("block.raid_plate.chains");

	private FinalRaidSounds() {
	}

	static void init() {
	}
}
