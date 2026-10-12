package io.github.tmldude.blocksouls.integration;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.SmithingRecipeInput;
import net.minecraft.world.item.crafting.SmithingTransformRecipe;
import net.minecraft.world.phys.Vec3;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.loader.api.FabricLoader;

import io.github.tmldude.blocksouls.common.CommonIds;
import io.github.tmldude.blocksouls.common.CommonTags;
import io.github.tmldude.blocksouls.common.mob.WaveContext;
import io.github.tmldude.blocksouls.common.mob.WaveMobs;
import io.github.tmldude.blocksouls.common.party.PartyScaling;
import io.github.tmldude.blocksouls.common.player.PlayerStatus;
import io.github.tmldude.blocksouls.common.run.Difficulty;
import io.github.tmldude.blocksouls.game.player.downed.Downed;
import io.github.tmldude.blocksouls.game.run.RunConfig;
import io.github.tmldude.blocksouls.game.run.RunDebug;
import io.github.tmldude.blocksouls.game.run.RunSystem;
import io.github.tmldude.blocksouls.game.run.StarterKit;
import io.github.tmldude.blocksouls.game.run.WaveMath;
import io.github.tmldude.blocksouls.guns.item.GunRolls;
import io.github.tmldude.blocksouls.guns.logic.GunRarity;
import io.github.tmldude.blocksouls.guns.logic.Perk;

/**
 * The whole pack in one server: every mod loads together, ids one module references in another resolve, and the
 * cross-module loops (waves spawning the mobs module's roster, downed players ignored by those mobs) work.
 */
public class FullPackGameTests {
	private static final List<String> MODS = List.of("blocksouls_common", "blocksouls_movement", "blocksouls_guns",
			"blocksouls_game", "blocksouls_mobs", "blocksouls_world", "blocksouls_vehicles", "blocksouls_bosses");

	private static final List<Identifier> MOBS = List.of(CommonIds.SHAMBLER, CommonIds.CRAWLER, CommonIds.SCREAMER,
			CommonIds.BLOATER, CommonIds.BRUTE, CommonIds.MUTANT_BRUTE, CommonIds.INFECTED_PIGLIN, CommonIds.ASH_HOUND);

	private static final List<Identifier> WAVE_MOBS = MOBS.subList(0, 6);

	private static final List<Identifier> ENCHANTMENTS = List.of(CommonIds.BOUNCY, CommonIds.COMBUSTION,
			CommonIds.VAMPIRIC, CommonIds.GRAPPLE, CommonIds.MAGNETIZED, CommonIds.MOONWALK);

	@GameTest
	public void everyModLoads(GameTestHelper helper) {
		for (String mod : MODS) {
			helper.assertTrue(FabricLoader.getInstance().isModLoaded(mod), mod + " is loaded");
		}
		helper.succeed();
	}

	@GameTest
	public void crossModuleIdsResolve(GameTestHelper helper) {
		for (Identifier item : List.of(CommonIds.STARTER_PISTOL, CommonIds.SCRAP, CommonIds.GUNMETAL_INGOT, CommonIds.BLIGHT_CORE,
				CommonIds.id(CommonIds.VEHICLES, "wheel"), CommonIds.id(CommonIds.VEHICLES, "engine"), CommonIds.SOUL, CommonIds.TRINKET_CASE)) {
			helper.assertTrue(BuiltInRegistries.ITEM.containsKey(item), "item " + item);
		}
		helper.assertTrue(BuiltInRegistries.ENTITY_TYPE.containsKey(CommonIds.KART_WRECK), "kart wreck entity");
		for (Identifier mob : MOBS) {
			helper.assertTrue(BuiltInRegistries.ENTITY_TYPE.containsKey(mob), "mob " + mob);
		}
		for (Identifier mob : WAVE_MOBS) {
			helper.assertTrue(WaveMobs.get(mob).isPresent(), "wave mob registered " + mob);
		}
		var enchantments = helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
		for (Identifier enchantment : ENCHANTMENTS) {
			helper.assertTrue(enchantments.get(ResourceKey.create(Registries.ENCHANTMENT, enchantment)).isPresent(),
					"enchantment " + enchantment);
		}
		// Recipes that use another module's items are guarded by load conditions; with the full pack they must load.
		var recipes = helper.getLevel().getServer().getRecipeManager();
		for (Identifier recipe : List.of(CommonIds.id(CommonIds.GUNS, "hand_cannon"), CommonIds.id(CommonIds.GUNS, "sniper_rifle"),
				CommonIds.id(CommonIds.GUNS, "special_ammo"), CommonIds.id(CommonIds.VEHICLES, "engine"), CommonIds.id(CommonIds.GAME, "reroll_charm"))) {
			helper.assertTrue(recipes.byKey(ResourceKey.create(Registries.RECIPE, recipe)).isPresent(), "recipe " + recipe);
		}
		helper.succeed();
	}

	/** Crafting-web recipes that mix modules (boss materials, gunmetal nuggets) load with the full pack. */
	private static final List<Identifier> CRAFTING_WEB_RECIPES = List.of(CommonIds.id(CommonIds.GUNS, "ember_hand_cannon"),
			CommonIds.id(CommonIds.GUNS, "ember_shotgun"), CommonIds.id(CommonIds.GUNS, "special_ammo_from_nuggets"),
			CommonIds.id(CommonIds.GUNS, "special_ammo_from_ashwyrm_scale"),
			CommonIds.id(CommonIds.WORLD, "blight_forged_helmet_ashwyrm_scaled"),
			CommonIds.id(CommonIds.WORLD, "blight_forged_chestplate_ashwyrm_scaled"),
			CommonIds.id(CommonIds.WORLD, "blight_forged_leggings_ashwyrm_scaled"),
			CommonIds.id(CommonIds.WORLD, "blight_forged_boots_ashwyrm_scaled"));

	@GameTest
	public void craftingWebRecipesLoad(GameTestHelper helper) {
		var recipes = helper.getLevel().getServer().getRecipeManager();
		for (Identifier recipe : CRAFTING_WEB_RECIPES) {
			helper.assertTrue(recipes.byKey(ResourceKey.create(Registries.RECIPE, recipe)).isPresent(), "recipe " + recipe);
		}
		helper.succeed();
	}

	@GameTest
	public void emberBarrelCraftsAnExplosiveHandCannon(GameTestHelper helper) {
		Recipe<?> recipe = recipe(helper, CommonIds.id(CommonIds.GUNS, "ember_hand_cannon"));
		helper.assertTrue(recipe instanceof ShapedRecipe, "ember hand cannon is a shaped recipe");
		CraftingInput input = CraftingInput.of(2, 2, List.of(stack(CommonIds.EMBER_BARREL), stack(CommonIds.id(CommonIds.GUNS, "gun_receiver")),
				stack(CommonIds.ASHWYRM_SCALE), stack(CommonIds.GUNMETAL_INGOT)));
		ShapedRecipe shaped = (ShapedRecipe) recipe;
		helper.assertTrue(shaped.matches(input, helper.getLevel()), "the pattern matches");
		ItemStack gun = shaped.assemble(input);
		helper.assertTrue(BuiltInRegistries.ITEM.getKey(gun.getItem()).equals(CommonIds.id(CommonIds.GUNS, "hand_cannon")), "result is a hand cannon");
		helper.assertTrue(GunRolls.isRolled(gun), "comes pre-rolled, so the first inventory tick won't re-roll it");
		helper.assertTrue(GunRolls.rarity(gun) == GunRarity.RARE, "rare");
		helper.assertTrue(GunRolls.perks(gun).equals(List.of(Perk.EXPLOSIVE_ROUNDS)), "carries Explosive Rounds");
		helper.succeed();
	}

	@GameTest
	public void ashwyrmScaleSmithsBurnResistantArmor(GameTestHelper helper) {
		Recipe<?> recipe = recipe(helper, CommonIds.id(CommonIds.WORLD, "blight_forged_chestplate_ashwyrm_scaled"));
		helper.assertTrue(recipe instanceof SmithingTransformRecipe, "a smithing upgrade");
		ItemStack base = stack(CommonIds.id(CommonIds.WORLD, "blight_forged_chestplate"));
		ItemStack result = ((SmithingTransformRecipe) recipe).assemble(new SmithingRecipeInput(stack(CommonIds.ASHWYRM_SCALE), base,
				new ItemStack(Items.MAGMA_CREAM)));
		ItemAttributeModifiers before = base.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
		ItemAttributeModifiers after = result.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
		helper.assertTrue(result.getItem() == base.getItem(), "same armor piece");
		// Armor materials hold floats (0.05F), recipe JSON holds doubles: compare loosely.
		for (ItemAttributeModifiers.Entry entry : before.modifiers()) {
			helper.assertTrue(after.modifiers().stream().anyMatch(e -> e.attribute().equals(entry.attribute())
					&& Math.abs(e.modifier().amount() - entry.modifier().amount()) < 1.0E-4 && e.slot() == entry.slot()), "keeps " + entry.attribute().getRegisteredName());
		}
		helper.assertTrue(after.modifiers().stream().anyMatch(e -> e.attribute().equals(Attributes.BURNING_TIME) && e.modifier().amount() < 0),
				"adds shorter burning");
		helper.succeed();
	}

	private static Recipe<?> recipe(GameTestHelper helper, Identifier id) {
		return helper.getLevel().getServer().getRecipeManager().byKey(ResourceKey.create(Registries.RECIPE, id)).orElseThrow().value();
	}

	private static ItemStack stack(Identifier item) {
		return new ItemStack(BuiltInRegistries.ITEM.getValue(item));
	}

	@GameTest
	public void starterKitHandsOutTheRealGun(GameTestHelper helper) {
		Item pistol = StarterKit.pistol().orElseThrow();
		helper.assertTrue(BuiltInRegistries.ITEM.getKey(pistol).equals(CommonIds.STARTER_PISTOL), "kit uses the guns item");
		helper.assertTrue(new ItemStack(pistol).is(CommonTags.GUNS), "the pistol is in #blocksouls:guns");
		helper.succeed();
	}

	@GameTest(environment = "blocksouls_integration_test:full_pack_wave", maxTicks = 300, skyAccess = true)
	public void wavesSpawnTheMobsModsRoster(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		RunDebug.reset(server);
		RunConfig config = RunSystem.config();
		RunConfig defaults = new RunConfig();
		config.spawnMinDistance = 2;
		config.spawnMaxDistance = 5;
		config.minPlayerDistance = 1;
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.snapTo(helper.absoluteVec(new Vec3(4, 1, 4)));
		RunDebug.skipTo(server, 14000);
		RunDebug.startWave(server, 6);

		helper.succeedWhen(() -> {
			Set<UUID> spawned = RunDebug.waveMobs(server);
			List<Identifier> types = new ArrayList<>();
			for (UUID id : spawned) {
				Entity mob = helper.getLevel().getEntity(id);
				if (mob != null) {
					types.add(BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()));
				}
			}
			helper.assertTrue(types.size() >= 3, "wave mobs spawned: " + types);
			helper.assertTrue(types.stream().allMatch(type -> type.getNamespace().equals(CommonIds.MOBS)),
					"every wave mob comes from the mobs roster, got " + types);
			RunDebug.reset(server);
			config.spawnMinDistance = defaults.spawnMinDistance;
			config.spawnMaxDistance = defaults.spawnMaxDistance;
			config.minPlayerDistance = defaults.minPlayerDistance;
		});
	}

	/**
	 * A full party of 8: the wave is sized by common's party curve and more than the old flat 30 mobs are alive at once.
	 * Other tests' mock players may be online too, but party size caps at 8, so the numbers are exact.
	 */
	@GameTest(environment = "blocksouls_integration_test:full_pack_party", maxTicks = 400, skyAccess = true)
	public void aFullPartyOfEightScalesTheWave(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		RunDebug.reset(server);
		RunConfig config = RunSystem.config();
		RunConfig defaults = new RunConfig();
		config.spawnMinDistance = 2;
		config.spawnMaxDistance = 6;
		config.minPlayerDistance = 1;
		for (int i = 0; i < 8; i++) {
			ServerPlayer player = helper.makeMockServerPlayerInLevel();
			player.snapTo(helper.absoluteVec(new Vec3(2 + (i % 4) * 2, 1, 2 + (i / 4) * 4)));
		}
		RunDebug.skipTo(server, 14000);
		RunDebug.startWave(server, 6);

		int expected = WaveMath.waveCount(6, 8, 1, 1, config);
		helper.assertTrue(expected == WaveMath.waveCount(6, 20, 1, 1, config), "party size caps at 8");
		helper.assertTrue(RunDebug.mobsLeft(server) == expected,
				"wave 6 for 8 players has " + expected + " mobs, got " + RunDebug.mobsLeft(server));
		helper.succeedWhen(() -> {
			int alive = RunDebug.waveMobs(server).size();
			helper.assertTrue(alive > 30 && alive <= PartyScaling.aliveCap(8),
					"8 players allow more than 30 mobs at once (cap " + PartyScaling.aliveCap(8) + "), alive " + alive);
			RunDebug.reset(server);
			config.spawnMinDistance = defaults.spawnMinDistance;
			config.spawnMaxDistance = defaults.spawnMaxDistance;
			config.minPlayerDistance = defaults.minPlayerDistance;
		});
	}

	@GameTest(environment = "blocksouls_integration_test:full_pack_downed", maxTicks = 120)
	public void wavesMobsIgnoreDownedPlayers(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.connection.handleAcceptPlayerLoad(new ServerboundPlayerLoadedPacket());
		player.getAbilities().invulnerable = false;
		player.snapTo(helper.absoluteVec(new Vec3(1.5, 1, 1.5)));
		Downed.forceInRun(player);
		Downed.goDown(player);
		helper.assertTrue(PlayerStatus.isDowned(player), "player is down");

		BlockPos at = helper.absolutePos(new BlockPos(4, 1, 4));
		WaveContext context = new WaveContext(1, Difficulty.NORMAL, Set.of(), false);
		Mob shambler = WaveMobs.get(CommonIds.SHAMBLER).orElseThrow().factory().create(level, at, context);
		helper.assertTrue(shambler != null, "factory made a Shambler");
		level.addFreshEntity(shambler);
		shambler.setTarget(player);

		helper.runAfterDelay(60, () -> {
			helper.assertTrue(shambler.getTarget() != player, "a downed player is never a target");
			helper.assertTrue(player.getHealth() > 0 && PlayerStatus.isDowned(player), "still down, not dead");
			shambler.discard();
			helper.succeed();
		});
	}
}
