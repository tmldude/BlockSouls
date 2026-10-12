package io.github.tmldude.blocksouls.game.player.rune;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

import io.github.tmldude.blocksouls.common.CommonIds;
import io.github.tmldude.blocksouls.common.Networking;
import io.github.tmldude.blocksouls.common.event.RunEvents;
import io.github.tmldude.blocksouls.common.player.Capabilities;
import io.github.tmldude.blocksouls.common.run.RunState;
import io.github.tmldude.blocksouls.game.BlockSoulsGame;
import io.github.tmldude.blocksouls.game.player.classes.ClassEffects;
import io.github.tmldude.blocksouls.game.player.classes.ClassesConfig;
import io.github.tmldude.blocksouls.game.player.classes.PlayerClass;
import io.github.tmldude.blocksouls.game.player.classes.PlayerClasses;

/**
 * Rune trees, the meta-progression (PLAN2 §2): per-class permanent unlocks bought with rune souls that the run
 * pays out at its end ({@link RunePayout}), kept per player across runs ({@link RuneProfiles}). While a player
 * plays a class, every unlocked rune of that class applies: its effects through the class effects layer (keyed by
 * the rune's id) and its movement verbs through {@code Capabilities.grant}.
 */
public final class PlayerRunes {
	private static RunesConfig config = new RunesConfig();

	private PlayerRunes() {
	}

	public static RunesConfig config() {
		return config;
	}

	public static void register(RunesConfig runesConfig) {
		config = runesConfig;
		RuneCatalog.register();
		Networking.registerClientbound(RunePayloads.Tree.TYPE, RunePayloads.Tree.CODEC);
		Networking.registerServerbound(RunePayloads.Request.TYPE, RunePayloads.Request.CODEC,
				(payload, context) -> sendTree(context.player(), payload.playerClass()));
		Networking.registerServerbound(RunePayloads.Unlock.TYPE, RunePayloads.Unlock.CODEC,
				(payload, context) -> onUnlock(context.player(), payload.rune()));

		ServerPlayConnectionEvents.JOIN.register((listener, sender, server) -> RuneProfiles.get(server, listener.player.getUUID()));
		ServerPlayConnectionEvents.DISCONNECT.register((listener, server) -> {
			RuneProfiles.save(server, listener.player.getUUID());
			RuneProfiles.evict(listener.player.getUUID());
		});
		RunEvents.RUN_OVER.register(PlayerRunes::onRunOver);
		// Traits: the class layer asks here what the runes want, and vice versa.
		ClassEffects.setOtherOwners(PlayerRunes::otherOwnersTraitLevel);
	}

	// ---------------------------------------------------------------- reading

	public static RuneProfile profile(ServerPlayer player) {
		return RuneProfiles.get(player.level().getServer(), player.getUUID());
	}

	/** The runes that apply to this player right now: unlocked ones of the class they play. */
	public static List<Rune> active(ServerPlayer player) {
		PlayerClass playerClass = PlayerClasses.get(player);
		if (playerClass == null) {
			return List.of();
		}
		RuneProfile profile = profile(player);
		return RuneCatalog.forClass(playerClass.id()).stream().filter(rune -> profile.has(rune.id())).toList();
	}

	/** Ability-mod ids from the active runes (for the abilities module, through a later contract). */
	public static List<String> abilityMods(ServerPlayer player) {
		return active(player).stream().flatMap(rune -> rune.abilityMods().stream()).toList();
	}

	public static int cost(Rune rune) {
		return (int) Math.round(rune.cost() * Math.max(0, config.costMultiplier));
	}

	// ---------------------------------------------------------------- applying

	/** Applies the active runes (idempotent; called from the class layer's apply). */
	public static void apply(ServerPlayer player, ClassesConfig classesConfig) {
		for (Rune rune : active(player)) {
			for (String verb : rune.capabilities()) {
				Capabilities.grant(player, Identifier.parse(verb));
			}
			ClassEffects.applyEffects(player, owner(rune), rune.effects(), classesConfig);
		}
	}

	/** Removes every rune's effects (the class is cleared; verbs come off with the class's grantAll). */
	public static void removeAll(ServerPlayer player) {
		for (Rune rune : active(player)) {
			ClassEffects.removeEffects(player, owner(rune), rune.effects());
		}
	}

	private static String owner(Rune rune) {
		Identifier id = Identifier.parse(rune.id());
		return "rune/" + id.getNamespace() + "/" + id.getPath();
	}

	private static int otherOwnersTraitLevel(ServerPlayer player, String excludingOwner, Identifier trait) {
		int level = 0;
		PlayerClass playerClass = PlayerClasses.get(player);
		if (playerClass != null && !ClassEffects.classOwner(playerClass).equals(excludingOwner)) {
			level += ClassEffects.traitLevel(playerClass.effects(), trait);
		}
		for (Rune rune : active(player)) {
			if (!owner(rune).equals(excludingOwner)) {
				level += ClassEffects.traitLevel(rune.effects(), trait);
			}
		}
		return level;
	}

	// ---------------------------------------------------------------- unlocking

	/** Why an unlock can't happen, or empty when it can. */
	public static Optional<String> blocker(ServerPlayer player, Rune rune) {
		RuneProfile profile = profile(player);
		if (profile.has(rune.id())) {
			return Optional.of("already unlocked");
		}
		if (!profile.canReach(rune)) {
			return Optional.of("requires " + String.join(", ", rune.requires()));
		}
		if (profile.souls < cost(rune)) {
			return Optional.of("needs " + cost(rune) + " rune souls, has " + profile.souls);
		}
		return Optional.empty();
	}

	/** Buys the rune for the player (prerequisites and souls checked), saves the profile and applies it. */
	public static boolean unlock(ServerPlayer player, Rune rune) {
		if (blocker(player, rune).isPresent()) {
			return false;
		}
		RuneProfile profile = profile(player);
		profile.souls -= cost(rune);
		profile.unlocked.add(rune.id());
		RuneProfiles.save(player.level().getServer(), player.getUUID());
		PlayerClasses.apply(player);
		player.level().playSound(null, player.blockPosition(), SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 0.7F, 1.2F);
		player.sendSystemMessage(Component.translatable("message.blocksouls_game.rune.unlocked", name(Identifier.parse(rune.id()))).withStyle(ChatFormatting.LIGHT_PURPLE));
		return true;
	}

	/** Debug: adds (or removes, when negative) rune souls. */
	public static int addSouls(ServerPlayer player, int amount) {
		RuneProfile profile = profile(player);
		profile.souls = Math.max(0, profile.souls + amount);
		RuneProfiles.save(player.level().getServer(), player.getUUID());
		return profile.souls;
	}

	/** Debug: forgets every unlock and the balance. */
	public static void reset(ServerPlayer player) {
		removeAll(player);
		RuneProfile profile = profile(player);
		profile.souls = 0;
		profile.unlocked.clear();
		RuneProfiles.save(player.level().getServer(), player.getUUID());
		PlayerClasses.apply(player);
	}

	private static void onUnlock(ServerPlayer player, Identifier runeId) {
		Rune rune = RuneCatalog.get(runeId);
		if (rune != null) {
			Optional<String> blocker = blocker(player, rune);
			if (blocker.isPresent()) {
				player.sendSystemMessage(Component.literal(blocker.get()).withStyle(ChatFormatting.RED));
			} else {
				unlock(player, rune);
			}
			sendTree(player, Identifier.parse(rune.playerClass()));
		}
	}

	/** Sends the class's tree (the sigil screen's Runes button, {@code /bs rune tree}). */
	public static void sendTree(ServerPlayer player, Identifier classId) {
		RuneProfile profile = profile(player);
		List<RunePayloads.RuneView> views = RuneCatalog.forClass(classId.toString()).stream()
				.map(rune -> new RunePayloads.RuneView(Identifier.parse(rune.id()), cost(rune),
						rune.requires().stream().map(Identifier::parse).toList(), profile.has(rune.id()), profile.canReach(rune),
						rune.capabilities().stream().sorted().map(Identifier::parse).toList()))
				.toList();
		Networking.sendToPlayer(player, new RunePayloads.Tree(classId, profile.souls, views));
	}

	// ---------------------------------------------------------------- the payout

	/**
	 * At run end: every online player banks the performance payout plus a share of their unspent souls (the
	 * soul items are taken; the run is over). Returns nothing; {@link #lastPayout} remembers it for the summary.
	 */
	private static void onRunOver(MinecraftServer server, RunState state) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			int unspent = takeSouls(player);
			int payout = RunePayout.payout(state.night(), state.threat(), unspent, config);
			RuneProfile profile = profile(player);
			profile.souls += payout;
			profile.soulsEarned += payout;
			profile.runsFinished++;
			RuneProfiles.save(server, player.getUUID());
			LAST_PAYOUT.put(player.getUUID(), payout);
			if (payout > 0) {
				player.sendSystemMessage(Component.translatable("message.blocksouls_game.rune.payout", payout, profile.souls).withStyle(ChatFormatting.LIGHT_PURPLE));
			}
		}
	}

	private static final java.util.Map<UUID, Integer> LAST_PAYOUT = new java.util.HashMap<>();

	/** The rune souls the player was paid at the last run end (for the summary card), 0 if none. */
	public static int lastPayout(UUID player) {
		return LAST_PAYOUT.getOrDefault(player, 0);
	}

	/** Counts and removes the player's soul items ({@code blocksouls_game:soul}, owned by the builds session). */
	static int takeSouls(ServerPlayer player) {
		Item soul = BuiltInRegistries.ITEM.getValue(CommonIds.SOUL);
		if (soul == null || soul == net.minecraft.world.item.Items.AIR) {
			return 0;
		}
		int count = 0;
		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			ItemStack stack = player.getInventory().getItem(i);
			if (stack.is(soul)) {
				count += stack.getCount();
				player.getInventory().setItem(i, ItemStack.EMPTY);
			}
		}
		if (count > 0) {
			player.inventoryMenu.broadcastChanges();
			BlockSoulsGame.LOGGER.info("{} carried {} unspent souls into the rune payout", player.getPlainTextName(), count);
		}
		return count;
	}

	public static Component name(Identifier id) {
		return Component.translatable("rune." + id.getNamespace() + "." + id.getPath());
	}
}
