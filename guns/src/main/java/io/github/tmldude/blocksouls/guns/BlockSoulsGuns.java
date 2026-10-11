package io.github.tmldude.blocksouls.guns;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;

import io.github.tmldude.blocksouls.common.Config;
import io.github.tmldude.blocksouls.guns.command.GunCommands;
import io.github.tmldude.blocksouls.guns.item.GunsItems;
import io.github.tmldude.blocksouls.guns.loot.GunLoot;
import io.github.tmldude.blocksouls.guns.shoot.GunServer;
import io.github.tmldude.blocksouls.guns.sound.GunSounds;
import io.github.tmldude.blocksouls.guns.weapon.Weapons;

public class BlockSoulsGuns implements ModInitializer {
	public static final String MOD_ID = "blocksouls_guns";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public static GunsConfig CONFIG = new GunsConfig();

	@Override
	public void onInitialize() {
		CONFIG = Config.load("guns", GunsConfig.class, GunsConfig::new);
		applyWeaponStats();
		// An integrated server must not keep stats synced from a server the client visited earlier.
		ServerLifecycleEvents.SERVER_STARTING.register(server -> applyWeaponStats());

		GunSounds.init();
		GunsItems.register();
		GunLoot.register();
		GunServer.register();
		GunCommands.register();
	}

	private static void applyWeaponStats() {
		List<String> rejected = Weapons.apply(CONFIG.weapons);
		if (!rejected.isEmpty()) {
			LOGGER.warn("Ignoring invalid or unknown weapon entries in guns.json: {} (using defaults)", rejected);
		}
	}
}
