package io.github.tmldude.blocksouls.bosses.stormplate;

import net.minecraft.sounds.SoundEvent;

import io.github.tmldude.blocksouls.bosses.registry.BossSounds;

public final class StormplateSounds {
	public static final SoundEvent MUSIC = BossSounds.register("music.stormplate_knight");
	public static final SoundEvent AMBIENT = BossSounds.register("entity.stormplate_knight.ambient");
	public static final SoundEvent HURT = BossSounds.register("entity.stormplate_knight.hurt");
	public static final SoundEvent DEATH = BossSounds.register("entity.stormplate_knight.death");
	public static final SoundEvent STEP = BossSounds.register("entity.stormplate_knight.step");
	public static final SoundEvent ROAR = BossSounds.register("entity.stormplate_knight.roar");
	public static final SoundEvent WINDUP = BossSounds.register("entity.stormplate_knight.windup");
	public static final SoundEvent THRUST = BossSounds.register("entity.stormplate_knight.thrust");
	public static final SoundEvent SWING = BossSounds.register("entity.stormplate_knight.swing");
	public static final SoundEvent CHARGE = BossSounds.register("entity.stormplate_knight.charge");
	public static final SoundEvent THUNDER = BossSounds.register("entity.stormplate_knight.thunder");
	public static final SoundEvent THROW = BossSounds.register("entity.stormplate_knight.throw");
	public static final SoundEvent SLAM = BossSounds.register("entity.stormplate_knight.slam");
	public static final SoundEvent PUDDLE = BossSounds.register("entity.stormplate_knight.puddle");

	private StormplateSounds() {
	}

	static void init() {
	}
}
