package io.github.tmldude.blocksouls.game.test;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import net.fabricmc.fabric.api.gametest.v1.GameTest;

import io.github.tmldude.blocksouls.common.player.PlayerTraits;
import io.github.tmldude.blocksouls.game.player.boon.Boon;
import io.github.tmldude.blocksouls.game.player.boon.BoonCatalog;
import io.github.tmldude.blocksouls.game.player.boon.BoonTier;
import io.github.tmldude.blocksouls.game.player.boon.CardRoller;
import io.github.tmldude.blocksouls.game.player.boon.PlayerBoons;
import io.github.tmldude.blocksouls.game.player.downed.Downed;
import io.github.tmldude.blocksouls.game.player.relic.RelicItems;
import io.github.tmldude.blocksouls.game.player.relic.RelicOffers;

public class BoonGameTests {
	private static Boon boon(GameTestHelper helper, String path) {
		Boon boon = BoonCatalog.get(Identifier.fromNamespaceAndPath("blocksouls_game", path));
		helper.assertTrue(boon != null, "boon " + path + " should be loaded from data");
		return boon;
	}

	@GameTest
	public void grantingAppliesEffectAttributeAndTrait(GameTestHelper helper) {
		ServerPlayer player = DownedTestPlayers.make(helper, 1, 1);
		PlayerBoons.grant(player, boon(helper, "swift_feet"));
		PlayerBoons.grant(player, boon(helper, "iron_skin"));
		PlayerBoons.grant(player, boon(helper, "hollow_bones"));

		helper.assertTrue(player.hasEffect(MobEffects.SPEED), "Swift Feet gives Speed");
		helper.assertTrue(player.getMaxHealth() == 24.0F, "Iron Skin gives +4 max health, max is " + player.getMaxHealth());
		helper.assertTrue(PlayerTraits.has(player, PlayerTraits.DOUBLE_JUMP), "Hollow Bones gives double jump");
		helper.succeed();
	}

	@GameTest
	public void goingDownClearsBoonsButKeepsItems(GameTestHelper helper) {
		ServerPlayer player = DownedTestPlayers.make(helper, 1, 1);
		PlayerBoons.grant(player, boon(helper, "swift_feet"));
		PlayerBoons.grant(player, boon(helper, "iron_skin"));
		PlayerBoons.grant(player, boon(helper, "hollow_bones"));
		PlayerBoons.grant(player, boon(helper, "sky_rider"));
		helper.assertTrue(player.getInventory().contains(new ItemStack(Items.ELYTRA)), "Sky Rider gives an Elytra");

		Downed.goDown(player);

		helper.assertTrue(PlayerBoons.owned(player).isEmpty(), "boons are gone");
		helper.assertFalse(player.hasEffect(MobEffects.SPEED), "Speed is gone");
		helper.assertTrue(player.getMaxHealth() == 20.0F, "max health is back to 20, is " + player.getMaxHealth());
		helper.assertFalse(PlayerTraits.has(player, PlayerTraits.DOUBLE_JUMP), "double jump is gone");
		helper.assertTrue(player.getInventory().contains(new ItemStack(Items.ELYTRA)), "the Elytra is gear and stays");
		helper.succeed();
	}

	@GameTest
	public void stackableBoonsCapAtMaxLevel(GameTestHelper helper) {
		ServerPlayer player = DownedTestPlayers.make(helper, 1, 1);
		Boon ironSkin = boon(helper, "iron_skin");
		for (int i = 0; i < ironSkin.maxLevel(); i++) {
			helper.assertTrue(PlayerBoons.grant(player, ironSkin), "level " + (i + 1) + " can be granted");
		}
		helper.assertFalse(PlayerBoons.grant(player, ironSkin), "can't go past max level");
		helper.assertTrue(player.getMaxHealth() == 20.0F + 4.0F * ironSkin.maxLevel(), "modifier scales with level, max " + player.getMaxHealth());
		helper.succeed();
	}

	@GameTest
	public void usingARelicOffersCardsAndPickingConsumesIt(GameTestHelper helper) {
		ServerPlayer player = DownedTestPlayers.make(helper, 1, 1);
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(RelicItems.get(BoonTier.COMMON), 2));

		RelicOffers.open(player, BoonTier.COMMON);
		CardRoller.Offer offer = RelicOffers.pending(player).orElse(null);
		helper.assertTrue(offer != null && offer.cards().size() == 3, "three cards offered");

		int pick = offer.cards().getFirst().wildcard() ? 1 : 0;
		Boon chosen = offer.cards().get(pick).boon();
		helper.assertTrue(RelicOffers.choose(player, pick, null), "picking a card works");
		helper.assertTrue(PlayerBoons.level(player, Identifier.parse(chosen.id())) == 1, "picked boon is owned");
		helper.assertTrue(player.getMainHandItem().getCount() == 1, "one relic consumed");
		helper.assertTrue(RelicOffers.pending(player).isEmpty(), "offer used up");
		helper.assertTrue(BuiltInRegistries.ITEM.containsKey(Identifier.fromNamespaceAndPath("blocksouls_game", "relic_legendary")),
				"all relic tiers are registered");
		helper.succeed();
	}
}
