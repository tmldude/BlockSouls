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
	public static final String WORLD = "blocksouls_world";

	/** Item (guns): handed out by the game at run start, broken by the game after wave 3. */
	public static final Identifier STARTER_PISTOL = id(GUNS, "starter_pistol");

	/**
	 * Item (game): the Soul, the run's currency. Skipping a relic pays souls, bosses drop them, the shopkeeper
	 * takes them. Recipes may use it (soul-infused crafting); it must stay an ingredient in 2+ recipes.
	 */
	public static final Identifier SOUL = id(GAME, "soul");
	/**
	 * Item (game): a Trinket Case; using it shows a pick-1-of-3 trinket offer. Field bosses hand one to every
	 * participant; loot tables (challenge pylons, rare cursed chests) may drop it directly.
	 */
	public static final Identifier TRINKET_CASE = id(GAME, "trinket_case");

	/** Boss id (bosses) whose {@code BossEvents.BOSS_KILLED} switches the run to endless mode. */
	public static final Identifier FINAL_BOSS = id(BOSSES, "final_dragon");
	public static final Identifier FURNACE_KNIGHT = id(BOSSES, "furnace_knight");
	public static final Identifier PALE_RIDER = id(BOSSES, "pale_rider");
	public static final Identifier ASHWYRM = id(BOSSES, "ashwyrm");
	public static final Identifier STORMPLATE_KNIGHT = id(BOSSES, "stormplate_knight");

	/** Wave mob entity types (mobs). Their loot tables can be extended by other modules. */
	public static final Identifier SHAMBLER = id(MOBS, "shambler");
	public static final Identifier CRAWLER = id(MOBS, "crawler");
	public static final Identifier SCREAMER = id(MOBS, "screamer");
	public static final Identifier BLOATER = id(MOBS, "bloater");
	public static final Identifier BRUTE = id(MOBS, "brute");
	public static final Identifier MUTANT_BRUTE = id(MOBS, "mutant_brute");
	public static final Identifier INFECTED_PIGLIN = id(MOBS, "infected_piglin");
	public static final Identifier ASH_HOUND = id(MOBS, "ash_hound");

	/** Wacky enchantments (world). Guns read these to apply them to bullets. */
	public static final Identifier BOUNCY = id(WORLD, "bouncy");
	public static final Identifier COMBUSTION = id(WORLD, "combustion");
	public static final Identifier VAMPIRIC = id(WORLD, "vampiric");
	public static final Identifier GRAPPLE = id(WORLD, "grapple");
	public static final Identifier MAGNETIZED = id(WORLD, "magnetized");
	public static final Identifier MOONWALK = id(WORLD, "moonwalk");

	/** Crafting materials (world). Other modules' recipes and loot tables use them. */
	public static final Identifier SCRAP = id(WORLD, "scrap");
	public static final Identifier GUNMETAL_INGOT = id(WORLD, "gunmetal_ingot");
	/** Nether material: mined from blight hearts, dropped by the Furnace Knight; blight-forged armor needs it. */
	public static final Identifier BLIGHT_CORE = id(WORLD, "blight_core");
	/** Material (bosses), dropped by the Ashwyrm. High-tier recipes may use it. */
	public static final Identifier ASHWYRM_SCALE = id(BOSSES, "ashwyrm_scale");
	/** Gun part (bosses), dropped by the Ashwyrm. Guns' fire-themed recipes may use it. */
	public static final Identifier EMBER_BARREL = id(BOSSES, "ember_barrel");

	/**
	 * Loot function (guns) that rolls a gun's rarity and perks, e.g.
	 * {@code {"type": "blocksouls_guns:roll_perks", "tier": "legendary"}} (26.3 loot JSON keys functions by
	 * {@code "type"} and takes them as an entry's {@code "modifier"}). Any module's loot table may use it.
	 */
	public static final Identifier ROLL_PERKS = id(GUNS, "roll_perks");

	/** Entity type (vehicles) placed by world structures as a repairable wreck. */
	public static final Identifier KART_WRECK = id(VEHICLES, "kart_wreck");

	/** The v2 floor dimension (world): every floor of a floors-shape run is an island in it. */
	public static final Identifier FLOORS_DIMENSION = id(WORLD, "floors");
	/** Resource node blocks (world): pop in a few hits with a burst. Their ids are the {@code NODE} slot tags. */
	public static final Identifier SCRAP_NODE = id(WORLD, "scrap_node");
	public static final Identifier GUNMETAL_NODE = id(WORLD, "gunmetal_node");
	public static final Identifier RESIN_NODE = id(WORLD, "resin_node");
	/** The Surface floor's rare material (world), from resin nodes. */
	public static final Identifier AMBER_RESIN = id(WORLD, "amber_resin");
	/** The camp's exit portal block (world); using it starts the act's portal charge, lit it leads on. */
	public static final Identifier FLOOR_PORTAL = id(WORLD, "floor_portal");

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
