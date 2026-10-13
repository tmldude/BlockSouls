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
	// --- floor 6 (Stage 6C, v2-floor6-arena) ---
	/** Entity type (bosses): floor 6's final boss. Its death ends a floors run (game listens on BossEvents.KILLED). */
	public static final Identifier RIFTWARDEN = id(BOSSES, "riftwarden");
	/** Floor template pool (world): floor 6, the final arena. */
	public static final Identifier ARENA_POOL = id(WORLD, "arena");
	/** Floor tag: the floor has no day timer, no siege and no night (the final arena). Game's day loop skips it. */
	public static final String TIMELESS_TAG = "timeless";
	/**
	 * Structure slot tag on the final arena floor: the slot's position is the arena's centre (its trigger plate) and
	 * its radius the fighting ring's. World builds it; bosses spawns the final boss when the party steps on the plate.
	 */
	public static final String FINAL_ARENA_SLOT = "final_arena";
	// --- end floor 6 ---

	/** Wave mob entity types (mobs). Their loot tables can be extended by other modules. */
	public static final Identifier SHAMBLER = id(MOBS, "shambler");
	public static final Identifier CRAWLER = id(MOBS, "crawler");
	public static final Identifier SCREAMER = id(MOBS, "screamer");
	public static final Identifier BLOATER = id(MOBS, "bloater");
	public static final Identifier BRUTE = id(MOBS, "brute");
	public static final Identifier MUTANT_BRUTE = id(MOBS, "mutant_brute");
	public static final Identifier INFECTED_PIGLIN = id(MOBS, "infected_piglin");
	public static final Identifier ASH_HOUND = id(MOBS, "ash_hound");
	/** v2 Expedition roster (mobs): a ranged spitter and a shielded mob that must be flanked. */
	public static final Identifier SPITTER = id(MOBS, "spitter");
	public static final Identifier BULWARK = id(MOBS, "bulwark");

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
	// v2-floor5-blight: the Blighted Surface's rare node, its crystal, and the ghostcap herb (block and item share the id).
	public static final Identifier BLIGHT_CRYSTAL_NODE = id(WORLD, "blight_crystal_node");
	public static final Identifier BLIGHT_CRYSTAL = id(WORLD, "blight_crystal");
	public static final Identifier GHOSTCAP = id(WORLD, "ghostcap");
	/** The camp's exit portal block (world); using it starts the act's portal charge, lit it leads on. */
	public static final Identifier FLOOR_PORTAL = id(WORLD, "floor_portal");
	// --- floor 4, The Vault (Stage 6C) ---
	/** The Vault's rare node block (world), set into the corridor walls; pops into deep iron. */
	public static final Identifier DEEP_IRON_NODE = id(WORLD, "deep_iron_node");
	/** The Vault's rare metal (world), from deep iron nodes: gun receivers, the antidote. */
	public static final Identifier DEEP_IRON = id(WORLD, "deep_iron");
	/** The Vault's herb block (world): glowing moss in the rooms, pops in one hit into cave moss. */
	public static final Identifier CAVE_MOSS_PATCH = id(WORLD, "cave_moss_patch");
	/** The Vault's herb (world): brewed into the night-eye tonic and the moss antidote. */
	public static final Identifier CAVE_MOSS = id(WORLD, "cave_moss");
	/** A key to the Vault's locked vault room (world): three open it. Found in chests in other rooms. */
	public static final Identifier VAULT_KEY = id(WORLD, "vault_key");
	/** The Vault's mini-bosses (world entity types, in the Director's {@code vault} mini-boss roster). */
	public static final Identifier VAULT_LISTENER = id(WORLD, "vault_listener");
	public static final Identifier VAULT_COLOSSUS = id(WORLD, "vault_colossus");
	// --- end floor 4 ---

	/** Ammo items (guns). The Director reads a party's ammo from them; the shopkeeper sells them. */
	public static final Identifier PRIMARY_AMMO = id(GUNS, "primary_ammo");
	public static final Identifier SPECIAL_AMMO = id(GUNS, "special_ammo");

	/** Economy objects (world): blocks whose use the game handles via {@code EconomyEvents.CAMP_OBJECT_USED}. */
	public static final Identifier BLOOD_ALTAR = id(WORLD, "blood_altar");
	public static final Identifier CURSED_CHEST = id(WORLD, "cursed_chest");
	public static final Identifier CHALLENGE_PYLON = id(WORLD, "challenge_pylon");
	public static final Identifier CAMP_HORN = id(WORLD, "camp_horn");
	/** Entity type (world): the camp shopkeeper. */
	public static final Identifier SHOPKEEPER = id(WORLD, "shopkeeper");
	/**
	 * Loot tables (game) the economy objects roll: the cursed chest's haul and the blood altar's legendary
	 * roll. Guns etc. add entries with {@code LootTableEvents.MODIFY}.
	 */
	public static final ResourceKey<LootTable> CURSED_CHEST_LOOT = lootTable(GAME, "economy/cursed_chest");
	public static final ResourceKey<LootTable> BLOOD_ALTAR_LOOT = lootTable(GAME, "economy/blood_altar");

	/**
	 * Loot tables (owned by game) rolled for wave kills, by tier. Guns, world etc. add their entries with
	 * {@code LootTableEvents.MODIFY}; the game picks the tier from the wave number.
	 */
	public static final ResourceKey<LootTable> WAVE_DROPS_COMMON = lootTable(GAME, "wave_drops/common");
	public static final ResourceKey<LootTable> WAVE_DROPS_RARE = lootTable(GAME, "wave_drops/rare");
	public static final ResourceKey<LootTable> WAVE_DROPS_LEGENDARY = lootTable(GAME, "wave_drops/legendary");
	/** Reward chest for clearing a wave. */
	public static final ResourceKey<LootTable> WAVE_CLEAR_REWARD = lootTable(GAME, "wave_clear_reward");
	/** Rolled once more (on top of the tier table) for every elite a player kills. Guns adds a gun and ammo. */
	public static final ResourceKey<LootTable> WAVE_DROPS_ELITE = lootTable(GAME, "wave_drops/elite");
	/**
	 * The night-2 pity gun: rolled when a party still has nothing but Starter Pistols. Empty in game; guns fills it with
	 * one guaranteed rolled gun.
	 */
	public static final ResourceKey<LootTable> GUN_PITY = lootTable(GAME, "gun_pity");

	/**
	 * Data folders (under {@code data/<namespace>/}) the shopkeeper's forge and brew boards read on every
	 * {@code /reload}; any module may ship files there. Formats: docs/decisions/v2-boards.md.
	 * {@code entry}: a recipe's board, floors and floor tags. {@code source}: "where to get it" for an ingredient.
	 * {@code enchant}: a floor's forge enchant service (metal, souls, enchantments). {@code upgrade}: a gear tier chain.
	 */
	public static final String BOARD_ENTRIES = "blocksouls_board/entry";
	public static final String BOARD_SOURCES = "blocksouls_board/source";
	public static final String BOARD_ENCHANTS = "blocksouls_board/enchant";
	public static final String BOARD_UPGRADES = "blocksouls_board/upgrade";

	private CommonIds() {
	}

	public static Identifier id(String namespace, String path) {
		return Identifier.fromNamespaceAndPath(namespace, path);
	}

	public static ResourceKey<LootTable> lootTable(String namespace, String path) {
		return ResourceKey.create(Registries.LOOT_TABLE, id(namespace, path));
	}
}
