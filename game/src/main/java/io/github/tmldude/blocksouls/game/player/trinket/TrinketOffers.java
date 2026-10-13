package io.github.tmldude.blocksouls.game.player.trinket;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.random.RandomGenerator;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

import io.github.tmldude.blocksouls.common.Networking;
import io.github.tmldude.blocksouls.common.event.BossEvents;
import io.github.tmldude.blocksouls.common.player.PlayerStatus;
import io.github.tmldude.blocksouls.game.player.boon.Boon;
import io.github.tmldude.blocksouls.game.player.boon.BoonCatalog;
import io.github.tmldude.blocksouls.game.player.boon.BoonPayloads;
import io.github.tmldude.blocksouls.game.player.boon.BuildConfig;
import io.github.tmldude.blocksouls.game.player.boon.PlayerBoons;

/**
 * Trinket offers: a Trinket Case in hand rolls {@code offerCards} trinkets the player doesn't own; the pick is
 * remembered until made (closing the screen keeps the case). Every participant of a boss kill gets a case.
 */
public final class TrinketOffers {
	private static final Map<UUID, List<Boon>> PENDING = new HashMap<>();
	private static final RandomGenerator RANDOM = new java.util.Random();
	private static BuildConfig build = new BuildConfig();

	private TrinketOffers() {
	}

	public static void register(BuildConfig buildConfig) {
		build = buildConfig;
		Networking.registerClientbound(BoonPayloads.TrinketOffer.TYPE, BoonPayloads.TrinketOffer.CODEC);
		Networking.registerServerbound(BoonPayloads.ChooseTrinket.TYPE, BoonPayloads.ChooseTrinket.CODEC,
				(payload, context) -> choose(context.player(), payload.card()));
		ServerPlayConnectionEvents.DISCONNECT.register((listener, server) -> PENDING.remove(listener.player.getUUID()));
		BossEvents.KILLED.register((level, bossId, boss, players) -> {
			if (build.trinkets.caseOnBossKill) {
				for (ServerPlayer player : players) {
					giveCase(player);
				}
			}
		});
	}

	/** Hands the player a Trinket Case (what {@code BuildHooks.offerTrinkets} does). False if no trinket is left to offer. */
	public static boolean giveCase(ServerPlayer player) {
		if (options(player).isEmpty()) {
			return false;
		}
		ItemStack stack = new ItemStack(TrinketCaseItem.get());
		if (!player.addItem(stack) && !stack.isEmpty()) {
			player.spawnAtLocation(player.level(), stack);
		}
		player.level().playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0F, 1.2F);
		return true;
	}

	/** Trinkets the player doesn't own, sorted by id. */
	public static List<Boon> options(ServerPlayer player) {
		List<Boon> options = new ArrayList<>();
		for (Boon trinket : BoonCatalog.trinkets()) {
			if (!PlayerBoons.hasTrinket(player, Identifier.parse(trinket.id()))) {
				options.add(trinket);
			}
		}
		options.sort(Comparator.comparing(Boon::id));
		return options;
	}

	/** Rolls up to {@code offerCards} distinct trinkets. */
	public static List<Boon> roll(ServerPlayer player) {
		List<Boon> pool = options(player);
		List<Boon> cards = new ArrayList<>();
		while (cards.size() < Math.max(1, build.trinkets.offerCards) && !pool.isEmpty()) {
			cards.add(pool.remove(RANDOM.nextInt(pool.size())));
		}
		return cards;
	}

	/** Shows the pending offer, or rolls a new one. */
	public static void open(ServerPlayer player) {
		if (PlayerStatus.isDowned(player)) {
			return;
		}
		List<Boon> offer = PENDING.get(player.getUUID());
		if (offer == null || !stillValid(player, offer)) {
			offer = roll(player);
		}
		if (offer.isEmpty()) {
			PENDING.remove(player.getUUID());
			player.sendSystemMessage(Component.translatable("message.blocksouls_game.trinket.empty").withStyle(ChatFormatting.GRAY));
			return;
		}
		PENDING.put(player.getUUID(), offer);
		Networking.sendToPlayer(player, toPayload(player, offer));
	}

	/** Rolls a fresh offer (the Reroll Charm). False if nothing is pending. */
	public static boolean reroll(ServerPlayer player) {
		if (!PENDING.containsKey(player.getUUID())) {
			return false;
		}
		List<Boon> offer = roll(player);
		if (offer.isEmpty()) {
			return false;
		}
		PENDING.put(player.getUUID(), offer);
		Networking.sendToPlayer(player, toPayload(player, offer));
		return true;
	}

	/** Takes the chosen trinket and consumes one case. Returns false if rejected. */
	public static boolean choose(ServerPlayer player, int cardIndex) {
		List<Boon> offer = PENDING.get(player.getUUID());
		if (offer == null || cardIndex < 0 || cardIndex >= offer.size() || PlayerStatus.isDowned(player)) {
			return false;
		}
		Boon trinket = offer.get(cardIndex);
		if (PlayerBoons.hasTrinket(player, Identifier.parse(trinket.id())) || !consumeCase(player)) {
			return false;
		}
		PENDING.remove(player.getUUID());
		PlayerBoons.grantTrinket(player, trinket);
		player.level().playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.7F, 1.4F);
		return true;
	}

	public static Optional<List<Boon>> pending(ServerPlayer player) {
		return Optional.ofNullable(PENDING.get(player.getUUID()));
	}

	private static boolean stillValid(ServerPlayer player, List<Boon> offer) {
		for (Boon trinket : offer) {
			if (PlayerBoons.hasTrinket(player, Identifier.parse(trinket.id())) || BoonCatalog.get(trinket.id()) == null) {
				return false;
			}
		}
		return true;
	}

	private static boolean consumeCase(ServerPlayer player) {
		for (InteractionHand hand : InteractionHand.values()) {
			ItemStack stack = player.getItemInHand(hand);
			if (stack.is(TrinketCaseItem.get())) {
				stack.shrink(1);
				return true;
			}
		}
		var inventory = player.getInventory();
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (stack.is(TrinketCaseItem.get())) {
				stack.shrink(1);
				return true;
			}
		}
		return false;
	}

	private static BoonPayloads.TrinketOffer toPayload(ServerPlayer player, List<Boon> offer) {
		List<BoonPayloads.BoonView> cards = offer.stream().map(trinket -> BoonPayloads.BoonView.of(trinket, 1)).toList();
		Optional<BoonPayloads.BoonView> replaces = PlayerBoons.trinketToReplace(player)
				.map(BoonCatalog::get)
				.map(trinket -> BoonPayloads.BoonView.of(trinket, 1));
		return new BoonPayloads.TrinketOffer(cards, replaces);
	}
}
