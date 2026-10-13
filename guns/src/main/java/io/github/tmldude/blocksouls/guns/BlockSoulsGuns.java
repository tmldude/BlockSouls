package io.github.tmldude.blocksouls.guns;

import java.util.List;

import org.jspecify.annotations.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;

import io.github.tmldude.blocksouls.common.Config;
import io.github.tmldude.blocksouls.common.synergy.SynergyTags;
import io.github.tmldude.blocksouls.guns.item.GunSynergy;
import io.github.tmldude.blocksouls.common.event.ConfigEvents;
import io.github.tmldude.blocksouls.guns.command.GunCommands;
import io.github.tmldude.blocksouls.guns.item.GunsItems;
import io.github.tmldude.blocksouls.guns.loot.GunLoot;
import io.github.tmldude.blocksouls.guns.loot.RarityBeam;
import io.github.tmldude.blocksouls.guns.shoot.GunServer;
import io.github.tmldude.blocksouls.guns.sound.GunSounds;
import io.github.tmldude.blocksouls.guns.weapon.Weapons;

public class BlockSoulsGuns implements ModInitializer {
	public static final String MOD_ID = "blocksouls_guns";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public static GunsConfig CONFIG = new GunsConfig();
	private static @Nullable MinecraftServer server;

	@Override
	public void onInitialize() {
		CONFIG = Config.load("guns", GunsConfig.class, GunsConfig::new);
		if (GunsConfig.migrate(CONFIG)) {
			Config.save("guns", CONFIG);
		}
		applyWeaponStats();
		// An integrated server must not keep stats synced from a server the client visited earlier.
		ServerLifecycleEvents.SERVER_STARTING.register(starting -> applyWeaponStats());
		ServerLifecycleEvents.SERVER_STARTED.register(started -> server = started);
		ServerLifecycleEvents.SERVER_STOPPED.register(stopped -> server = null);
		// /bs config reload guns: weapon stats are a rebuilt table, and clients time shots with the synced copy.
		ConfigEvents.RELOADED.register((name, config) -> {
			if (name.equals("guns")) {
				// A reloaded file from before a defaults revision gets the same migration as at startup.
				if (GunsConfig.migrate(CONFIG)) {
					Config.save("guns", CONFIG);
				}
				applyWeaponStats();
				if (server != null) {
					for (ServerPlayer player : server.getPlayerList().getPlayers()) {
						GunServer.sendStats(player);
					}
				}
			}
		});

		GunSounds.init();
		GunsItems.register();
		GunLoot.register();
		RarityBeam.register();
		GunServer.register();
		GunCommands.register();
		SynergyTags.addSource(GunSynergy::collect);
	}

	private static void applyWeaponStats() {
		List<String> rejected = Weapons.apply(CONFIG.weapons);
		if (!rejected.isEmpty()) {
			LOGGER.warn("Ignoring invalid or unknown weapon entries in guns.json: {} (using defaults)", rejected);
		}
	}
}
