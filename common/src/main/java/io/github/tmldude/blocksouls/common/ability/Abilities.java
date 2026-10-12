package io.github.tmldude.blocksouls.common.ability;

import org.jspecify.annotations.Nullable;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;

import io.github.tmldude.blocksouls.common.CommonIds;
import io.github.tmldude.blocksouls.common.Networking;
import io.github.tmldude.blocksouls.common.event.AbilityEvents;

/**
 * The per-player ability attachment: equipped abilities, cooldowns and ultimate charge (see {@link AbilityState}).
 * Saved with the player, copied on death (an origin's kit isn't lost with its boons) and synced to that player's
 * client. The abilities module writes it (casts, charge); HUDs and other modules read it.
 *
 * <p>Clocks: cooldowns are stamped in the level's game time, {@link #now(Player)}.
 */
public final class Abilities {
	private static final AttachmentType<AbilityState> TYPE = AttachmentRegistry.create(
			CommonIds.id("blocksouls_common", "abilities"),
			builder -> builder.persistent(AbilityState.CODEC)
					.copyOnDeath()
					.syncWith(AbilityState.STREAM_CODEC, AttachmentSyncPredicate.targetOnly()));

	private Abilities() {
	}

	/** Works on both sides for the local player. */
	public static AbilityState state(Player player) {
		AbilityState state = ((AttachmentTarget) player).getAttached(TYPE);
		return state == null ? AbilityState.EMPTY : state;
	}

	/** Server side. */
	public static void set(Player player, AbilityState state) {
		((AttachmentTarget) player).setAttached(TYPE, state.isEmpty() ? null : state);
	}

	/** The level's game time, the clock cooldowns are stamped in. */
	public static long now(Player player) {
		return player.level().getGameTime();
	}

	public static @Nullable Identifier equipped(Player player, AbilitySlot slot) {
		return state(player).equipped().get(slot);
	}

	/** The definition of what's in the slot, or {@code null} when the slot is empty or holds an unknown id. */
	public static @Nullable AbilityDefinition definition(Player player, AbilitySlot slot) {
		return AbilityRegistry.get(equipped(player, slot));
	}

	/** Server side: puts {@code id} (or nothing, for {@code null}) in the slot. Keeps the slot's cooldown. */
	public static void equip(Player player, AbilitySlot slot, @Nullable Identifier id) {
		set(player, state(player).withEquipped(slot, id));
	}

	public static AbilityState.@Nullable Cooldown cooldown(Player player, AbilitySlot slot) {
		return state(player).cooldowns().get(slot);
	}

	/** Ticks until the slot's cooldown ends; 0 when ready or never cast. */
	public static long remainingTicks(Player player, AbilitySlot slot) {
		AbilityState.Cooldown cooldown = cooldown(player, slot);
		return cooldown == null ? 0 : cooldown.remaining(now(player));
	}

	/**
	 * Whether the slot can be cast now: it holds a known ability and its cooldown has ended, or, for a kill-charged
	 * ability, the ultimate charge is full.
	 */
	public static boolean isReady(Player player, AbilitySlot slot) {
		AbilityDefinition definition = definition(player, slot);
		if (definition == null) {
			return false;
		}
		if (definition.charge() == ChargeModel.KILL_CHARGE) {
			return state(player).ultimateReady();
		}
		return remainingTicks(player, slot) <= 0;
	}

	/** Server side: starts the slot's cooldown now, for {@code ticks}. */
	public static void startCooldown(Player player, AbilitySlot slot, int ticks) {
		long now = now(player);
		set(player, state(player).withCooldown(slot, new AbilityState.Cooldown(now, now + Math.max(0, ticks))));
	}

	/** Server side: ends every cooldown (debug, or a boon that refreshes the kit). */
	public static void clearCooldowns(Player player) {
		set(player, state(player).withoutCooldowns());
	}

	public static double ultimateCharge(Player player) {
		return state(player).ultimateCharge();
	}

	/**
	 * Server side: sets the ultimate charge (clamped to 0..1). Fires {@link AbilityEvents#ULTIMATE_READY} when the
	 * charge reaches 1 from below. Returns whether it is now ready.
	 */
	public static boolean setUltimateCharge(ServerPlayer player, double charge) {
		AbilityState before = state(player);
		AbilityState after = before.withUltimateCharge(charge);
		if (after.ultimateCharge() == before.ultimateCharge()) {
			return after.ultimateReady();
		}
		set(player, after);
		if (after.ultimateReady() && !before.ultimateReady()) {
			AbilityEvents.ULTIMATE_READY.invoker().onPlayer(player);
		}
		return after.ultimateReady();
	}

	/** Server side: adds to the ultimate charge; see {@link #setUltimateCharge}. */
	public static boolean addUltimateCharge(ServerPlayer player, double amount) {
		return setUltimateCharge(player, ultimateCharge(player) + amount);
	}

	/**
	 * Server side: a cast request for the slot (from the client's keybind through {@link AbilityCastPayload}, or a
	 * debug command). Fires {@link AbilityEvents#CAST_REQUESTED}; the abilities module decides whether it goes off.
	 */
	public static void requestCast(ServerPlayer player, AbilitySlot slot) {
		AbilityEvents.CAST_REQUESTED.invoker().onCastRequested(player, slot);
	}

	/** Called once by common's initializer: registers the attachment and the cast payload. */
	public static void init() {
		Networking.registerServerbound(AbilityCastPayload.TYPE, AbilityCastPayload.CODEC,
				(payload, context) -> requestCast(context.player(), payload.slot()));
	}
}
