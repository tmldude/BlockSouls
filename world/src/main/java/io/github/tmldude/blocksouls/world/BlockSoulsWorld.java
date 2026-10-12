package io.github.tmldude.blocksouls.world;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.resources.Identifier;

import net.fabricmc.api.ModInitializer;

import io.github.tmldude.blocksouls.common.Config;
import io.github.tmldude.blocksouls.world.armor.SetBonuses;
import io.github.tmldude.blocksouls.world.command.WorldCommands;
import io.github.tmldude.blocksouls.world.content.WorldBlocks;
import io.github.tmldude.blocksouls.world.content.WorldItems;
import io.github.tmldude.blocksouls.world.enchant.WackyEnchantments;
import io.github.tmldude.blocksouls.world.gen.WorldGen;
import io.github.tmldude.blocksouls.world.loot.WorldLoot;

public class BlockSoulsWorld implements ModInitializer {
	public static final String MOD_ID = "blocksouls_world";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	public static WorldConfig CONFIG = new WorldConfig();

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	@Override
	public void onInitialize() {
		CONFIG = Config.load("world", WorldConfig.class, WorldConfig::new);
		if (WorldConfig.migrate(CONFIG)) {
			Config.save("world", CONFIG);
		}
		WorldBlocks.init();
		WorldItems.init();
		WorldGen.init();
		WorldLoot.init();
		WackyEnchantments.init();
		SetBonuses.init();
		WorldCommands.init();
		LOGGER.info("BlockSouls World loaded");
	}
}
