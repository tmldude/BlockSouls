package io.github.tmldude.blocksouls.game.player.downed;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.mojang.serialization.Codec;
import org.jspecify.annotations.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

import io.github.tmldude.blocksouls.common.event.PlayerRunEvents;
import io.github.tmldude.blocksouls.common.event.RunEvents;
import io.github.tmldude.blocksouls.common.player.PlayerStatus;
import io.github.tmldude.blocksouls.common.run.Runs;
import io.github.tmldude.blocksouls.game.BlockSoulsGame;
import io.github.tmldude.blocksouls.game.player.PlayersConfig;
import io.github.tmldude.blocksouls.game.player.boon.PlayerBoons;
import io.github.tmldude.blocksouls.game.run.RunSystem;

/**
 * Downed, not dead: lethal damage during a run puts a player down instead of killing them. Downed players are
 * invulnerable, crawl, can't act, and stay down until a teammate revives them ({@link Revive}). Everyone down at
 * once ends the run.
 */
public final class Downed {
	private static final Identifier SPEED_MODIFIER = Identifier.fromNamespaceAndPath(BlockSoulsGame.MOD_ID, "downed_speed");
	private static final Identifier JUMP_MODIFIER = Identifier.fromNamespaceAndPath(BlockSoulsGame.MOD_ID, "downed_jump");

	/** Survives logout and restarts so a player who leaves while down is still down when they come back. */
	private static final AttachmentType<Boolean> WAS_DOWNED = AttachmentRegistry.create(
			Identifier.fromNamespaceAndPath(BlockSoulsGame.MOD_ID, "was_downed"),
			builder -> builder.persistent(Codec.BOOL));

	/** Players that count as in an active run regardless of run state and game mode (gametests). */
	private static final Set<UUID> FORCED = ConcurrentHashMap.newKeySet();

	private static PlayersConfig.Downed config;
	private static int wipeCheckTimer;

	private Downed() {
	}

	public static void register(PlayersConfig.Downed downedConfig) {
		config = downedConfig;

		ServerLivingEntityEvents.ALLOW_DEATH.register(Downed::allowDeath);
		ServerLivingEntityEvents.ALLOW_DAMAGE.register(Downed::allowDamage);
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof ServerPlayer player) {
				clearDowned(player);
				PlayerBoons.clearAll(player);
			}
		});

		AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> blocked(player));
		AttackBlockCallback.EVENT.register((player, level, hand, pos, direction) -> blocked(player));
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> blocked(player));
		UseItemCallback.EVENT.register((player, level, hand) ->
				PlayerStatus.isDowned(player) && DownedPlay.mayUseWhileDowned(player.getItemInHand(hand)) ? InteractionResult.PASS : blocked(player));
		UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> blocked(player));
		PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, blockEntity) -> !PlayerStatus.isDowned(player));

		ServerPlayConnectionEvents.JOIN.register((listener, sender, server) -> onJoin(listener.player));
		ServerTickEvents.END_SERVER_TICK.register(Downed::tick);

		// A run ending or starting stands everyone up; a new run also starts without boons.
		RunEvents.RUN_OVER.register((server, state) -> realPlayers(server).forEach(Downed::clearDowned));
		RunEvents.RUN_STARTED.register((server, state) -> realPlayers(server).forEach(player -> {
			clearDowned(player);
			PlayerBoons.clearAll(player);
		}));
	}

	private static InteractionResult blocked(Player player) {
		return PlayerStatus.isDowned(player) ? InteractionResult.FAIL : InteractionResult.PASS;
	}

	/** Whether lethal damage would down this player rather than kill them. */
	public static boolean canGoDown(ServerPlayer player) {
		if (FORCED.contains(player.getUUID())) {
			return true;
		}
		return !player.isCreative() && !player.isSpectator() && Runs.get(player.level().getServer()).isActive();
	}

	private static boolean allowDeath(LivingEntity entity, DamageSource source, float amount) {
		if (!(entity instanceof ServerPlayer player) || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)
				|| PlayerStatus.isDowned(player) || !canGoDown(player)) {
			return true;
		}

		goDown(player);
		return false;
	}

	private static boolean allowDamage(LivingEntity entity, DamageSource source, float amount) {
		return !(entity instanceof ServerPlayer player) || !PlayerStatus.isDowned(player)
				|| source.is(DamageTypeTags.BYPASSES_INVULNERABILITY);
	}

	/** Puts the player down: health 1, crawling, boons lost, {@code DOWNED} fired, then checks for a team wipe. */
	public static void goDown(ServerPlayer player) {
		if (PlayerStatus.isDowned(player)) {
			return;
		}

		player.setHealth(1.0F);
		PlayerStatus.setDowned(player, true);
		((AttachmentTarget) player).setAttached(WAS_DOWNED, Boolean.TRUE);
		player.stopRiding();
		player.stopUsingItem();
		player.clearFire();
		player.setSprinting(false);
		applyModifiers(player);
		Revive.cancelAttemptsBy(player);
		PlayerBoons.clearAll(player);

		player.level().playSound(null, player.blockPosition(), SoundEvents.PLAYER_HURT, SoundSource.PLAYERS, 1.0F, 0.6F);
		player.level().getServer().getPlayerList().broadcastSystemMessage(
				Component.translatable("message.blocksouls_game.downed", player.getDisplayName()).withStyle(ChatFormatting.RED), false);
		PlayerRunEvents.DOWNED.invoker().onPlayer(player);

		checkWipe(player.level().getServer(), player);
	}

	/** Gets the player back up. {@code reviver} is null for admin revives. */
	public static void revive(ServerPlayer player, @Nullable ServerPlayer reviver) {
		if (!PlayerStatus.isDowned(player)) {
			return;
		}

		clearDowned(player);
		player.setHealth(Math.min(config.reviveHealth, player.getMaxHealth()));
		player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, config.reviveResistanceTicks, config.reviveResistanceAmplifier));
		player.level().playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.8F, 1.4F);
		PlayerRunEvents.REVIVED.invoker().onRevived(player, reviver);
	}

	private static void clearDowned(ServerPlayer player) {
		((AttachmentTarget) player).removeAttached(WAS_DOWNED);
		Revive.cancelAttemptsOn(player);
		if (PlayerStatus.isDowned(player)) {
			PlayerStatus.setDowned(player, false);
		}
		removeModifiers(player);
	}

	private static void onJoin(ServerPlayer player) {
		if (!Boolean.TRUE.equals(((AttachmentTarget) player).getAttached(WAS_DOWNED))) {
			return;
		}
		if (canGoDown(player)) {
			PlayerStatus.setDowned(player, true);
			applyModifiers(player);
		} else {
			clearDowned(player);
		}
	}

	private static void tick(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (PlayerStatus.isDowned(player)) {
				if (player.isPassenger()) {
					player.stopRiding();
				}
				if (player.isSprinting()) {
					player.setSprinting(false);
				}
				applyModifiers(player);
			}
		}

		if (++wipeCheckTimer >= Math.max(1, config.wipeCheckIntervalTicks)) {
			wipeCheckTimer = 0;
			checkWipe(server, null);
		}
	}

	/**
	 * Ends the run if everyone who counts is down, unless a solo player's Last Stand takes over ({@code justDowned}
	 * is the player who went down this call, null on the periodic check). Players forced in by gametests never end
	 * a real run.
	 */
	private static void checkWipe(MinecraftServer server, @Nullable ServerPlayer justDowned) {
		if (!Runs.get(server).isActive()) {
			return;
		}
		Collection<ServerPlayer> players = realPlayers(server);
		if (!isTeamWiped(players)) {
			return;
		}
		if (LastStand.isActive()) {
			return;
		}
		int counting = (int) players.stream().filter(Downed::canGoDown).count();
		ServerPlayer soloist = justDowned != null && !FORCED.contains(justDowned.getUUID()) ? justDowned
				: players.stream().filter(Downed::canGoDown).findFirst().orElse(null);
		if (soloist != null && LastStand.tryStart(soloist, counting)) {
			return;
		}
		BlockSoulsGame.LOGGER.info("Everyone is down: ending the run");
		RunSystem.endRun(server);
	}

	/** Online players, minus mocks that gametests forced into the run (they never touch a real run). */
	private static Collection<ServerPlayer> realPlayers(MinecraftServer server) {
		return server.getPlayerList().getPlayers().stream().filter(player -> !FORCED.contains(player.getUUID())).toList();
	}

	/** True if at least one of {@code players} counts toward a wipe and every one who counts is down. */
	public static boolean isTeamWiped(Collection<ServerPlayer> players) {
		return TeamWipe.isWipe(players, Downed::canGoDown, PlayerStatus::isDowned);
	}

	private static void applyModifiers(ServerPlayer player) {
		addModifier(player.getAttribute(Attributes.MOVEMENT_SPEED), SPEED_MODIFIER, config.speedPenalty);
		addModifier(player.getAttribute(Attributes.JUMP_STRENGTH), JUMP_MODIFIER, -1.0);
	}

	private static void addModifier(@Nullable AttributeInstance attribute, Identifier id, double amount) {
		// Update a stale amount too, so a config reload applies without getting up and going down again.
		AttributeModifier current = attribute != null ? attribute.getModifier(id) : null;
		if (attribute != null && (current == null || current.amount() != amount)) {
			attribute.addOrUpdateTransientModifier(new AttributeModifier(id, amount, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
		}
	}

	private static void removeModifiers(ServerPlayer player) {
		AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
		if (speed != null) {
			speed.removeModifier(SPEED_MODIFIER);
		}
		AttributeInstance jump = player.getAttribute(Attributes.JUMP_STRENGTH);
		if (jump != null) {
			jump.removeModifier(JUMP_MODIFIER);
		}
	}

	/** Gametests: treat this player as in an active run in survival, whatever the run state and game mode. */
	public static void forceInRun(ServerPlayer player) {
		FORCED.add(player.getUUID());
	}

	/** Forces class loading so the attachment registers during init. */
	public static void init() {
	}
}
