package io.github.tmldude.blocksouls.game.run.director;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

import io.github.tmldude.blocksouls.common.CommonIds;
import io.github.tmldude.blocksouls.common.economy.CampObject;
import io.github.tmldude.blocksouls.common.event.EconomyEvents;
import io.github.tmldude.blocksouls.common.event.PlayerRunEvents;
import io.github.tmldude.blocksouls.common.party.PartyScaling;
import io.github.tmldude.blocksouls.common.synergy.BuildHooks;
import io.github.tmldude.blocksouls.game.player.boon.BoonTier;
import io.github.tmldude.blocksouls.game.player.relic.RelicOffers;
import io.github.tmldude.blocksouls.game.player.soul.Souls;
import io.github.tmldude.blocksouls.game.run.DirectorHooks;

/**
 * The run's economy objects (PLAN2 §3c): what using a blood altar, cursed chest, challenge pylon, the camp horn
 * or the shopkeeper does. The world module owns the blocks and entity and fires
 * {@link EconomyEvents#CAMP_OBJECT_USED}; everything here is a world object, no menus but the shop's board.
 */
public final class Economy {
	/** The altar's max-health modifier (one per player, sized by the hearts paid this run). */
	static final Identifier ALTAR_MODIFIER = CommonIds.id(CommonIds.GAME, "blood_altar_hearts");

	/** A running challenge pylon. */
	record Pylon(ServerLevel level, BlockPos pos, long startedAt) {
	}

	static @Nullable Pylon pylon;
	private static long lastPylonPack;
	/** Wagers are taken until this second (siege start + window); -1 when closed. */
	static long wagerOpenUntil = -1;

	private Economy() {
	}

	private static DirectorConfig config() {
		return Director.config();
	}

	static void init() {
		EconomyEvents.CAMP_OBJECT_USED.register(Economy::onUsed);
		PlayerRunEvents.DOWNED.register(player -> {
			DirectorData data = DirectorData.get(player.level().getServer());
			if (Director.siegeActive && data.wagers.remove(player.getUUID()) != null) {
				data.setDirty();
				player.sendSystemMessage(Component.translatable("message.blocksouls_game.director.wager_lost"));
				Director.log(player.level().getServer(), DirectorLog.Kind.ECONOMY, player.getScoreboardName() + " went down: wager forfeit");
			}
		});
		ServerPlayConnectionEvents.JOIN.register((listener, sender, server) -> {
			ServerPlayer player = listener.player;
			Integer hearts = DirectorData.get(server).altarHearts.get(player.getUUID());
			applyAltarHearts(player, hearts == null ? 0 : hearts);
		});
	}

	static boolean onUsed(ServerPlayer player, CampObject object, BlockPos pos, boolean sneaking) {
		MinecraftServer server = player.level().getServer();
		if (!Director.active(server)) {
			return false;
		}
		ServerLevel level = (ServerLevel) player.level();
		String error = switch (object) {
			case BLOOD_ALTAR -> useAltar(player, level, pos, sneaking);
			case CURSED_CHEST -> useCursedChest(player, level, pos);
			case CHALLENGE_PYLON -> startPylon(player, level, pos);
			case CAMP_HORN -> DayLoop.blowHorn(player, sneaking);
			case SHOPKEEPER -> sneaking && wagerOpen(server) ? wager(player) : Shop.open(player);
		};
		if (error != null) {
			player.sendSystemMessage(Component.literal(error));
		} else {
			Director.beat(server, "economy " + object.name().toLowerCase(java.util.Locale.ROOT));
		}
		return true;
	}

	static void onRunStarted(MinecraftServer server) {
		pylon = null;
		wagerOpenUntil = -1;
		clearAltarHearts(server);
	}

	static void onRunOver(MinecraftServer server) {
		pylon = null;
		wagerOpenUntil = -1;
		clearAltarHearts(server);
		DirectorData data = DirectorData.get(server);
		data.wagers.clear();
		data.setDirty();
	}

	// ---------------------------------------------------------------- blood altar

	/**
	 * Pays max hearts (sneak-use: takes a curse instead) for a roll of the altar's legendary table. Paying a boon
	 * isn't offered: there is no boon-removal API (decision logged). Consumed on use.
	 */
	static @Nullable String useAltar(ServerPlayer player, ServerLevel level, BlockPos pos, boolean takeCurse) {
		MinecraftServer server = level.getServer();
		DirectorData data = DirectorData.get(server);
		int paid = data.altarHearts.getOrDefault(player.getUUID(), 0);
		int affordable = (int) Math.floor((player.getMaxHealth() - 2) / 2);
		int hearts = Math.min(config().altar.hearts, affordable);
		String price;
		if (takeCurse || hearts <= 0) {
			if (!takeCurse && !config().altar.curseWithoutBoons) {
				return "You have no hearts left to give";
			}
			Identifier curse = BuildHooks.applyRandomCurse(player);
			price = "a curse (" + curse + ")";
		} else {
			data.altarHearts.put(player.getUUID(), paid + hearts);
			data.setDirty();
			applyAltarHearts(player, paid + hearts);
			price = hearts + " heart(s)";
		}
		level.removeBlock(pos, false);
		int items = rollLoot(level, CommonIds.BLOOD_ALTAR_LOOT, pos, player);
		Director.log(server, DirectorLog.Kind.ECONOMY, player.getScoreboardName() + " paid the blood altar " + price + ": " + items + " item(s)");
		return null;
	}

	/** Sets the altar's max-health modifier to {@code hearts} paid (0 removes it). */
	static void applyAltarHearts(ServerPlayer player, int hearts) {
		AttributeInstance health = player.getAttribute(Attributes.MAX_HEALTH);
		if (health == null) {
			return;
		}
		health.removeModifier(ALTAR_MODIFIER);
		if (hearts > 0) {
			health.addPermanentModifier(new AttributeModifier(ALTAR_MODIFIER, -2.0 * hearts, AttributeModifier.Operation.ADD_VALUE));
		}
		if (player.getHealth() > player.getMaxHealth()) {
			player.setHealth(player.getMaxHealth());
		}
	}

	private static void clearAltarHearts(MinecraftServer server) {
		DirectorData data = DirectorData.get(server);
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			applyAltarHearts(player, 0);
		}
		data.altarHearts.clear();
		data.setDirty();
	}

	// ---------------------------------------------------------------- cursed chest

	/** Great loot, the next siege's modifiers up a tier, and a curse. Consumed on use. */
	static @Nullable String useCursedChest(ServerPlayer player, ServerLevel level, BlockPos pos) {
		MinecraftServer server = level.getServer();
		DirectorData data = DirectorData.get(server);
		level.removeBlock(pos, false);
		int items = rollLoot(level, CommonIds.CURSED_CHEST_LOOT, pos, player);
		data.pendingBumps += Math.max(0, config().cursedChest.modifierTiers);
		data.setDirty();
		Identifier curse = config().cursedChest.curse ? BuildHooks.applyRandomCurse(player) : null;
		for (ServerPlayer each : DirectorHooks.activePlayers(server)) {
			each.sendSystemMessage(Component.translatable("message.blocksouls_game.director.cursed_chest", player.getDisplayName()));
		}
		Director.log(server, DirectorLog.Kind.ECONOMY, player.getScoreboardName() + " opened a cursed chest: " + items + " item(s), next siege +"
				+ data.pendingBumps + " tier(s)" + (curse != null ? ", curse " + curse : ""));
		return null;
	}

	// ---------------------------------------------------------------- challenge pylon

	static @Nullable String startPylon(ServerPlayer player, ServerLevel level, BlockPos pos) {
		MinecraftServer server = level.getServer();
		if (pylon != null) {
			return "A challenge is already running";
		}
		pylon = new Pylon(level, pos.immutable(), Director.now(server));
		lastPylonPack = Director.now(server);
		for (ServerPlayer each : DirectorHooks.activePlayers(server)) {
			each.sendSystemMessage(Component.translatable("message.blocksouls_game.director.pylon_started", player.getDisplayName(),
					config().pylon.seconds));
		}
		Director.log(server, DirectorLog.Kind.ECONOMY, player.getScoreboardName() + " lit a challenge pylon at " + pos.toShortString());
		return null;
	}

	private static void tickPylon(MinecraftServer server, long now) {
		Pylon p = pylon;
		if (p == null) {
			return;
		}
		DirectorConfig.Pylon config = config().pylon;
		long elapsed = now - p.startedAt();
		double radius = DirectorRules.pylonRadius(elapsed, config);
		Vec3 centre = Vec3.atBottomCenterOf(p.pos());
		List<ServerPlayer> inside = DirectorHooks.activePlayers(server).stream()
				.filter(player -> player.level() == p.level() && Places.distanceSq(p.pos(), player.getX(), player.getZ()) <= radius * radius)
				.toList();
		for (int i = 0; i < 24; i++) {
			double angle = Math.PI * 2 * i / 24;
			p.level().sendParticles(ParticleTypes.SOUL_FIRE_FLAME, centre.x + Math.cos(angle) * radius, centre.y + 0.2, centre.z + Math.sin(angle) * radius,
					1, 0, 0, 0, 0);
		}
		if (inside.isEmpty()) {
			pylon = null;
			p.level().removeBlock(p.pos(), false);
			Director.log(server, DirectorLog.Kind.ECONOMY, "Challenge pylon failed: nobody stayed in the circle");
			Director.beat(server, "pylon failed");
			return;
		}
		if (elapsed >= config.seconds) {
			pylon = null;
			p.level().removeBlock(p.pos(), false);
			for (ServerPlayer player : inside) {
				if ("trinket".equals(config.reward)) {
					BuildHooks.offerTrinkets(player);
				} else {
					RelicOffers.dropRelic(player, BoonTier.byId(config.reward));
				}
			}
			Director.log(server, DirectorLog.Kind.ECONOMY, "Challenge pylon survived by " + inside.size() + ": " + config.reward + " reward");
			Director.beat(server, "pylon won");
			return;
		}
		if (now - lastPylonPack >= config.packEverySeconds) {
			lastPylonPack = now;
			int size = (int) Math.max(1, Math.round(config.pack * PartyScaling.directorBurst(inside.size())));
			int sent = Director.spawnPack(server, inside, size, null, 0);
			Director.log(server, DirectorLog.Kind.ECONOMY, "Pylon calls the horde: " + sent);
			Director.beat(server, "pylon pack");
		}
	}

	// ---------------------------------------------------------------- the soul wager

	static void onSiegeStarted(MinecraftServer server) {
		wagerOpenUntil = Director.now(server) + config().wager.windowSeconds;
	}

	static boolean wagerOpen(MinecraftServer server) {
		return Director.siegeActive && wagerOpenUntil >= 0 && Director.now(server) <= wagerOpenUntil;
	}

	/** Stakes souls on the siege: paid {@code payout}× when it completes, lost if the staker goes down. */
	static @Nullable String wager(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		DirectorData data = DirectorData.get(server);
		if (data.wagers.containsKey(player.getUUID())) {
			return "You already have a wager on this siege";
		}
		int stake = config().wager.stake;
		if (Souls.count(player) < stake) {
			return "The wager is " + stake + " souls";
		}
		Souls.take(player, stake);
		data.wagers.put(player.getUUID(), stake);
		data.setDirty();
		player.sendSystemMessage(Component.translatable("message.blocksouls_game.director.wager", stake, (int) Math.round(stake * config().wager.payout)));
		Director.log(server, DirectorLog.Kind.ECONOMY, player.getScoreboardName() + " wagered " + stake + " souls on the siege");
		return null;
	}

	static void onSiegeEnded(MinecraftServer server, boolean completed) {
		wagerOpenUntil = -1;
		DirectorData data = DirectorData.get(server);
		for (Map.Entry<UUID, Integer> wager : data.wagers.entrySet()) {
			ServerPlayer player = server.getPlayerList().getPlayer(wager.getKey());
			int payout = (int) Math.round(wager.getValue() * config().wager.payout);
			if (completed && player != null) {
				Souls.give(player, payout);
				player.sendSystemMessage(Component.translatable("message.blocksouls_game.director.wager_won", payout));
			}
			Director.log(server, DirectorLog.Kind.ECONOMY, "Wager " + wager.getKey() + (completed ? " paid " + payout : " lost"));
		}
		data.wagers.clear();
		data.setDirty();
	}

	// ---------------------------------------------------------------- shared

	static void second(MinecraftServer server, long now) {
		tickPylon(server, now);
		if (wagerOpenUntil >= 0 && now > wagerOpenUntil) {
			wagerOpenUntil = -1;
		}
	}

	/** Rolls {@code table} and drops the items at {@code pos}. Returns how many stacks dropped. */
	static int rollLoot(ServerLevel level, ResourceKey<LootTable> key, BlockPos pos, @Nullable ServerPlayer player) {
		LootTable table = level.getServer().reloadableRegistries().getLootTable(key);
		LootParams.Builder builder = new LootParams.Builder(level).withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(pos));
		if (player != null) {
			builder.withLuck(player.getLuck());
		}
		List<ItemStack> items = table.getRandomItems(builder.create(LootContextParamSets.CHEST));
		RandomSource random = level.getRandom();
		for (ItemStack stack : items) {
			if (player != null && player.addItem(stack) && stack.isEmpty()) {
				continue;
			}
			net.minecraft.world.Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5 + random.nextDouble() * 0.2, pos.getZ() + 0.5, stack);
		}
		return items.size();
	}
}
