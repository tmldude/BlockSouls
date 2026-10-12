package io.github.tmldude.blocksouls.game.player.origin;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

import io.github.tmldude.blocksouls.common.Networking;
import io.github.tmldude.blocksouls.common.event.BossEvents;
import io.github.tmldude.blocksouls.common.event.PlayerMoveEvents;
import io.github.tmldude.blocksouls.common.event.PlayerRunEvents;
import io.github.tmldude.blocksouls.common.event.RunEvents;
import io.github.tmldude.blocksouls.common.player.Capabilities;
import io.github.tmldude.blocksouls.common.run.Runs;
import io.github.tmldude.blocksouls.common.synergy.SynergyTags;
import io.github.tmldude.blocksouls.game.BlockSoulsGame;

/**
 * The origin a player holds: picked with the Origin Sigil at run start (or {@code /bs origin set}), saved with the
 * player and kept across deaths. Picking restricts the player's movement verbs to the origin's signature
 * ({@code Capabilities.restrictTo}), hands the kit over once, and applies passives and drawbacks as a maintained
 * layer ({@link OriginEffects}). No origin = the default all-granted v1 player; with {@code origins.enabled} off
 * nothing here runs on its own (the commands still work for testing).
 */
public final class PlayerOrigins {
	private static final AttachmentType<OriginState> TYPE = AttachmentRegistry.create(
			Identifier.fromNamespaceAndPath(BlockSoulsGame.MOD_ID, "origin"),
			builder -> builder.persistent(OriginState.CODEC)
					.copyOnDeath()
					.syncWith(ByteBufCodecs.fromCodecWithRegistries(OriginState.CODEC), AttachmentSyncPredicate.targetOnly()));

	private static OriginsConfig config = new OriginsConfig();
	private static int reapplyTimer;

	private PlayerOrigins() {
	}

	public static OriginsConfig config() {
		return config;
	}

	public static void register(OriginsConfig originsConfig) {
		config = originsConfig;
		OriginSigilItem.register();
		OriginCatalog.register();
		Networking.registerClientbound(OriginPayloads.Offer.TYPE, OriginPayloads.Offer.CODEC);
		Networking.registerServerbound(OriginPayloads.Choose.TYPE, OriginPayloads.Choose.CODEC,
				(payload, context) -> onChoose(context.player(), payload.origin()));

		// A maintained layer: put the origin's share back after everything that clears effects or traits.
		ServerPlayConnectionEvents.JOIN.register((listener, sender, server) -> {
			apply(listener.player);
			if (config.enabled && config.giveSigilAtRunStart && Runs.get(server).isActive() && get(listener.player) == null) {
				giveSigil(listener.player);
			}
		});
		PlayerRunEvents.DOWNED.register(PlayerOrigins::apply);
		PlayerRunEvents.REVIVED.register((player, reviver) -> apply(player));
		PlayerRunEvents.BOON_GRANTED.register((player, boon) -> apply(player));
		ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, resourceManager, success) -> server.getPlayerList().getPlayers().forEach(PlayerOrigins::apply));
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (++reapplyTimer >= Math.max(1, config.reapplyIntervalTicks)) {
				reapplyTimer = 0;
				server.getPlayerList().getPlayers().forEach(PlayerOrigins::apply);
			}
		});

		// A new run is a new pick: origins come off, sigils go out, solo gets its default.
		RunEvents.RUN_STARTED.register((server, state) -> onRunStarted(server));

		BossEvents.KILLED.register((level, bossId, boss, players) -> players.forEach(PlayerOrigins::onBossKill));
		PlayerMoveEvents.ROLLED.register(PlayerOrigins::onRoll);
		SynergyTags.addSource(PlayerOrigins::collectTags);
	}

	// ---------------------------------------------------------------- reading

	/** The player's origin, or null (the all-granted v1 player). Both sides for the local player. */
	public static @Nullable Origin get(Player player) {
		OriginState state = ((AttachmentTarget) player).getAttached(TYPE);
		return state == null ? null : OriginCatalog.get(state.origin());
	}

	public static Optional<Identifier> id(Player player) {
		OriginState state = ((AttachmentTarget) player).getAttached(TYPE);
		return state == null ? Optional.empty() : Optional.of(Identifier.parse(state.origin()));
	}

	/** The origin's rule tweaks, or the neutral rules without an origin. Other systems (revive, heal, relics) read this. */
	public static OriginRules rules(Player player) {
		Origin origin = get(player);
		return origin == null ? OriginRules.NEUTRAL : origin.rules();
	}

	private static @Nullable OriginState state(Player player) {
		return ((AttachmentTarget) player).getAttached(TYPE);
	}

	// ---------------------------------------------------------------- choosing

	/**
	 * Gives the player this origin: clears the old one, restricts the movement verbs to the signature, hands the
	 * kit over (once per origin per run) and applies the effects. Returns false for an unknown origin.
	 */
	public static boolean choose(ServerPlayer player, Identifier originId) {
		Origin origin = OriginCatalog.get(originId);
		if (origin == null) {
			return false;
		}
		OriginState old = state(player);
		Origin previous = get(player);
		if (previous != null) {
			OriginEffects.remove(player, previous);
		}
		OriginState state = old == null ? new OriginState(origin.id(), Set.of(), 0) : old.withOrigin(origin.id());
		Capabilities.restrictTo(player, origin.capabilities().stream().map(Identifier::parse).collect(Collectors.toSet()));
		if (!state.kitsGiven().contains(origin.id())) {
			OriginKits.give(player, origin);
			state = state.withKitGiven(origin.id());
		}
		((AttachmentTarget) player).setAttached(TYPE, state);
		OriginEffects.apply(player, origin, state, config);
		player.level().playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.7F, 0.9F);
		player.sendSystemMessage(Component.translatable("message.blocksouls_game.origin.chosen", name(originId)).withStyle(ChatFormatting.GOLD));
		return true;
	}

	/** Back to the all-granted v1 player: effects off, every verb back. */
	public static void clear(ServerPlayer player) {
		Origin origin = get(player);
		if (origin != null) {
			OriginEffects.remove(player, origin);
		}
		((AttachmentTarget) player).removeAttached(TYPE);
		Capabilities.grantAll(player);
	}

	/** Re-applies the origin's effects (idempotent). Repairs a lost capability set; never undoes cross-training grants. */
	public static void apply(ServerPlayer player) {
		OriginState state = state(player);
		if (state == null) {
			return;
		}
		Origin origin = OriginCatalog.get(state.origin());
		if (origin == null) {
			return;
		}
		if (Capabilities.allGranted(player)) {
			Capabilities.restrictTo(player, origin.capabilities().stream().map(Identifier::parse).collect(Collectors.toSet()));
		}
		OriginEffects.apply(player, origin, state, config);
	}

	/** Opens the select screen on the player's client (the sigil, or {@code /bs origin select}). */
	public static void openSelect(ServerPlayer player) {
		List<OriginPayloads.OriginView> views = OriginCatalog.all().stream().map(OriginPayloads.OriginView::of).toList();
		if (views.isEmpty()) {
			player.sendSystemMessage(Component.translatable("message.blocksouls_game.origin.none").withStyle(ChatFormatting.RED));
			return;
		}
		Networking.sendToPlayer(player, new OriginPayloads.Offer(views, id(player)));
	}

	private static void onChoose(ServerPlayer player, Identifier originId) {
		if (OriginCatalog.get(originId) == null) {
			return;
		}
		boolean paid = takeSigil(player);
		if (!paid && !player.hasInfiniteMaterials()) {
			// The screen was opened by something other than a sigil (a command): the pick still stands.
			BlockSoulsGame.LOGGER.info("{} picked origin {} without a sigil", player.getPlainTextName(), originId);
		}
		choose(player, originId);
	}

	private static boolean takeSigil(ServerPlayer player) {
		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			ItemStack stack = player.getInventory().getItem(i);
			if (stack.is(OriginSigilItem.INSTANCE)) {
				stack.shrink(1);
				return true;
			}
		}
		return false;
	}

	public static void giveSigil(ServerPlayer player) {
		if (OriginSigilItem.INSTANCE == null) {
			return;
		}
		ItemStack stack = new ItemStack(OriginSigilItem.INSTANCE);
		if (!player.addItem(stack) && !stack.isEmpty()) {
			player.spawnAtLocation(player.level(), stack);
		}
	}

	// ---------------------------------------------------------------- run events

	private static void onRunStarted(MinecraftServer server) {
		List<ServerPlayer> players = server.getPlayerList().getPlayers();
		for (ServerPlayer player : players) {
			clear(player);
		}
		if (!config.enabled) {
			return;
		}
		boolean solo = players.size() == 1;
		for (ServerPlayer player : players) {
			if (solo && config.soloAutoPick) {
				Identifier fallback = Identifier.tryParse(config.soloDefault);
				if (fallback == null || !choose(player, fallback)) {
					BlockSoulsGame.LOGGER.warn("origins.soloDefault {} isn't a known origin", config.soloDefault);
				}
			}
			if (config.giveSigilAtRunStart) {
				giveSigil(player);
			}
		}
	}

	private static void onBossKill(ServerPlayer player) {
		OriginState state = state(player);
		Origin origin = get(player);
		if (state == null || origin == null || origin.rules().heartsPerBossKill() <= 0) {
			return;
		}
		state = state.withBossKills(state.bossKills() + 1);
		((AttachmentTarget) player).setAttached(TYPE, state);
		OriginEffects.apply(player, origin, state, config);
	}

	/** Bloodbound: the roll leaves a short, blinding blood mist behind. */
	private static void onRoll(ServerPlayer player) {
		Origin origin = get(player);
		if (origin == null || !origin.rules().rollMist()) {
			return;
		}
		ServerLevel level = player.level();
		AreaEffectCloud mist = new AreaEffectCloud(level, player.getX(), player.getY(), player.getZ());
		mist.setOwner(player);
		mist.setRadius((float) config.rollMistRadius);
		mist.setRadiusPerTick(0);
		mist.setDuration(Math.max(1, config.rollMistDurationTicks));
		mist.setCustomParticle(ParticleTypes.CRIMSON_SPORE);
		mist.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, Math.max(1, config.rollMistBlindnessTicks), 0, false, true));
		level.addFreshEntity(mist);
	}

	/** The origin's weighted synergy tags count as part of the player's build (relic offers weight toward them). */
	private static void collectTags(Player player, java.util.function.BiConsumer<Identifier, Integer> sink) {
		Origin origin = get(player);
		if (origin != null) {
			origin.synergyTags().forEach((tag, weight) -> sink.accept(Identifier.parse(tag), weight));
		}
	}

	/** The origin's display name from the lang file ({@code origin.<ns>.<path>}). */
	public static Component name(Identifier id) {
		return Component.translatable("origin." + id.getNamespace() + "." + id.getPath());
	}

	/** Forces class loading so the attachment registers during init. */
	public static void init() {
	}
}
