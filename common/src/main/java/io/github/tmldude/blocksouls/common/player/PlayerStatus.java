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
 * revive prompt. Other modules check it: no shooting, rolling, climbing or driving while downed.
 */
public final class PlayerStatus {
	private static final AttachmentType<Boolean> DOWNED = AttachmentRegistry.create(
			CommonIds.id("blocksouls_common", "downed"),
			builder -> builder.syncWith(ByteBufCodecs.BOOL, AttachmentSyncPredicate.all()));

	private PlayerStatus() {
	}

	public static boolean isDowned(Player player) {
		return Boolean.TRUE.equals(((AttachmentTarget) player).getAttached(DOWNED));
	}

	/** Server side. */
	public static void setDowned(Player player, boolean downed) {
		((AttachmentTarget) player).setAttached(DOWNED, downed ? Boolean.TRUE : null);
	}

	/** Forces class loading so the attachment registers during init. */
	public static void init() {
	}
}
