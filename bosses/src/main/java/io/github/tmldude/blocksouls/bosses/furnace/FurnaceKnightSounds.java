package io.github.tmldude.blocksouls.bosses.furnace;

import net.minecraft.sounds.SoundEvent;

import io.github.tmldude.blocksouls.bosses.registry.BossSounds;

public final class FurnaceKnightSounds {
	public static final SoundEvent MUSIC = BossSounds.register("music.furnace_knight");
	public static final SoundEvent AMBIENT = BossSounds.register("entity.furnace_knight.ambient");
	public static final SoundEvent HURT = BossSounds.register("entity.furnace_knight.hurt");
	public static final SoundEvent DEATH = BossSounds.register("entity.furnace_knight.death");
	public static final SoundEvent STEP = BossSounds.register("entity.furnace_knight.step");
	public static final SoundEvent ROAR = BossSounds.register("entity.furnace_knight.roar");
	public static final SoundEvent WINDUP = BossSounds.register("entity.furnace_knight.windup");
	public static final SoundEvent SLAM = BossSounds.register("entity.furnace_knight.slam");
	public static final SoundEvent SWING = BossSounds.register("entity.furnace_knight.swing");
	public static final SoundEvent BREATH = BossSounds.register("entity.furnace_knight.breath");
	public static final SoundEvent CHARGE = BossSounds.register("entity.furnace_knight.charge");
	public static final SoundEvent PILLAR = BossSounds.register("entity.furnace_knight.pillar");

	private FurnaceKnightSounds() {
	}

	static void init() {
	}
}
