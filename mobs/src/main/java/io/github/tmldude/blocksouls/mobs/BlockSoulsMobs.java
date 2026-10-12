package io.github.tmldude.blocksouls.mobs;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.api.ModInitializer;

import io.github.tmldude.blocksouls.common.Config;
import io.github.tmldude.blocksouls.mobs.command.MobCommands;
import io.github.tmldude.blocksouls.mobs.dread.Dread;
import io.github.tmldude.blocksouls.mobs.nether.NetherSpawns;
import io.github.tmldude.blocksouls.mobs.registry.MobEntities;
import io.github.tmldude.blocksouls.mobs.registry.MobSounds;
import io.github.tmldude.blocksouls.mobs.registry.MobWaves;

public class BlockSoulsMobs implements ModInitializer {
	public static final String MOD_ID = "blocksouls_mobs";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public static MobsConfig CONFIG = new MobsConfig();

	@Override
	public void onInitialize() {
		CONFIG = Config.load("mobs", MobsConfig.class, MobsConfig::new);
		if (MobsConfig.migrate(CONFIG)) {
			Config.save("mobs", CONFIG);
		}
		MobSounds.init();
		MobEntities.init();
		MobWaves.init();
		NetherSpawns.init();
		Dread.init();
		MobCommands.init();
	}
}
