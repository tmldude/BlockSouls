package io.github.tmldude.blocksouls.bosses.registry;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

import io.github.tmldude.blocksouls.common.CommonIds;

/**
 * Sound event ids ({@code blocksouls_bosses:<path>}). {@code sounds.json} points them at re-pitched vanilla events
 * for now; real sounds drop in at the same ids. Bosses register their own with {@link #register}.
 */
public final class BossSounds {
	public static final SoundEvent FOG_WALL_CLOSE = register("block.fog_wall.close");

	private BossSounds() {
	}

	public static SoundEvent register(String path) {
		Identifier id = CommonIds.id(CommonIds.BOSSES, path);
		return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
	}

	public static void init() {
	}
}
