package io.github.tmldude.blocksouls.guns.sound;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

import io.github.tmldude.blocksouls.guns.BlockSoulsGuns;

/** Our sound events. {@code sounds.json} maps them to re-pitched vanilla sounds until real audio exists. */
public final class GunSounds {
	public static final SoundEvent PISTOL_FIRE = register("gun.starter_pistol.fire");
	public static final SoundEvent HAND_CANNON_FIRE = register("gun.hand_cannon.fire");
	public static final SoundEvent AUTO_RIFLE_FIRE = register("gun.auto_rifle.fire");
	public static final SoundEvent SHOTGUN_FIRE = register("gun.shotgun.fire");
	public static final SoundEvent SNIPER_RIFLE_FIRE = register("gun.sniper_rifle.fire");
	public static final SoundEvent EMPTY = register("gun.empty");
	public static final SoundEvent RELOAD_START = register("gun.reload_start");
	public static final SoundEvent RELOAD_END = register("gun.reload_end");
	public static final SoundEvent RICOCHET = register("gun.ricochet");
	public static final SoundEvent EXPLOSIVE_ROUND = register("gun.explosive_round");

	private GunSounds() {
	}

	public static SoundEvent fire(String weaponId) {
		return switch (weaponId) {
			case "hand_cannon" -> HAND_CANNON_FIRE;
			case "auto_rifle" -> AUTO_RIFLE_FIRE;
			case "shotgun" -> SHOTGUN_FIRE;
			case "sniper_rifle" -> SNIPER_RIFLE_FIRE;
			default -> PISTOL_FIRE;
		};
	}

	private static SoundEvent register(String path) {
		Identifier id = Identifier.fromNamespaceAndPath(BlockSoulsGuns.MOD_ID, path);
		return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
	}

	public static void init() {
	}
}
