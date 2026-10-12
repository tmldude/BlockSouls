package io.github.tmldude.blocksouls.game.player.downed;

import com.mojang.serialization.Codec;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;

import io.github.tmldude.blocksouls.common.CommonTags;
import io.github.tmldude.blocksouls.common.event.PlayerRunEvents;
import io.github.tmldude.blocksouls.common.event.RunEvents;
import io.github.tmldude.blocksouls.common.player.PlayerStatus;
import io.github.tmldude.blocksouls.game.BlockSoulsGame;
import io.github.tmldude.blocksouls.game.player.PlayersConfig;
import io.github.tmldude.blocksouls.game.run.StarterKit;

/**
 * Downed players keep playing: they crawl, keep (or are lent) a pistol and fire it at reduced damage, and can ping.
 * The gun gate is {@code PlayerStatus.canShoot}, switched on here from config; the damage scale is a mixin on
 * {@code LivingEntity.hurtServer} ({@link DownedRules#damage}). The lent Starter Pistol is taken back when the
 * player stands up.
 */
public final class DownedPlay {
	/** Set while the player holds a Starter Pistol we lent them for the crawl. Survives logout like the downed flag. */
	private static final AttachmentType<Boolean> LENT_PISTOL = AttachmentRegistry.create(
			Identifier.fromNamespaceAndPath(BlockSoulsGame.MOD_ID, "lent_pistol"), builder -> builder.persistent(Codec.BOOL));

	private static PlayersConfig.Downed config = new PlayersConfig.Downed();

	private DownedPlay() {
	}

	public static void register(PlayersConfig.Downed downedConfig) {
		config = downedConfig;
		PlayerStatus.setDownedShooting(config.keepShooting);

		PlayerRunEvents.DOWNED.register(DownedPlay::lendPistol);
		PlayerRunEvents.REVIVED.register((player, reviver) -> reclaimPistol(player));
		RunEvents.RUN_OVER.register((server, state) -> server.getPlayerList().getPlayers().forEach(DownedPlay::reclaimPistol));
		RunEvents.RUN_STARTED.register((server, state) -> server.getPlayerList().getPlayers().forEach(DownedPlay::reclaimPistol));
	}

	/** Whether downed players keep firing (config; mirrors {@code PlayerStatus.downedShooting}). */
	public static boolean keepShooting() {
		return config.keepShooting;
	}

	/** The mixin's hook: damage from a downed attacker is scaled by {@code downedDamageMultiplier}. */
	public static float scaleDamage(DamageSource source, float amount) {
		boolean downed = source.getEntity() instanceof ServerPlayer attacker && PlayerStatus.isDowned(attacker);
		return DownedRules.damage(amount, downed, config.downedDamageMultiplier);
	}

	/** Whether a downed player may use this item: only guns, and only while downed shooting is on. */
	public static boolean mayUseWhileDowned(ItemStack stack) {
		return config.keepShooting && stack.is(CommonTags.GUNS);
	}

	/** A downed player with no gun at all gets a Starter Pistol in the hotbar, selected, for the crawl. */
	static void lendPistol(ServerPlayer player) {
		if (!config.keepShooting || !config.lendPistol || hasGun(player)) {
			return;
		}
		StarterKit.pistol().ifPresent(pistol -> {
			Inventory inventory = player.getInventory();
			int slot = inventory.getItem(inventory.getSelectedSlot()).isEmpty() ? inventory.getSelectedSlot() : freeHotbarSlot(inventory);
			ItemStack stack = new ItemStack(pistol);
			if (slot < 0) {
				if (!player.addItem(stack)) {
					return;
				}
			} else {
				inventory.setItem(slot, stack);
				inventory.setSelectedSlot(slot);
			}
			((AttachmentTarget) player).setAttached(LENT_PISTOL, Boolean.TRUE);
			player.inventoryMenu.broadcastChanges();
		});
	}

	/** Takes one Starter Pistol back if we lent it. */
	static void reclaimPistol(ServerPlayer player) {
		AttachmentTarget target = (AttachmentTarget) player;
		if (!Boolean.TRUE.equals(target.getAttached(LENT_PISTOL))) {
			return;
		}
		target.removeAttached(LENT_PISTOL);
		StarterKit.pistol().ifPresent(pistol -> {
			Inventory inventory = player.getInventory();
			for (int i = 0; i < inventory.getContainerSize(); i++) {
				if (inventory.getItem(i).is(pistol)) {
					inventory.getItem(i).shrink(1);
					player.inventoryMenu.broadcastChanges();
					return;
				}
			}
		});
	}

	static boolean wasLentPistol(ServerPlayer player) {
		return Boolean.TRUE.equals(((AttachmentTarget) player).getAttached(LENT_PISTOL));
	}

	private static boolean hasGun(ServerPlayer player) {
		Inventory inventory = player.getInventory();
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			if (inventory.getItem(i).is(CommonTags.GUNS)) {
				return true;
			}
		}
		return false;
	}

	private static int freeHotbarSlot(Inventory inventory) {
		for (int i = 0; i < Inventory.getSelectionSize(); i++) {
			if (inventory.getItem(i).isEmpty()) {
				return i;
			}
		}
		return -1;
	}

	/** Forces class loading so the attachment registers during init. */
	public static void init() {
	}
}
