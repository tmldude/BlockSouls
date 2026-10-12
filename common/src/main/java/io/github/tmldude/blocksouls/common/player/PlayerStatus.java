package io.github.tmldude.blocksouls.common.player;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.entity.player.Player;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;

import io.github.tmldude.blocksouls.common.CommonIds;

/**
 * Whether a player is downed (game sets it). Synced to everyone so clients can render the crawl and the
 * revive prompt. Other modules check it: no rolling, climbing or driving while downed. Shooting goes
 * through {@link #canShoot}: v2's downed gameplay lets a downed player keep firing (the game lends a
 * pistol and halves their damage), so guns gate on {@code canShoot}, not on {@code !isDowned}.
 */
public final class PlayerStatus {
	private static final AttachmentType<Boolean> DOWNED = AttachmentRegistry.create(
			CommonIds.id("blocksouls_common", "downed"),
			builder -> builder.syncWith(ByteBufCodecs.BOOL, AttachmentSyncPredicate.all()));

	private static volatile boolean downedShooting;

	private PlayerStatus() {
	}

	public static boolean isDowned(Player player) {
		return Boolean.TRUE.equals(((AttachmentTarget) player).getAttached(DOWNED));
	}

	/**
	 * Whether the player may fire a gun right now: standing players always, downed players only while the game
	 * has downed shooting on ({@link #setDownedShooting}). Works on both sides (the flag is set by both the
	 * server and the client's own game module from the same config default; the server is authoritative).
	 */
	public static boolean canShoot(Player player) {
		return !isDowned(player) || downedShooting;
	}

	/** Game side: whether downed players keep their pistol (v2 downed gameplay). Off = the v1 rule. */
	public static void setDownedShooting(boolean enabled) {
		downedShooting = enabled;
	}

	public static boolean downedShooting() {
		return downedShooting;
	}

	/** Server side. */
	public static void setDowned(Player player, boolean downed) {
		((AttachmentTarget) player).setAttached(DOWNED, downed ? Boolean.TRUE : null);
	}

	/** Forces class loading so the attachment registers during init. */
	public static void init() {
	}
}
