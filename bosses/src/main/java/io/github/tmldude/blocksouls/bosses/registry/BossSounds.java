package io.github.tmldude.blocksouls.bosses.registry;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

import io.github.tmldude.blocksouls.common.CommonIds;

/**
 * Sound event ids ({@code blocksouls_bosses:<path>}). {@code sounds.json} points them at original synthesized sounds
 * ({@code tools/assets/boss_sounds.py}) and music ({@code tools/assets/music.py}). Bosses register their own with
 * {@link #register}.
 */
public final class BossSounds {
	public static final SoundEvent FOG_WALL_CLOSE = register("block.fog_wall.close");
	public static final SoundEvent FOG_WALL_OPEN = register("block.fog_wall.open");
	/** The player's own cue when lock-on catches a target (client side, UI). */
	public static final SoundEvent LOCK_ON = register("ui.lock_on");
	/** Full poise: the boss buckles and the visceral window opens. */
	public static final SoundEvent POSTURE_BREAK = register("entity.boss.posture_break");
	/** A breakable part (pauldron, tail) shatters. */
	public static final SoundEvent PART_BREAK = register("entity.boss.part_break");
	/** The name-splash sting as the fog wall rises (each player hears it once per fight). */
	public static final SoundEvent STING = register("ui.boss_sting");

	private BossSounds() {
	}

	public static SoundEvent register(String path) {
		Identifier id = CommonIds.id(CommonIds.BOSSES, path);
		return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
	}

	public static void init() {
	}
}
