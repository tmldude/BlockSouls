package io.github.tmldude.blocksouls.bosses.ashwyrm;

import net.minecraft.sounds.SoundEvent;

import io.github.tmldude.blocksouls.bosses.registry.BossSounds;

public final class AshwyrmSounds {
	public static final SoundEvent MUSIC = BossSounds.register("music.ashwyrm");
	public static final SoundEvent AMBIENT = BossSounds.register("entity.ashwyrm.ambient");
	public static final SoundEvent HURT = BossSounds.register("entity.ashwyrm.hurt");
	public static final SoundEvent DEATH = BossSounds.register("entity.ashwyrm.death");
	public static final SoundEvent STEP = BossSounds.register("entity.ashwyrm.step");
	public static final SoundEvent ROAR = BossSounds.register("entity.ashwyrm.roar");
	public static final SoundEvent BITE = BossSounds.register("entity.ashwyrm.bite");
	public static final SoundEvent TAIL = BossSounds.register("entity.ashwyrm.tail");
	public static final SoundEvent BREATH = BossSounds.register("entity.ashwyrm.breath");
	public static final SoundEvent FLAP = BossSounds.register("entity.ashwyrm.flap");
	public static final SoundEvent FIREBALL = BossSounds.register("entity.ashwyrm.fireball");
	public static final SoundEvent IMPACT = BossSounds.register("entity.ashwyrm.impact");
	public static final SoundEvent LAND = BossSounds.register("entity.ashwyrm.land");
	public static final SoundEvent METEOR = BossSounds.register("entity.ashwyrm.meteor");

	private AshwyrmSounds() {
	}

	static void init() {
	}
}
