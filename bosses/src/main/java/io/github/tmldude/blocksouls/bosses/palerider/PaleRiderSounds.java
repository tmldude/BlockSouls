package io.github.tmldude.blocksouls.bosses.palerider;

import net.minecraft.sounds.SoundEvent;

import io.github.tmldude.blocksouls.bosses.registry.BossSounds;

public final class PaleRiderSounds {
	public static final SoundEvent MUSIC = BossSounds.register("music.pale_rider");
	public static final SoundEvent AMBIENT = BossSounds.register("entity.pale_rider.ambient");
	public static final SoundEvent HURT = BossSounds.register("entity.pale_rider.hurt");
	public static final SoundEvent DEATH = BossSounds.register("entity.pale_rider.death");
	public static final SoundEvent HOOVES = BossSounds.register("entity.pale_rider.hooves");
	public static final SoundEvent SCREAM = BossSounds.register("entity.pale_rider.scream");
	public static final SoundEvent WINDUP = BossSounds.register("entity.pale_rider.windup");
	public static final SoundEvent CHARGE = BossSounds.register("entity.pale_rider.charge");
	public static final SoundEvent SWING = BossSounds.register("entity.pale_rider.swing");
	public static final SoundEvent STOMP = BossSounds.register("entity.pale_rider.stomp");
	public static final SoundEvent CALL = BossSounds.register("entity.pale_rider.call");
	public static final SoundEvent THROW = BossSounds.register("entity.pale_rider.throw");
	public static final SoundEvent BLINK = BossSounds.register("entity.pale_rider.blink");
	public static final SoundEvent STEED_SUMMON = BossSounds.register("item.wraith_steed_whistle.use");
	public static final SoundEvent STEED_FADE = BossSounds.register("entity.wraith_steed.fade");

	private PaleRiderSounds() {
	}

	static void init() {
	}
}
