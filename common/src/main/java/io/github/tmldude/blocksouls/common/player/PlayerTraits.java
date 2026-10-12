package io.github.tmldude.blocksouls.common.player;

import java.util.HashMap;
import java.util.Map;

import com.mojang.serialization.Codec;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;

import io.github.tmldude.blocksouls.common.CommonIds;

/**
 * Gameplay traits on a player, as {@code id → level}. The game module grants them (boons); other modules read
 * them to change behavior (e.g. movement checks {@link #DOUBLE_JUMP}). Saved with the player and synced to that
 * player's client. The game clears them when the player goes down; a vanilla death also clears them (not
 * copied on death).
 */
public final class PlayerTraits {
	public static final Identifier DOUBLE_JUMP = trait("double_jump");
	/** Extra i-frame ticks per level on dodge rolls. */
	public static final Identifier ROLL_IFRAMES = trait("roll_iframes");
	/** Free roll (no stamina) for a few seconds after a kill. */
	public static final Identifier FREE_ROLL_ON_KILL = trait("free_roll_on_kill");
	/** +25% web reach per level. */
	public static final Identifier WEB_REACH = trait("web_reach");
	/** +25% climb speed per level. */
	public static final Identifier CLIMB_SPEED = trait("climb_speed");
	/** +20% stamina regen per level. */
	public static final Identifier STAMINA_REGEN = trait("stamina_regen");
	/** Short creative-style flight. */
	public static final Identifier FLIGHT = trait("flight");
	/** +25% reload speed per level. */
	public static final Identifier RELOAD_SPEED = trait("reload_speed");
	/** −25 max stamina per level (an origin drawback; movement clamps the max at a floor). */
	public static final Identifier STAMINA_MAX_PENALTY = trait("stamina_max_penalty");

	private static final Codec<Map<Identifier, Integer>> CODEC = Codec.unboundedMap(Identifier.CODEC, Codec.INT);
	private static final AttachmentType<Map<Identifier, Integer>> TYPE = AttachmentRegistry.create(
			CommonIds.id("blocksouls_common", "traits"),
			builder -> builder.persistent(CODEC)
					.syncWith(ByteBufCodecs.map(HashMap::new, Identifier.STREAM_CODEC, ByteBufCodecs.VAR_INT), AttachmentSyncPredicate.targetOnly()));

	private PlayerTraits() {
	}

	/** 0 if the player doesn't have the trait. Works on both sides for the local player. */
	public static int level(Player player, Identifier trait) {
		Map<Identifier, Integer> traits = ((AttachmentTarget) player).getAttached(TYPE);
		return traits == null ? 0 : traits.getOrDefault(trait, 0);
	}

	public static boolean has(Player player, Identifier trait) {
		return level(player, trait) > 0;
	}

	/** Server side. Level 0 removes the trait. */
	public static void set(Player player, Identifier trait, int level) {
		Map<Identifier, Integer> traits = new HashMap<>(all(player));
		if (level <= 0) {
			traits.remove(trait);
		} else {
			traits.put(trait, level);
		}
		((AttachmentTarget) player).setAttached(TYPE, traits.isEmpty() ? null : Map.copyOf(traits));
	}

	public static Map<Identifier, Integer> all(Player player) {
		Map<Identifier, Integer> traits = ((AttachmentTarget) player).getAttached(TYPE);
		return traits == null ? Map.of() : traits;
	}

	/** Server side. */
	public static void clear(Player player) {
		((AttachmentTarget) player).removeAttached(TYPE);
	}

	/** Forces class loading so the attachment registers during init. */
	public static void init() {
	}

	private static Identifier trait(String path) {
		return CommonIds.id("blocksouls", path);
	}
}
