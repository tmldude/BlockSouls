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
import io.github.tmldude.blocksouls.game.player.PlayersConfig;
import io.github.tmldude.blocksouls.game.player.boon.Boon;
import io.github.tmldude.blocksouls.game.player.boon.BoonCatalog;
import io.github.tmldude.blocksouls.game.player.boon.BoonPayloads;
import io.github.tmldude.blocksouls.game.player.boon.BoonTier;
import io.github.tmldude.blocksouls.game.player.boon.CardRoller;
import io.github.tmldude.blocksouls.game.player.boon.PlayerBoons;
import io.github.tmldude.blocksouls.game.player.boon.TierOdds;
import io.github.tmldude.blocksouls.game.player.downed.Downed;

/**
 * Relic cards: rolled on the server when a relic is used, shown by the client, picked by index. An offer stays
 * pending until a card is picked, so closing the screen and using the relic again shows the same cards.
 */
public final class RelicOffers {
	private static final Map<UUID, CardRoller.Offer> PENDING = new HashMap<>();
	private static final RandomGenerator RANDOM = new java.util.Random();
	private static PlayersConfig.Boons config;

	private RelicOffers() {
	}

	public static void register(PlayersConfig.Boons boonsConfig) {
		config = boonsConfig;
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
			offer = CardRoller.roll(BoonCatalog.all(), PlayerBoons.ownedById(player), tier, config.cardsPerRelic,
					config.wildcardChance, config.combosAnyTier, RANDOM);
		}
		if (offer.isEmpty()) {
			PENDING.remove(player.getUUID());
			player.sendSystemMessage(Component.translatable("message.blocksouls_game.relic.empty").withStyle(ChatFormatting.GRAY));
			return;
		}

		PENDING.put(player.getUUID(), offer);
		Networking.sendToPlayer(player, toPayload(player, offer));
	}

	/** Grants the chosen card's boon and consumes one relic of the offer's tier. Returns false if rejected. */
	public static boolean choose(ServerPlayer player, int cardIndex, Identifier wildcardPick) {
		CardRoller.Offer offer = PENDING.get(player.getUUID());
		if (offer == null || cardIndex < 0 || cardIndex >= offer.cards().size() || PlayerStatus.isDowned(player)) {
			return false;
		}

		CardRoller.Card card = offer.cards().get(cardIndex);
		Boon boon = card.wildcard()
				? offer.wildcardOptions().stream().filter(b -> wildcardPick != null && b.id().equals(wildcardPick.toString())).findFirst().orElse(null)
				: card.boon();
		if (boon == null || !CardRoller.canOffer(boon, PlayerBoons.ownedById(player)) || !consumeRelic(player, offer.tier())) {
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
		for (CardRoller.Card card : offer.cards()) {
			if (!card.wildcard() && !CardRoller.canOffer(card.boon(), owned)) {
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
				.map(card -> card.wildcard()
						? new BoonPayloads.Card(Optional.empty(), true)
						: new BoonPayloads.Card(Optional.of(view(card.boon(), owned)), false))
				.toList();
		List<BoonPayloads.BoonView> options = offer.wildcardOptions().stream().map(boon -> view(boon, owned)).toList();
		return new BoonPayloads.Offer(offer.tier().ordinal(), cards, options);
	}

	private static BoonPayloads.BoonView view(Boon boon, Map<String, Integer> owned) {
		return BoonPayloads.BoonView.of(boon, owned.getOrDefault(boon.id(), 0) + 1);
	}
}
