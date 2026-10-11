package io.github.tmldude.blocksouls.bosses.ashwyrm;

import net.minecraft.world.entity.EntityType;

import io.github.tmldude.blocksouls.bosses.api.Bosses;
import io.github.tmldude.blocksouls.bosses.field.FieldBosses;
import io.github.tmldude.blocksouls.common.CommonIds;
import io.github.tmldude.blocksouls.common.Config;

/** Registers the Ashwyrm: config, sounds, items, entity type, definition, field spawn rule. */
public final class Ashwyrms {
	public static AshwyrmConfig CONFIG = new AshwyrmConfig();
	public static EntityType<Ashwyrm> TYPE;

	private Ashwyrms() {
	}

	public static void register() {
		CONFIG = Config.load("boss-ashwyrm", AshwyrmConfig.class, AshwyrmConfig::new);
		AshwyrmSounds.init();
		AshwyrmItems.register();
		Ashwyrm.createDefinition();
		TYPE = Bosses.register(CommonIds.ASHWYRM, Ashwyrm::new, new Bosses.Size(3.5f, 3.0f, 2.6f), true,
				() -> Bosses.attributes(CONFIG.health, CONFIG.armor, CONFIG.speed, CONFIG.bite.damage));
		FieldBosses.register(CommonIds.ASHWYRM, new AshwyrmSpawnRule());
	}
}
