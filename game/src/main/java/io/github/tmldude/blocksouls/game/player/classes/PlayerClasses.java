package io.github.tmldude.blocksouls.game.player.classes;

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
import io.github.tmldude.blocksouls.common.player.Origins;
import io.github.tmldude.blocksouls.common.run.Runs;
import io.github.tmldude.blocksouls.common.synergy.SynergyTags;
import io.github.tmldude.blocksouls.game.BlockSoulsGame;
import io.github.tmldude.blocksouls.game.player.rune.PlayerRunes;
import io.github.tmldude.blocksouls.game.player.rune.Rune;

/**
 * The class a player plays this run (PLAN2 §2/§3f: Warden, Striker, Arcanist, Ranger): picked with the Class Sigil
 * at run start (or {@code /bs class set}), saved with the player and kept across deaths. Everyone keeps the
 * universal v1 movement kit; picking <em>adds</em> the class's signature verbs ({@code Capabilities.grant}), hands
 * the kit over once, and applies passives and drawbacks as a maintained layer ({@link ClassEffects}). Rune unlocks
 * layer on top. No class = the plain v1 player; with {@code classes.enabled} off nothing here runs on its own (the
 * commands still work for testing).
 */
public final class PlayerClasses {
	private static final AttachmentType<ClassState> TYPE = AttachmentRegistry.create(
			Identifier.fromNamespaceAndPath(BlockSoulsGame.MOD_ID, "player_class"),
			builder -> builder.persistent(ClassState.CODEC)
					.copyOnDeath()
					.syncWith(ByteBufCodecs.fromCodecWithRegistries(ClassState.CODEC), AttachmentSyncPredicate.targetOnly()));

	private static ClassesConfig config = new ClassesConfig();
	private static int reapplyTimer;

	private PlayerClasses() {
	}

	public static ClassesConfig config() {
		return config;
	}

	public static void register(ClassesConfig originsConfig) {
		config = originsConfig;
		ClassSigilItem.register();
		ClassCatalog.register();
		Networking.registerClientbound(ClassPayloads.Offer.TYPE, ClassPayloads.Offer.CODEC);
		Networking.registerServerbound(ClassPayloads.Choose.TYPE, ClassPayloads.Choose.CODEC,
				(payload, context) -> onChoose(context.player(), payload.playerClass()));

		// A maintained layer: put the class's share back after everything that clears effects or traits.
		ServerPlayConnectionEvents.JOIN.register((listener, sender, server) -> {
			apply(listener.player);
			if (config.enabled && config.giveSigilAtRunStart && Runs.get(server).isActive() && get(listener.player) == null) {
				giveSigil(listener.player);
			}
		});
		PlayerRunEvents.DOWNED.register(PlayerClasses::apply);
		PlayerRunEvents.REVIVED.register((player, reviver) -> apply(player));
		PlayerRunEvents.BOON_GRANTED.register((player, boon) -> apply(player));
		ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, resourceManager, success) -> server.getPlayerList().getPlayers().forEach(PlayerClasses::apply));
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (++reapplyTimer >= Math.max(1, config.reapplyIntervalTicks)) {
				reapplyTimer = 0;
				server.getPlayerList().getPlayers().forEach(PlayerClasses::apply);
			}
		});

		// A new run is a new pick: classes come off, sigils go out, solo gets its default.
		RunEvents.RUN_STARTED.register((server, state) -> onRunStarted(server));

		BossEvents.KILLED.register((level, bossId, boss, players) -> players.forEach(PlayerClasses::onBossKill));
		PlayerMoveEvents.ROLLED.register(PlayerClasses::onRoll);
		SynergyTags.addSource(PlayerClasses::collectTags);
		// common's Origins contract carries the class id (the abilities module binds the signature ability by it).
		// The attachment syncs to its own player, so this answers on the local client too.
		Origins.setProvider(player -> id(player).orElse(null));
	}

	// ---------------------------------------------------------------- reading

	/** The player's class, or null (the all-granted v1 player). Both sides for the local player. */
	public static @Nullable PlayerClass get(Player player) {
		ClassState state = ((AttachmentTarget) player).getAttached(TYPE);
		return state == null ? null : ClassCatalog.get(state.classId());
	}

	public static Optional<Identifier> id(Player player) {
		ClassState state = ((AttachmentTarget) player).getAttached(TYPE);
		return state == null ? Optional.empty() : Optional.of(Identifier.parse(state.classId()));
	}

	/** The class's rule tweaks, or the neutral rules without a class. Other systems (revive, heal, relics) read this. */
	public static ClassRules rules(Player player) {
		PlayerClass playerClass = get(player);
		return playerClass == null ? ClassRules.NEUTRAL : playerClass.rules();
	}

	private static @Nullable ClassState state(Player player) {
		return ((AttachmentTarget) player).getAttached(TYPE);
	}

	// ---------------------------------------------------------------- choosing

	/**
	 * Gives the player this class: clears the old one, restricts the movement verbs to the signature, hands the
	 * kit over (once per class per run) and applies the effects. Returns false for an unknown class.
	 */
	public static boolean choose(ServerPlayer player, Identifier originId) {
		PlayerClass playerClass = ClassCatalog.get(originId);
		if (playerClass == null) {
			return false;
		}
		ClassState old = state(player);
		PlayerClass previous = get(player);
		List<Rune> previousRunes = PlayerRunes.active(player);
		ClassState state = old == null ? new ClassState(playerClass.id(), Set.of(), 0) : old.withClass(playerClass.id());
		if (!state.kitsGiven().contains(playerClass.id())) {
			ClassKits.give(player, playerClass);
			state = state.withKitGiven(playerClass.id());
		}
		// Switch first, then take the old layer off: trait removal recounts every *current* owner's share.
		((AttachmentTarget) player).setAttached(TYPE, state);
		if (previous != null) {
			PlayerRunes.removeEffects(player, previousRunes);
			ClassEffects.remove(player, previous);
			// The old signature's and runes' verbs go; the new class and its runes re-grant theirs below.
			Capabilities.grantAll(player);
		}
		apply(player);
		player.level().playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.7F, 0.9F);
		player.sendSystemMessage(Component.translatable("message.blocksouls_game.class.chosen", name(originId)).withStyle(ChatFormatting.GOLD));
		return true;
	}

	/** Back to the plain v1 player: effects off, the signature's extra verbs gone (the v1 kit was never touched). */
	public static void clear(ServerPlayer player) {
		PlayerClass playerClass = get(player);
		List<Rune> runes = PlayerRunes.active(player);
		// Detach first: trait removal recounts every current owner's share, and none should be left.
		((AttachmentTarget) player).removeAttached(TYPE);
		if (playerClass != null) {
			PlayerRunes.removeEffects(player, runes);
			ClassEffects.remove(player, playerClass);
		}
		Capabilities.grantAll(player);
	}

	/** Re-applies the class's effects and signature verbs (idempotent). Never removes a verb something else granted. */
	public static void apply(ServerPlayer player) {
		ClassState state = state(player);
		if (state == null) {
			return;
		}
		PlayerClass playerClass = ClassCatalog.get(state.classId());
		if (playerClass == null) {
			return;
		}
		grantSignature(player, playerClass);
		ClassEffects.apply(player, playerClass, state, config);
		PlayerRunes.apply(player, config);
	}

	/** The class's signature verbs, added on top of whatever the player already holds (the v1 kit at least). */
	private static void grantSignature(ServerPlayer player, PlayerClass playerClass) {
		for (String verb : playerClass.capabilities()) {
			Capabilities.grant(player, Identifier.parse(verb));
		}
	}

	/** Opens the select screen on the player's client (the sigil, or {@code /bs class select}). */
	public static void openSelect(ServerPlayer player) {
		List<ClassPayloads.ClassView> views = ClassCatalog.all().stream().map(ClassPayloads.ClassView::of).toList();
		if (views.isEmpty()) {
			player.sendSystemMessage(Component.translatable("message.blocksouls_game.class.none").withStyle(ChatFormatting.RED));
			return;
		}
		Networking.sendToPlayer(player, new ClassPayloads.Offer(views, id(player)));
	}

	private static void onChoose(ServerPlayer player, Identifier originId) {
		if (ClassCatalog.get(originId) == null) {
			return;
		}
		boolean paid = takeSigil(player);
		if (!paid && !player.hasInfiniteMaterials()) {
			// The screen was opened by something other than a sigil (a command): the pick still stands.
			BlockSoulsGame.LOGGER.info("{} picked class {} without a sigil", player.getPlainTextName(), originId);
		}
		choose(player, originId);
	}

	private static boolean takeSigil(ServerPlayer player) {
		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			ItemStack stack = player.getInventory().getItem(i);
			if (stack.is(ClassSigilItem.INSTANCE)) {
				stack.shrink(1);
				return true;
			}
		}
		return false;
	}

	public static void giveSigil(ServerPlayer player) {
		if (ClassSigilItem.INSTANCE == null) {
			return;
		}
		ItemStack stack = new ItemStack(ClassSigilItem.INSTANCE);
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
					BlockSoulsGame.LOGGER.warn("classes.soloDefault {} isn't a known class", config.soloDefault);
				}
			}
			if (config.giveSigilAtRunStart) {
				giveSigil(player);
			}
		}
	}

	private static void onBossKill(ServerPlayer player) {
		ClassState state = state(player);
		PlayerClass playerClass = get(player);
		if (state == null || playerClass == null || playerClass.rules().heartsPerBossKill() <= 0) {
			return;
		}
		state = state.withBossKills(state.bossKills() + 1);
		((AttachmentTarget) player).setAttached(TYPE, state);
		ClassEffects.apply(player, playerClass, state, config);
	}

	/** Bloodbound: the roll leaves a short, blinding blood mist behind. */
	private static void onRoll(ServerPlayer player) {
		PlayerClass playerClass = get(player);
		if (playerClass == null || !playerClass.rules().rollMist()) {
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

	/** The class's weighted synergy tags count as part of the player's build (relic offers weight toward them). */
	private static void collectTags(Player player, java.util.function.BiConsumer<Identifier, Integer> sink) {
		PlayerClass playerClass = get(player);
		if (playerClass != null) {
			playerClass.synergyTags().forEach((tag, weight) -> sink.accept(Identifier.parse(tag), weight));
		}
	}

	/** The class's display name from the lang file ({@code class.<ns>.<path>}). */
	public static Component name(Identifier id) {
		return Component.translatable("class." + id.getNamespace() + "." + id.getPath());
	}

	/** Forces class loading so the attachment registers during init. */
	public static void init() {
	}
}
