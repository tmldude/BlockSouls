package io.github.tmldude.blocksouls.game.test;

import java.nio.file.Files;
import java.nio.file.Path;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import net.fabricmc.fabric.api.gametest.v1.GameTest;

import io.github.tmldude.blocksouls.common.CommonIds;
import io.github.tmldude.blocksouls.common.event.RunEvents;
import io.github.tmldude.blocksouls.common.player.Capabilities;
import io.github.tmldude.blocksouls.common.player.PlayerTraits;
import io.github.tmldude.blocksouls.common.run.Difficulty;
import io.github.tmldude.blocksouls.common.run.RunPhase;
import io.github.tmldude.blocksouls.common.run.RunState;
import io.github.tmldude.blocksouls.game.player.PlayerSystem;
import io.github.tmldude.blocksouls.game.player.classes.PlayerClasses;
import io.github.tmldude.blocksouls.game.player.rune.PlayerRunes;
import io.github.tmldude.blocksouls.game.player.rune.Rune;
import io.github.tmldude.blocksouls.game.player.rune.RuneCatalog;
import io.github.tmldude.blocksouls.game.player.rune.RunePayout;
import io.github.tmldude.blocksouls.game.player.rune.RuneProfile;
import io.github.tmldude.blocksouls.game.player.rune.RuneProfiles;

/** Rune trees: unlocks follow the tree and the balance, apply only while playing the class, persist, and get paid at run end. */
public class RuneGameTests {
	@GameTest
	public void twentyFourRunesLoad(GameTestHelper helper) {
		helper.assertTrue(RuneCatalog.all().size() == 24, "24 runes loaded, got " + RuneCatalog.all().size());
		helper.assertTrue(RuneCatalog.forClass("blocksouls_game:ranger").size() == 6, "six per class");
		helper.succeed();
	}

	@GameTest
	public void unlocksNeedSoulsAndPrerequisitesAndApplyOnlyToTheirClass(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		PlayerRunes.reset(player);
		Rune fleet = RuneCatalog.get(id("ranger_fleet"));
		Rune wings = RuneCatalog.get(id("ranger_wings"));
		helper.assertTrue(fleet != null && wings != null, "ranger runes");

		helper.assertTrue(!PlayerRunes.unlock(player, fleet), "no souls, no rune");
		PlayerRunes.addSouls(player, 100);
		helper.assertTrue(!PlayerRunes.unlock(player, wings), "wings need fleet first");
		helper.assertTrue(PlayerRunes.unlock(player, fleet), "fleet");
		helper.assertTrue(PlayerRunes.unlock(player, wings), "then wings");
		helper.assertTrue(PlayerRunes.profile(player).souls == 100 - PlayerRunes.cost(fleet) - PlayerRunes.cost(wings), "souls spent");

		helper.assertTrue(!Capabilities.has(player, Capabilities.GLIDE), "no class yet: runes are dormant");
		PlayerClasses.choose(player, id("warden"));
		helper.assertTrue(!Capabilities.has(player, Capabilities.GLIDE), "a warden doesn't get the ranger's wings");
		PlayerClasses.choose(player, id("ranger"));
		helper.assertTrue(Capabilities.has(player, Capabilities.GLIDE), "playing ranger: the wings are on");
		helper.assertTrue(Capabilities.has(player, Capabilities.CLIMB), "...on top of the universal kit");
		helper.assertTrue(PlayerRunes.active(player).size() == 2, "two active runes");
		PlayerClasses.clear(player);
		helper.assertTrue(!Capabilities.has(player, Capabilities.GLIDE), "cleared: wings off again");
		PlayerRunes.reset(player);
		helper.succeed();
	}

	@GameTest
	public void runeTraitsStackWithTheClassAndBoons(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		PlayerRunes.reset(player);
		PlayerRunes.addSouls(player, 100);
		PlayerClasses.choose(player, id("ranger")); // the class itself grants RELOAD_SPEED 1
		helper.assertTrue(PlayerTraits.level(player, PlayerTraits.RELOAD_SPEED) == 1, "class reload trait");
		helper.assertTrue(PlayerRunes.unlock(player, RuneCatalog.get(id("ranger_eagle_eye"))), "eagle eye");
		helper.assertTrue(PlayerTraits.level(player, PlayerTraits.RELOAD_SPEED) == 2, "class + rune = 2, got " + PlayerTraits.level(player, PlayerTraits.RELOAD_SPEED));
		PlayerClasses.apply(player);
		helper.assertTrue(PlayerTraits.level(player, PlayerTraits.RELOAD_SPEED) == 2, "stable across re-applies");
		PlayerClasses.clear(player);
		helper.assertTrue(PlayerTraits.level(player, PlayerTraits.RELOAD_SPEED) == 0, "all gone with the class");
		PlayerRunes.reset(player);
		helper.succeed();
	}

	@GameTest
	public void profilesPersistOnDiskUnderTheServerRoot(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		PlayerRunes.reset(player);
		PlayerRunes.addSouls(player, 42);
		Path file = RuneProfiles.file(server, player.getUUID());
		helper.assertTrue(Files.exists(file), "profile written to " + file);
		helper.assertTrue(file.startsWith(server.getServerDirectory().resolve("blocksouls/runes")), "under <server root>/blocksouls/runes");
		RuneProfiles.evict(player.getUUID());
		RuneProfile reloaded = RuneProfiles.get(server, player.getUUID());
		helper.assertTrue(reloaded.souls == 42, "reloaded from disk: " + reloaded.souls);
		PlayerRunes.reset(player);
		helper.succeed();
	}

	@GameTest
	public void runOverPaysOutAndTakesUnspentSouls(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		PlayerRunes.reset(player);
		Item soul = BuiltInRegistries.ITEM.getValue(CommonIds.SOUL);
		helper.assertTrue(soul != null, "the test mod registers a soul item");
		player.getInventory().add(new ItemStack(soul, 40));

		RunState over = new RunState(RunPhase.OVER, 7, 3, false, Difficulty.NORMAL);
		RunEvents.RUN_OVER.invoker().onChange(server, over);
		int expected = RunePayout.payout(3, 7, 40, PlayerSystem.CONFIG.runes);
		RuneProfile profile = PlayerRunes.profile(player);
		helper.assertTrue(profile.souls == expected, "paid " + expected + ", got " + profile.souls);
		helper.assertTrue(PlayerRunes.lastPayout(player.getUUID()) == expected, "remembered for the summary");
		helper.assertTrue(!player.getInventory().contains(new ItemStack(soul)), "the soul items are gone");
		PlayerRunes.reset(player);
		helper.succeed();
	}

	private static Identifier id(String name) {
		return Identifier.fromNamespaceAndPath("blocksouls_game", name);
	}
}
