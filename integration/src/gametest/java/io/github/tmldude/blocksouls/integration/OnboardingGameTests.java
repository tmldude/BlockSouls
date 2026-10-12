package io.github.tmldude.blocksouls.integration;

import java.util.List;

import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;

import net.fabricmc.fabric.api.gametest.v1.GameTest;

import io.github.tmldude.blocksouls.common.CommonIds;
import io.github.tmldude.blocksouls.common.advancement.MilestoneTrigger;
import io.github.tmldude.blocksouls.common.advancement.Milestones;

/** Onboarding with the full pack: recipes unlock from their ingredients, and the tutorial advancements grant. */
public class OnboardingGameTests {
	/** Recipes (several modules) whose unlock advancement keys on scrap. */
	private static final List<Identifier> SCRAP_RECIPES = List.of(CommonIds.id(CommonIds.GUNS, "gun_receiver"),
			CommonIds.id(CommonIds.GUNS, "gun_stock"), CommonIds.id(CommonIds.WORLD, "gunmetal_ingot_from_smelting_raw_gunmetal"),
			CommonIds.id(CommonIds.WORLD, "scrap_plate_helmet"), CommonIds.id(CommonIds.VEHICLES, "wheel"),
			CommonIds.id(CommonIds.VEHICLES, "engine"));

	private static ResourceKey<Recipe<?>> recipe(Identifier id) {
		return ResourceKey.create(Registries.RECIPE, id);
	}

	private static void give(ServerPlayer player, Identifier item) {
		player.getInventory().add(new ItemStack(BuiltInRegistries.ITEM.getValue(item)));
		// What the player's own tick does: inventory changes reach the inventory_changed criterion.
		player.inventoryMenu.broadcastChanges();
	}

	private static boolean done(GameTestHelper helper, ServerPlayer player, Identifier advancement) {
		AdvancementHolder holder = helper.getLevel().getServer().getAdvancements().get(advancement);
		helper.assertTrue(holder != null, "advancement " + advancement + " loaded");
		return player.getAdvancements().getOrStartProgress(holder).isDone();
	}

	@GameTest
	public void scrapUnlocksItsRecipes(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		for (Identifier id : SCRAP_RECIPES) {
			helper.assertFalse(player.getRecipeBook().contains(recipe(id)), "a new player doesn't know " + id);
		}
		give(player, CommonIds.SCRAP);
		for (Identifier id : SCRAP_RECIPES) {
			helper.assertTrue(player.getRecipeBook().contains(recipe(id)), "holding scrap unlocks " + id);
		}
		helper.assertFalse(player.getRecipeBook().contains(recipe(CommonIds.id(CommonIds.GUNS, "hand_cannon"))),
				"a gun needs its parts first");
		helper.succeed();
	}

	@GameTest
	public void everyRecipeHasAnUnlockAdvancement(GameTestHelper helper) {
		var server = helper.getLevel().getServer();
		var unlocked = server.getAdvancements().getAllAdvancements().stream()
				.flatMap(holder -> holder.value().rewards().recipes().stream()).toList();
		for (var holder : server.getRecipeManager().getRecipes()) {
			if (holder.id().identifier().getNamespace().startsWith("blocksouls")) {
				helper.assertTrue(unlocked.contains(holder.id()), "recipe " + holder.id().identifier() + " has an unlock advancement");
			}
		}
		helper.succeed();
	}

	@GameTest
	public void tutorialChainsStartAndAdvance(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		Identifier scrapStep = CommonIds.id(CommonIds.WORLD, "tutorial/scrap");
		helper.assertFalse(done(helper, player, scrapStep), "not before holding scrap");
		give(player, CommonIds.SCRAP);
		helper.assertTrue(done(helper, player, scrapStep), "the guns chain's first step grants on scrap");

		Identifier firstNight = CommonIds.id(CommonIds.GAME, "tutorial/first_night");
		helper.assertFalse(done(helper, player, firstNight), "no night survived yet");
		Milestones.nightSurvived(helper.getLevel().getServer(), 1);
		helper.assertTrue(done(helper, player, firstNight), "dawn grants the first night");

		Identifier fieldBoss = CommonIds.id(CommonIds.BOSSES, "tutorial/field_boss");
		Identifier finalRaid = CommonIds.id(CommonIds.BOSSES, "tutorial/final_raid");
		Milestones.TRIGGER.trigger(player, MilestoneTrigger.Milestone.BOSS_KILLED, CommonIds.ASHWYRM, 0);
		helper.assertTrue(done(helper, player, fieldBoss), "any field boss counts");
		helper.assertFalse(done(helper, player, finalRaid), "a field boss isn't the final raid");
		Milestones.TRIGGER.trigger(player, MilestoneTrigger.Milestone.BOSS_KILLED, CommonIds.FINAL_BOSS, 0);
		helper.assertTrue(done(helper, player, finalRaid), "the Hollow Sovereign ends the chain");
		helper.succeed();
	}
}
