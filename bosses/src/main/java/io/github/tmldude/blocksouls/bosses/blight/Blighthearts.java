package io.github.tmldude.blocksouls.bosses.blight;

import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;

import io.github.tmldude.blocksouls.bosses.api.Bosses;
import io.github.tmldude.blocksouls.common.CommonIds;
import io.github.tmldude.blocksouls.common.Config;

/**
 * Registers the Blightheart: config, entity type, definition. No field spawn rule: it walks only where floor 5's hazard
 * (game) or the Director's mini-boss pick sends it.
 */
public final class Blighthearts {
	public static final Identifier ID = CommonIds.id(CommonIds.BOSSES, "blightheart");
	public static BlightheartConfig CONFIG = new BlightheartConfig();
	public static EntityType<Blightheart> TYPE;

	private Blighthearts() {
	}

	public static void register() {
		CONFIG = Config.load("boss-blightheart", BlightheartConfig.class, BlightheartConfig::new);
		Blightheart.createDefinition();
		TYPE = Bosses.register(ID, Blightheart::new, new Bosses.Size(1.4f, 2.5f, 2.8f), false,
				() -> Bosses.attributes(CONFIG.health, CONFIG.armor, CONFIG.speed, CONFIG.leap.stats.damage));
	}
}
