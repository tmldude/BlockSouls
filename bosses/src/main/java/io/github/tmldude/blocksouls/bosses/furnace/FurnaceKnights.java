package io.github.tmldude.blocksouls.bosses.furnace;

import net.minecraft.world.entity.EntityType;

import io.github.tmldude.blocksouls.bosses.api.Bosses;
import io.github.tmldude.blocksouls.bosses.field.FieldBosses;
import io.github.tmldude.blocksouls.common.CommonIds;
import io.github.tmldude.blocksouls.common.Config;

/** Registers the Furnace Knight: config, sounds, entity type, definition, field spawn rule. */
public final class FurnaceKnights {
	public static FurnaceKnightConfig CONFIG = new FurnaceKnightConfig();
	public static EntityType<FurnaceKnight> TYPE;

	private FurnaceKnights() {
	}

	public static void register() {
		CONFIG = Config.load("boss-furnace_knight", FurnaceKnightConfig.class, FurnaceKnightConfig::new);
		FurnaceKnightSounds.init();
		FurnaceKnight.createDefinition();
		TYPE = Bosses.register(CommonIds.FURNACE_KNIGHT, FurnaceKnight::new, new Bosses.Size(1.4f, 2.5f, 2.8f), true,
				() -> Bosses.attributes(CONFIG.health, CONFIG.armor, CONFIG.speed, CONFIG.slam.damage));
		FieldBosses.register(CommonIds.FURNACE_KNIGHT, new FurnaceKnightSpawnRule());
	}
}
