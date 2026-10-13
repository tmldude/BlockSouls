package io.github.tmldude.blocksouls.bosses.riftwarden;

import net.minecraft.world.entity.EntityType;

import io.github.tmldude.blocksouls.bosses.api.Bosses;
import io.github.tmldude.blocksouls.common.CommonIds;
import io.github.tmldude.blocksouls.common.Config;

/** Registers the Riftwarden: config, entity type, definition, the arena's trigger plate and its debug command. */
public final class Riftwardens {
	public static RiftwardenConfig CONFIG = new RiftwardenConfig();
	public static EntityType<Riftwarden> TYPE;

	private Riftwardens() {
	}

	public static void register() {
		CONFIG = Config.load("boss-riftwarden", RiftwardenConfig.class, RiftwardenConfig::new);
		Riftwarden.createDefinition();
		TYPE = Bosses.register(CommonIds.RIFTWARDEN, Riftwarden::new, new Bosses.Size(1.6f, 4.2f, 3.8f), true,
				() -> Bosses.attributes(CONFIG.health, CONFIG.armor, CONFIG.speed, CONFIG.leap.stats.damage));
		RiftArena.init();
		RiftwardenCommands.init();
	}
}
