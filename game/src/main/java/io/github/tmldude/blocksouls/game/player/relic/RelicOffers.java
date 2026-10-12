package io.github.tmldude.blocksouls.game.player.relic;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.random.RandomGenerator;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

import io.github.tmldude.blocksouls.common.Networking;
import io.github.tmldude.blocksouls.common.event.RunEvents;
import io.github.tmldude.blocksouls.common.player.PlayerStatus;
import io.github.tmldude.blocksouls.common.run.Runs;
import io.github.tmldude.blocksouls.common.synergy.SynergyTags;
import io.github.tmldude.blocksouls.game.player.PlayersConfig;
import io.github.tmldude.blocksouls.game.player.boon.Boon;
import io.github.tmldude.blocksouls.game.player.boon.BoonCatalog;
import io.github.tmldude.blocksouls.game.player.boon.BoonPayloads;
import io.github.tmldude.blocksouls.game.player.boon.BoonTier;
import io.github.tmldude.blocksouls.game.player.boon.BuildConfig;
import io.github.tmldude.blocksouls.game.player.boon.CardRoller;
import io.github.tmldude.blocksouls.game.player.boon.OfferRules;
import io.github.tmldude.blocksouls.game.player.boon.PlayerBoons;
import io.github.tmldude.blocksouls.game.player.boon.TierOdds;
import io.github.tmldude.blocksouls.game.player.boon.rule.BoonRules;
import io.github.tmldude.blocksouls.game.player.downed.Downed;
import io.github.tmldude.blocksouls.game.player.soul.Souls;

/**
 * Relic cards: rolled on the server when a relic is used, shown by the client, picked by index. An offer stays
 * pending until a card is picked, so closing the screen and using the relic again shows the same cards. Cards lean
 * toward the tags the player's build already has; declining them all pays souls.
 */
public final class RelicOffers {
	private static final Map<UUID, CardRoller.Offer> PENDING = new HashMap<>();
	private static final RandomGenerator RANDOM = new java.util.Random();
	private static PlayersConfig.Boons config;
	private static BuildConfig build = new BuildConfig();

	private RelicOffers() {
	}

	public static void register(PlayersConfig.Boons boonsConfig, BuildConfig buildConfig) {
		config = boonsConfig;
		build = buildConfig;
		Networking.registerClientbound(BoonPayloads.Offer.TYPE, BoonPayloads.Offer.CODEC);
		Networking.registerServerbound(BoonPayloads.Choose.TYPE, BoonPayloads.Choose.CODEC,
				(payload, context) -> choose(context.player(), payload.card(), payload.wildcardPick().orElse(null)));
		ServerPlayConnectionEvents.DISCONNECT.register((listener, server) -> PENDING.remove(listener.player.getUUID()));
		RunEvents.DAWN.register((server, night) -> dropRelics(server));
	}

	/** Every online, standing player in the run gets a relic at their feet, tier rolled from the wave. */
	public static void dropRelics(MinecraftServer server) {
		int wave = Runs.get(server).wave();
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (Downed.canGoDown(player) && !PlayerStatus.isDowned(player)) {
				dropRelic(player, TierOdds.roll(config.tierOdds, wave, RANDOM));
			}
		}
	}

	public static void dropRelic(ServerPlayer player, BoonTier tier) {
		player.spawnAtLocation(player.level(), new ItemStack(RelicItems.get(tier)));
		player.level().playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0F, 0.8F);
	}

	/** Shows the pending offer for this tier, or rolls a new one. */
	public static void open(ServerPlayer player, BoonTier tier) {
		if (PlayerStatus.isDowned(player)) {
			return;
		}

		CardRoller.Offer offer = PENDING.get(player.getUUID());
		if (offer == null || offer.tier() != tier || !stillValid(player, offer)) {
			offer = roll(player, tier);
		}
		if (offer.isEmpty()) {
			PENDING.remove(player.getUUID());
			player.sendSystemMessage(Component.translatable("message.blocksouls_game.relic.empty").withStyle(ChatFormatting.GRAY));
			return;
		}

		PENDING.put(player.getUUID(), offer);
		Networking.sendToPlayer(player, toPayload(player, offer));
	}

	/** Rolls a fresh offer of the pending tier (the Reroll Charm). False if nothing is pending. */
	public static boolean reroll(ServerPlayer player) {
		CardRoller.Offer pending = PENDING.get(player.getUUID());
		if (pending == null) {
			return false;
		}
		CardRoller.Offer offer = roll(player, pending.tier());
		if (offer.isEmpty()) {
			return false;
		}
		PENDING.put(player.getUUID(), offer);
		Networking.sendToPlayer(player, toPayload(player, offer));
		return true;
	}

	public static CardRoller.Offer roll(ServerPlayer player, BoonTier tier) {
		return CardRoller.roll(BoonCatalog.all(), PlayerBoons.ownedById(player), ownedTags(player), tier, settings(),
				!PlayerBoons.upgradable(player).isEmpty(), RANDOM);
	}

	/** The build's tag stacks keyed by string id, for the roller. */
	public static Map<String, Integer> ownedTags(ServerPlayer player) {
		Map<String, Integer> tags = new HashMap<>();
		SynergyTags.of(player).forEach((tag, stacks) -> tags.put(tag.toString(), stacks));
		return tags;
	}

	public static OfferRules.Weighting weighting() {
		BuildConfig.Offers offers = build.offers;
		return new OfferRules.Weighting(offers.tagWeightPerStack, offers.tagWeightMax, offers.keystoneMinTagStacks, offers.oneKeystonePerTrigger);
	}

	private static CardRoller.Settings settings() {
		return new CardRoller.Settings(config.cardsPerRelic, config.wildcardChance, config.combosAnyTier, build.offers.upgradeCardChance, weighting());
	}

	/** Souls paid for declining a relic of this tier, with the player's bonuses. */
	public static int skipSouls(ServerPlayer player, BoonTier tier) {
		int base = switch (tier) {
			case COMMON -> build.souls.skipCommon;
			case RARE -> build.souls.skipRare;
			case LEGENDARY -> build.souls.skipLegendary;
		};
		return Math.max(0, base + BoonRules.skipSoulsBonus(player));
	}

	/**
	 * Grants the chosen card's boon and consumes one relic of the offer's tier; {@link BoonPayloads.Choose#SKIP}
	 * consumes the relic for souls instead. Returns false if rejected.
	 */
	public static boolean choose(ServerPlayer player, int cardIndex, Identifier wildcardPick) {
		CardRoller.Offer offer = PENDING.get(player.getUUID());
		if (offer == null || PlayerStatus.isDowned(player)) {
			return false;
		}
		if (cardIndex == BoonPayloads.Choose.SKIP) {
			if (!consumeRelic(player, offer.tier())) {
				return false;
			}
			PENDING.remove(player.getUUID());
			int souls = skipSouls(player, offer.tier());
			Souls.give(player, souls);
			player.sendSystemMessage(Component.translatable("message.blocksouls_game.relic.skipped", souls).withStyle(ChatFormatting.GRAY));
			player.level().playSound(null, player.blockPosition(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.7F, 0.8F);
			return true;
		}
		if (cardIndex < 0 || cardIndex >= offer.cards().size()) {
			return false;
		}

		CardRoller.Card card = offer.cards().get(cardIndex);
		Boon boon;
		if (card.upgrade()) {
			List<Boon> upgradable = PlayerBoons.upgradable(player);
			if (upgradable.isEmpty()) {
				return false;
			}
			boon = upgradable.get(RANDOM.nextInt(upgradable.size()));
		} else if (card.wildcard()) {
			boon = offer.wildcardOptions().stream().filter(b -> wildcardPick != null && b.id().equals(wildcardPick.toString())).findFirst().orElse(null);
		} else {
			boon = card.boon();
		}
		if (boon == null || !CardRoller.canOffer(boon, PlayerBoons.ownedById(player), ownedTags(player), BoonCatalog.all(), weighting())
				|| !consumeRelic(player, offer.tier())) {
			return false;
		}

		PENDING.remove(player.getUUID());
		PlayerBoons.grant(player, boon);
		player.level().playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.7F, 1.2F);
		return true;
	}

	/** The offer the player would see now, if any (gametests, debug). */
	public static Optional<CardRoller.Offer> pending(ServerPlayer player) {
		return Optional.ofNullable(PENDING.get(player.getUUID()));
	}

	private static boolean stillValid(ServerPlayer player, CardRoller.Offer offer) {
		Map<String, Integer> owned = PlayerBoons.ownedById(player);
		Map<String, Integer> tags = ownedTags(player);
		for (CardRoller.Card card : offer.cards()) {
			if (card.isBoon() && !CardRoller.canOffer(card.boon(), owned, tags, BoonCatalog.all(), weighting())) {
				return false;
			}
			if (card.upgrade() && PlayerBoons.upgradable(player).isEmpty()) {
				return false;
			}
		}
		return true;
	}

	private static boolean consumeRelic(ServerPlayer player, BoonTier tier) {
		RelicItem relic = RelicItems.get(tier);
		for (InteractionHand hand : InteractionHand.values()) {
			ItemStack stack = player.getItemInHand(hand);
			if (stack.is(relic)) {
				stack.shrink(1);
				return true;
			}
		}
		var inventory = player.getInventory();
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (stack.is(relic)) {
				stack.shrink(1);
				return true;
			}
		}
		return false;
	}

	private static BoonPayloads.Offer toPayload(ServerPlayer player, CardRoller.Offer offer) {
		Map<String, Integer> owned = PlayerBoons.ownedById(player);
		List<BoonPayloads.Card> cards = offer.cards().stream()
				.map(card -> card.isBoon()
						? new BoonPayloads.Card(Optional.of(view(card.boon(), owned)), false, false)
						: new BoonPayloads.Card(Optional.empty(), card.wildcard(), card.upgrade()))
				.toList();
		List<BoonPayloads.BoonView> options = offer.wildcardOptions().stream().map(boon -> view(boon, owned)).toList();
		return new BoonPayloads.Offer(offer.tier().ordinal(), cards, options, skipSouls(player, offer.tier()));
	}

	private static BoonPayloads.BoonView view(Boon boon, Map<String, Integer> owned) {
		return BoonPayloads.BoonView.of(boon, owned.getOrDefault(boon.id(), 0) + 1);
	}
}
