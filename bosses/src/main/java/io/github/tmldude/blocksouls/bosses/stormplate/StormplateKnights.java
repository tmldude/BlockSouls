package io.github.tmldude.blocksouls.bosses.stormplate;

import net.minecraft.world.entity.EntityType;

import io.github.tmldude.blocksouls.bosses.api.Bosses;
import io.github.tmldude.blocksouls.bosses.field.FieldBosses;
import io.github.tmldude.blocksouls.common.CommonIds;
import io.github.tmldude.blocksouls.common.Config;

/** Registers the Stormplate Knight: config, sounds, the dragonscale set, entity type, definition, field spawn rule. */
public final class StormplateKnights {
	public static StormplateConfig CONFIG = new StormplateConfig();
	public static EntityType<StormplateKnight> TYPE;

	private StormplateKnights() {
	}

	public static void register() {
		CONFIG = Config.load("boss-stormplate_knight", StormplateConfig.class, StormplateConfig::new);
		StormplateSounds.init();
		DragonscaleArmor.register();
		StormplateKnight.createDefinition();
		TYPE = Bosses.register(CommonIds.STORMPLATE_KNIGHT, StormplateKnight::new, new Bosses.Size(1.6f, 4.0f, 3.5f), false,
				() -> Bosses.attributes(CONFIG.health, CONFIG.armor, CONFIG.speed, CONFIG.thrust.damage));
		FieldBosses.register(CommonIds.STORMPLATE_KNIGHT, new StormplateSpawnRule());
	}
}
