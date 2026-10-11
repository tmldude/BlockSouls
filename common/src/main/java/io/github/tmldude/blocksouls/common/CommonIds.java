package io.github.tmldude.blocksouls.common;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * Ids that one module defines and another references. Feature modules never import each other, so a
 * cross-module reference goes through a constant here (look the object up in its registry by this id).
 */
public final class CommonIds {
	public static final String GAME = "blocksouls_game";
	public static final String GUNS = "blocksouls_guns";
	public static final String MOBS = "blocksouls_mobs";
	public static final String BOSSES = "blocksouls_bosses";
	public static final String VEHICLES = "blocksouls_vehicles";

	/** Item (guns): handed out by the game at run start, broken by the game after wave 3. */
	public static final Identifier STARTER_PISTOL = id(GUNS, "starter_pistol");

	/** Boss id (bosses) whose {@code BossEvents.BOSS_KILLED} switches the run to endless mode. */
	public static final Identifier FINAL_BOSS = id(BOSSES, "final_dragon");
	public static final Identifier FURNACE_KNIGHT = id(BOSSES, "furnace_knight");
	public static final Identifier PALE_RIDER = id(BOSSES, "pale_rider");
	public static final Identifier ASHWYRM = id(BOSSES, "ashwyrm");
	public static final Identifier STORMPLATE_KNIGHT = id(BOSSES, "stormplate_knight");

	/** Entity type (vehicles) placed by world structures as a repairable wreck. */
	public static final Identifier KART_WRECK = id(VEHICLES, "kart_wreck");

	/**
	 * Loot tables (owned by game) rolled for wave kills, by tier. Guns, world etc. add their entries with
	 * {@code LootTableEvents.MODIFY}; the game picks the tier from the wave number.
	 */
	public static final ResourceKey<LootTable> WAVE_DROPS_COMMON = lootTable(GAME, "wave_drops/common");
	public static final ResourceKey<LootTable> WAVE_DROPS_RARE = lootTable(GAME, "wave_drops/rare");
	public static final ResourceKey<LootTable> WAVE_DROPS_LEGENDARY = lootTable(GAME, "wave_drops/legendary");
	/** Reward chest for clearing a wave. */
	public static final ResourceKey<LootTable> WAVE_CLEAR_REWARD = lootTable(GAME, "wave_clear_reward");

	private CommonIds() {
	}

	public static Identifier id(String namespace, String path) {
		return Identifier.fromNamespaceAndPath(namespace, path);
	}

	public static ResourceKey<LootTable> lootTable(String namespace, String path) {
		return ResourceKey.create(Registries.LOOT_TABLE, id(namespace, path));
	}
}
