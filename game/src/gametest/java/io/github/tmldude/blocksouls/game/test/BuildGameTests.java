package io.github.tmldude.blocksouls.game.test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;

import net.fabricmc.fabric.api.gametest.v1.GameTest;

import io.github.tmldude.blocksouls.common.CommonIds;
import io.github.tmldude.blocksouls.common.event.BossEvents;
import io.github.tmldude.blocksouls.common.event.CombatEvents;
import io.github.tmldude.blocksouls.common.player.PlayerStatus;
import io.github.tmldude.blocksouls.common.synergy.BuildHooks;
import io.github.tmldude.blocksouls.common.synergy.SynergyTags;
import io.github.tmldude.blocksouls.game.player.PlayerSystem;
import io.github.tmldude.blocksouls.game.player.boon.Boon;
import io.github.tmldude.blocksouls.game.player.boon.BoonCatalog;
import io.github.tmldude.blocksouls.game.player.boon.BoonEffect;
import io.github.tmldude.blocksouls.game.player.boon.BoonPayloads;
import io.github.tmldude.blocksouls.game.player.boon.BoonTier;
import io.github.tmldude.blocksouls.game.player.boon.CardRoller;
import io.github.tmldude.blocksouls.game.player.boon.PlayerBoons;
import io.github.tmldude.blocksouls.game.player.boon.rule.BoonRules;
import io.github.tmldude.blocksouls.game.player.downed.Downed;
import io.github.tmldude.blocksouls.game.player.relic.RelicItems;
import io.github.tmldude.blocksouls.game.player.relic.RelicOffers;
import io.github.tmldude.blocksouls.game.player.soul.Souls;
import io.github.tmldude.blocksouls.game.player.trinket.TrinketCaseItem;
import io.github.tmldude.blocksouls.game.player.trinket.TrinketOffers;

/** The v2 build systems: tags, keystones, levels, skip-for-souls, curses, trinkets. */
public class BuildGameTests {
	private static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath("blocksouls_game", path);
	}

	private static Boon def(GameTestHelper helper, String path) {
		Boon boon = BoonCatalog.get(id(path));
		helper.assertTrue(boon != null, path + " should be loaded from data");
		return boon;
	}

	@GameTest
	public void tagsStackIntoTheBuildLine(GameTestHelper helper) {
		ServerPlayer player = DownedTestPlayers.make(helper, 1, 1);
		Boon swift = def(helper, "swift_feet");
		PlayerBoons.grant(player, swift);
		PlayerBoons.grant(player, swift);
		PlayerBoons.grant(player, def(helper, "brawler"));
		PlayerBoons.grant(player, def(helper, "fire_ward"));

		Map<Identifier, Integer> tags = SynergyTags.of(player);
		helper.assertTrue(tags.getOrDefault(SynergyTags.KINETIC, 0) == 3, "kinetic stacks = 2 (Swift Feet L2) + 1 (Brawler), got " + tags);
		helper.assertTrue(tags.getOrDefault(SynergyTags.FIRE, 0) == 1, "fire 1");
		List<BoonPayloads.TagView> line = PlayerBoons.buildLine(player, 2);
		helper.assertTrue(line.size() == 2 && line.getFirst().tag().equals(SynergyTags.KINETIC), "build line leads with kinetic: " + line);
		helper.succeed();
	}

	@GameTest
	public void duplicatePicksLevelUpToThreePips(GameTestHelper helper) {
		ServerPlayer player = DownedTestPlayers.make(helper, 1, 1);
		Boon swift = def(helper, "swift_feet");
		helper.assertTrue(swift.maxLevel() == 3, "Swift Feet has 3 levels, has " + swift.maxLevel());
		for (int i = 1; i <= 3; i++) {
			helper.assertTrue(PlayerBoons.grant(player, swift), "pick " + i);
			helper.assertTrue(PlayerBoons.level(player, id("swift_feet")) == i, "level " + i);
		}
		helper.assertFalse(PlayerBoons.grant(player, swift), "capped at 3");
		helper.assertTrue(player.getEffect(MobEffects.SPEED).getAmplifier() == 2, "Speed III at level 3");
		helper.succeed();
	}

	@GameTest
	public void oneKeystonePerTriggerTag(GameTestHelper helper) {
		ServerPlayer player = DownedTestPlayers.make(helper, 1, 1);
		PlayerBoons.grant(player, def(helper, "quick_hands")); // on_reload stacks: both reload keystones become eligible
		Map<String, Integer> owned = PlayerBoons.ownedById(player);
		Map<String, Integer> tags = RelicOffers.ownedTags(player);
		helper.assertTrue(CardRoller.canOffer(def(helper, "powder_burn"), owned, tags, BoonCatalog.all(), RelicOffers.weighting()), "Powder Burn offerable");
		helper.assertTrue(CardRoller.canOffer(def(helper, "undertow"), owned, tags, BoonCatalog.all(), RelicOffers.weighting()), "Undertow offerable");

		PlayerBoons.grant(player, def(helper, "powder_burn"));
		owned = PlayerBoons.ownedById(player);
		tags = RelicOffers.ownedTags(player);
		helper.assertFalse(CardRoller.canOffer(def(helper, "undertow"), owned, tags, BoonCatalog.all(), RelicOffers.weighting()),
				"Undertow shares on_reload with the owned Powder Burn");
		helper.assertFalse(CardRoller.canOffer(def(helper, "deadeye_wind"), owned, tags, BoonCatalog.all(), RelicOffers.weighting()),
				"Deadeye Wind needs an on_precision or kinetic stack first");
		PlayerBoons.grant(player, def(helper, "swift_feet"));
		helper.assertTrue(CardRoller.canOffer(def(helper, "deadeye_wind"), PlayerBoons.ownedById(player), RelicOffers.ownedTags(player),
				BoonCatalog.all(), RelicOffers.weighting()), "a kinetic stack unlocks Deadeye Wind");
		helper.succeed();
	}

	@GameTest
	public void skippingARelicPaysSouls(GameTestHelper helper) {
		ServerPlayer player = DownedTestPlayers.make(helper, 1, 1);
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(RelicItems.get(BoonTier.RARE), 2));
		RelicOffers.open(player, BoonTier.RARE);
		helper.assertTrue(RelicOffers.pending(player).isPresent(), "offer pending");

		helper.assertTrue(RelicOffers.choose(player, BoonPayloads.Choose.SKIP, null), "skip accepted");
		int expected = PlayerSystem.BUILD.souls.skipRare;
		helper.assertTrue(Souls.count(player) == expected, "skipping a rare relic pays " + expected + " souls, got " + Souls.count(player));
		helper.assertTrue(player.getMainHandItem().getCount() == 1, "one relic consumed");
		helper.assertTrue(RelicOffers.pending(player).isEmpty(), "offer gone");
		helper.assertTrue(PlayerBoons.owned(player).isEmpty(), "no boon taken");

		PlayerBoons.grantTrinket(player, def(helper, "soul_jar"));
		RelicOffers.open(player, BoonTier.RARE);
		helper.assertTrue(RelicOffers.skipSouls(player, BoonTier.RARE) == expected + PlayerSystem.BUILD.rules.soulJar.bonusSouls, "Soul Jar adds to the payout");
		helper.succeed();
	}

	@GameTest
	public void upgradeCardLevelsUpAnOwnedBoon(GameTestHelper helper) {
		ServerPlayer player = DownedTestPlayers.make(helper, 1, 1);
		double chance = PlayerSystem.BUILD.offers.upgradeCardChance;
		PlayerSystem.BUILD.offers.upgradeCardChance = 1.0;
		try {
			PlayerBoons.grant(player, def(helper, "iron_skin"));
			player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(RelicItems.get(BoonTier.COMMON)));
			RelicOffers.open(player, BoonTier.COMMON);
			CardRoller.Offer offer = RelicOffers.pending(player).orElseThrow();
			helper.assertTrue(offer.cards().getFirst().upgrade(), "the first card is an Upgrade");
			helper.assertTrue(RelicOffers.choose(player, 0, null), "upgrade picked");
			helper.assertTrue(PlayerBoons.level(player, id("iron_skin")) == 2, "Iron Skin leveled to 2");
		} finally {
			PlayerSystem.BUILD.offers.upgradeCardChance = chance;
		}
		helper.succeed();
	}

	@GameTest
	public void cursesApplyPurgeAndAreLostOnGoingDown(GameTestHelper helper) {
		ServerPlayer player = DownedTestPlayers.make(helper, 1, 1);
		helper.assertTrue(id("hemorrhage").equals(BuildHooks.applyCurse(player, id("hemorrhage"))), "Hemorrhage applied through the hook");
		helper.assertTrue(BoonRules.healMultiplier(player) < 1.0F, "healing is weaker");
		helper.assertTrue(BuildHooks.applyCurse(player, id("hemorrhage")) == null, "no duplicates");
		helper.assertTrue(id("leaden").equals(BuildHooks.applyCurse(player, id("leaden"))), "Leaden applied");
		double slowed = player.getAttributeValue(Attributes.MOVEMENT_SPEED);
		helper.assertTrue(slowed < 0.1, "Leaden slows: " + slowed);
		helper.assertTrue(BuildHooks.curses(player).size() == 2, "two curses");

		helper.assertTrue(BuildHooks.purgeCurse(player), "purge the most recent (Leaden)");
		helper.assertTrue(BuildHooks.curses(player).equals(List.of(id("hemorrhage"))), "Hemorrhage left: " + BuildHooks.curses(player));
		helper.assertTrue(player.getAttributeValue(Attributes.MOVEMENT_SPEED) > slowed, "speed modifier removed");
		helper.assertTrue(BuildHooks.purgeCurse(player, id("hemorrhage")), "purge by id");
		helper.assertTrue(BoonRules.healMultiplier(player) == 1.0F, "healing back to normal");

		int max = PlayerSystem.BUILD.curses.maxCurses;
		int applied = 0;
		while (BuildHooks.applyRandomCurse(player) != null) {
			applied++;
		}
		helper.assertTrue(applied == Math.min(max, BoonCatalog.curses().size()), "random curses stop at the cap, applied " + applied);
		Downed.goDown(player);
		helper.assertTrue(BuildHooks.curses(player).isEmpty(), "going down wipes curses");
		helper.succeed();
	}

	@GameTest
	public void trinketsSurviveGoingDown(GameTestHelper helper) {
		ServerPlayer player = DownedTestPlayers.make(helper, 1, 1);
		PlayerBoons.grant(player, def(helper, "iron_skin"));
		PlayerBoons.grantTrinket(player, def(helper, "thick_coat"));
		PlayerBoons.grantTrinket(player, def(helper, "iron_lung"));
		helper.assertTrue(player.getAttributeValue(Attributes.ARMOR) == 3.0, "Thick Coat gives +3 armor, has " + player.getAttributeValue(Attributes.ARMOR));
		helper.assertTrue(player.getMaxHealth() == 24.0F, "Iron Skin +4 health");

		Downed.goDown(player);
		helper.assertTrue(PlayerStatus.isDowned(player), "down");
		helper.assertTrue(PlayerBoons.owned(player).isEmpty(), "boons gone");
		helper.assertTrue(PlayerBoons.trinkets(player).equals(List.of(id("thick_coat"), id("iron_lung"))), "trinkets kept: " + PlayerBoons.trinkets(player));
		helper.assertTrue(player.getAttributeValue(Attributes.ARMOR) == 3.0, "Thick Coat still applies");
		helper.assertTrue(player.getMaxHealth() == 20.0F, "Iron Skin's health is gone");
		helper.assertTrue(io.github.tmldude.blocksouls.common.player.PlayerTraits.level(player,
				io.github.tmldude.blocksouls.common.player.PlayerTraits.STAMINA_REGEN) == 1, "Iron Lung's trait survives the wipe");
		helper.succeed();
	}

	@GameTest
	public void trinketCaseOffersThreeAndReplacesTheOldestWhenFull(GameTestHelper helper) {
		ServerPlayer player = DownedTestPlayers.make(helper, 1, 1);
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(TrinketCaseItem.get(), 2));
		TrinketOffers.open(player);
		List<Boon> offer = TrinketOffers.pending(player).orElseThrow();
		helper.assertTrue(offer.size() == 3 && offer.stream().map(Boon::id).distinct().count() == 3, "three distinct trinkets");
		helper.assertTrue(offer.stream().allMatch(Boon::isTrinket), "all trinkets");
		Boon picked = offer.getFirst();
		helper.assertTrue(TrinketOffers.choose(player, 0), "pick works");
		helper.assertTrue(PlayerBoons.hasTrinket(player, Identifier.parse(picked.id())), "picked trinket owned");
		helper.assertTrue(player.getMainHandItem().getCount() == 1, "one case consumed");
		helper.assertTrue(TrinketOffers.pending(player).isEmpty(), "offer used up");

		// Fill the slots: the next pick replaces the oldest.
		List<Boon> rest = new ArrayList<>(TrinketOffers.options(player));
		while (PlayerBoons.trinkets(player).size() < PlayerSystem.BUILD.trinkets.slots) {
			PlayerBoons.grantTrinket(player, rest.removeFirst());
		}
		Identifier oldest = PlayerBoons.trinkets(player).getFirst();
		helper.assertTrue(PlayerBoons.trinketToReplace(player).orElseThrow().equals(oldest), "full slots replace the oldest");
		TrinketOffers.open(player);
		helper.assertTrue(TrinketOffers.choose(player, 0), "pick with full slots");
		helper.assertFalse(PlayerBoons.hasTrinket(player, oldest), "oldest replaced");
		helper.assertTrue(PlayerBoons.trinkets(player).size() == PlayerSystem.BUILD.trinkets.slots, "still at the slot cap");
		helper.succeed();
	}

	@GameTest
	public void lastShellPreventsOneDownPerSiege(GameTestHelper helper) {
		ServerPlayer player = DownedTestPlayers.make(helper, 1, 1);
		PlayerBoons.grantTrinket(player, def(helper, "last_shell"));
		player.hurtServer(helper.getLevel(), player.damageSources().generic(), 100.0F);
		helper.assertFalse(PlayerStatus.isDowned(player), "the shell took the hit");
		helper.assertTrue(player.getHealth() == PlayerSystem.BUILD.rules.lastShell.health, "left at " + player.getHealth());

		player.removeEffect(MobEffects.RESISTANCE);
		// Inside the hurt cooldown vanilla only applies the damage above the last hit, so hit harder than the first.
		player.hurtServer(helper.getLevel(), player.damageSources().generic(), 500.0F);
		helper.assertTrue(PlayerStatus.isDowned(player), "the second lethal hit this siege downs");
		helper.succeed();
	}

	@GameTest
	public void keystonesReactToCombatEvents(GameTestHelper helper) {
		ServerPlayer player = DownedTestPlayers.make(helper, 1, 1);
		LivingEntity zombie = helper.spawn(EntityTypes.ZOMBIE, new BlockPos(2, 1, 1));
		PlayerBoons.grant(player, def(helper, "red_ledger"));
		helper.assertTrue(BoonRules.blocksNaturalRegen(player), "Red Ledger switches natural regen off");
		helper.assertTrue(player.getAttributeValue(Attributes.MAX_ABSORPTION) >= PlayerSystem.BUILD.rules.redLedger.overhealMax,
				"Red Ledger raises max absorption so overheal can stick: " + player.getAttributeValue(Attributes.MAX_ABSORPTION));
		CombatEvents.HIT.invoker().onHit(player, zombie, 4.0F, false, true);
		helper.assertTrue(player.getAbsorptionAmount() == (float) PlayerSystem.BUILD.rules.redLedger.absorptionPerKill,
				"a kill overheals: " + player.getAbsorptionAmount());

		PlayerBoons.grant(player, def(helper, "powder_burn"));
		float before = zombie.getHealth();
		for (int i = 0; i < 3; i++) {
			CombatEvents.HIT.invoker().onHit(player, zombie, 4.0F, false, false);
		}
		CombatEvents.RELOADED.invoker().onReloaded(player, 6);
		float expected = (float) (12.0 * PlayerSystem.BUILD.rules.powderBurn.damageShare);
		helper.assertTrue(zombie.getHealth() < before, "the reload nova hurt the zombie by about " + expected + ": " + before + " -> " + zombie.getHealth());

		Downed.goDown(player);
		helper.assertFalse(BoonRules.blocksNaturalRegen(player), "rules stop with the boons");
		helper.succeed();
	}

	@GameTest
	public void heavyPocketsTakesSoulsOnGoingDown(GameTestHelper helper) {
		ServerPlayer player = DownedTestPlayers.make(helper, 1, 1);
		Souls.give(player, 5);
		BuildHooks.applyCurse(player, id("heavy_pockets"));
		player.hurtServer(helper.getLevel(), player.damageSources().generic(), 100.0F);
		helper.assertTrue(PlayerStatus.isDowned(player), "down");
		helper.assertTrue(Souls.count(player) == 5 - PlayerSystem.BUILD.rules.heavyPockets.soulsLost, "souls lost: " + Souls.count(player));
		helper.succeed();
	}

	@GameTest
	public void bossKillHandsEveryParticipantACaseSolo(GameTestHelper helper) {
		bossKillCases(helper, 1);
	}

	@GameTest
	public void bossKillHandsEveryParticipantACaseParty(GameTestHelper helper) {
		bossKillCases(helper, 8);
	}

	private static void bossKillCases(GameTestHelper helper, int partySize) {
		List<ServerPlayer> party = new ArrayList<>();
		for (int i = 0; i < partySize; i++) {
			party.add(DownedTestPlayers.make(helper, 1 + i % 4, 1 + i / 4));
		}
		LivingEntity boss = helper.spawn(EntityTypes.ZOMBIE, new BlockPos(2, 1, 2));
		BossEvents.KILLED.invoker().onBoss(helper.getLevel(), CommonIds.PALE_RIDER, boss, party);
		for (ServerPlayer player : party) {
			helper.assertTrue(player.getInventory().contains(new ItemStack(TrinketCaseItem.get())), "every participant gets a Trinket Case");
		}
		helper.succeed();
	}

	@GameTest
	public void everyDefinitionResolves(GameTestHelper helper) {
		helper.assertTrue(BoonCatalog.curses().size() >= 5, "curses loaded: " + BoonCatalog.curses().size());
		helper.assertTrue(BoonCatalog.trinkets().size() == 8, "trinkets loaded: " + BoonCatalog.trinkets().size());
		helper.assertTrue(BoonCatalog.all().stream().filter(Boon::keystone).count() == 8, "keystones loaded");
		for (Boon boon : BoonCatalog.everything()) {
			helper.assertTrue(BoonCatalog.iconResolves(boon), boon.id() + "'s icon resolves");
			for (String tag : boon.tags()) {
				helper.assertTrue(SynergyTags.isKnown(Identifier.parse(tag)), boon.id() + " uses the known tag " + tag);
			}
			for (BoonEffect effect : boon.effects()) {
				if (effect instanceof BoonEffect.Rule rule) {
					helper.assertTrue(BoonRules.has(Identifier.parse(rule.rule())), boon.id() + "'s rule " + rule.rule() + " is implemented");
				}
			}
		}
		helper.succeed();
	}
}
