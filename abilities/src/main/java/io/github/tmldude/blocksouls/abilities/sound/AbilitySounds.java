package io.github.tmldude.blocksouls.abilities.sound;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

import io.github.tmldude.blocksouls.abilities.BlockSoulsAbilities;

/** Our sound events; {@code sounds.json} and the files come from {@code tools/assets/ability_sounds.py}. */
public final class AbilitySounds {
	public static final SoundEvent CAST_TACTICAL = register("ability.cast.tactical");
	public static final SoundEvent CAST_SIGNATURE = register("ability.cast.signature");
	public static final SoundEvent CAST_ULTIMATE = register("ability.cast.ultimate");
	/** Pressed while on cooldown (client-side UI sound). */
	public static final SoundEvent DENIED = register("ability.denied");
	/** The ultimate bar just filled (client-side UI sound). */
	public static final SoundEvent READY = register("ability.ready");
	public static final SoundEvent THROW = register("ability.throw");
	public static final SoundEvent FRAG = register("ability.frag");
	public static final SoundEvent WEB = register("ability.web");
	public static final SoundEvent FIRE = register("ability.fire");
	public static final SoundEvent HEAL = register("ability.heal");
	public static final SoundEvent SLAM = register("ability.slam");
	public static final SoundEvent GEYSER = register("ability.geyser");
	public static final SoundEvent FAN = register("ability.fan");
	public static final SoundEvent ZAP = register("ability.zap");
	public static final SoundEvent DRAIN = register("ability.drain");
	public static final SoundEvent STORM = register("ability.storm");
	public static final SoundEvent FRENZY = register("ability.frenzy");
	public static final SoundEvent SHIELD = register("ability.shield");

	private AbilitySounds() {
	}

	private static SoundEvent register(String path) {
		Identifier id = Identifier.fromNamespaceAndPath(BlockSoulsAbilities.MOD_ID, path);
		return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
	}

	public static void init() {
	}
}
