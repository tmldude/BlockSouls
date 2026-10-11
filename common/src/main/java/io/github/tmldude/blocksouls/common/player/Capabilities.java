package io.github.tmldude.blocksouls.common.player;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
 * The movement capability set on a player: which movement verbs (climb, swing, extra jumps, glide, ...)
 * this player has at all. The movement module gates each mechanic on {@link #has}; the game module
 * restricts the set when v2 origins pick a movement signature. <strong>The default — no set attached —
 * grants everything</strong>, which is exactly the v1 behavior and the classic run shape, so a world
 * without origins never changes.
 *
 * <p>Capabilities are on/off verbs; magnitudes (climb speed, web reach, ...) stay in {@link PlayerTraits}.
 * Saved with the player and synced to every client (other players' climb/glide poses render too). Unlike
 * traits, the set survives going down — an origin's identity isn't lost with its boons.
 */
public final class Capabilities {
	/** Wall climbing and ledge grabs. */
	public static final Identifier CLIMB = capability("climb");
	/** Web swinging. */
	public static final Identifier SWING = capability("swing");
	/** Double/triple jump chains (the hold-jump feel itself is universal, not a capability). */
	public static final Identifier MULTI_JUMP = capability("multi_jump");
	/** Stamina-drain glide wings. */
	public static final Identifier GLIDE = capability("glide");

	private static final Codec<Set<Identifier>> CODEC = Identifier.CODEC.listOf().xmap(Set::copyOf, List::copyOf);
	private static final AttachmentType<Set<Identifier>> TYPE = AttachmentRegistry.create(
			CommonIds.id("blocksouls_common", "capabilities"),
			builder -> builder.persistent(CODEC)
					.copyOnDeath()
					.syncWith(ByteBufCodecs.collection(HashSet::new, Identifier.STREAM_CODEC), AttachmentSyncPredicate.all()));

	private Capabilities() {
	}

	/** Whether the player has this movement verb. True for every verb while no explicit set is attached. */
	public static boolean has(Player player, Identifier capability) {
		Set<Identifier> set = ((AttachmentTarget) player).getAttached(TYPE);
		return set == null || set.contains(capability);
	}

	/** Whether the player is on the default all-granted set (v1 / classic behavior). */
	public static boolean allGranted(Player player) {
		return ((AttachmentTarget) player).getAttached(TYPE) == null;
	}

	/** The explicit set, or null when everything is granted. */
	public static Set<Identifier> granted(Player player) {
		return ((AttachmentTarget) player).getAttached(TYPE);
	}

	/** Server side: restricts the player to exactly this set (an origin's movement signature). */
	public static void restrictTo(Player player, Set<Identifier> capabilities) {
		((AttachmentTarget) player).setAttached(TYPE, Set.copyOf(capabilities));
	}

	/** Server side: adds one capability (e.g. a cross-training boon). No-op while everything is granted. */
	public static void grant(Player player, Identifier capability) {
		Set<Identifier> set = ((AttachmentTarget) player).getAttached(TYPE);
		if (set == null || set.contains(capability)) {
			return;
		}
		Set<Identifier> grown = new HashSet<>(set);
		grown.add(capability);
		((AttachmentTarget) player).setAttached(TYPE, Set.copyOf(grown));
	}

	/** Server side: back to the default all-granted set. */
	public static void grantAll(Player player) {
		((AttachmentTarget) player).removeAttached(TYPE);
	}

	/** Forces class loading so the attachment registers during init. */
	public static void init() {
	}

	private static Identifier capability(String path) {
		return CommonIds.id("blocksouls", path);
	}
}
