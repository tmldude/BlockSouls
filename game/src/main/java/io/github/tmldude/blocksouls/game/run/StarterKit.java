package io.github.tmldude.blocksouls.game.run;

import java.util.Optional;

import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import io.github.tmldude.blocksouls.common.CommonIds;
import io.github.tmldude.blocksouls.game.BlockSoulsGame;

/** The start kit (stone pickaxe + Starter Pistol) and the pistol breaking after wave 3. */
public final class StarterKit {
	private static boolean warnedMissingPistol;

	private StarterKit() {
	}

	/** The Starter Pistol item, if the guns mod registered it. */
	public static Optional<Item> pistol() {
		Optional<Item> item = BuiltInRegistries.ITEM.getOptional(CommonIds.STARTER_PISTOL);
		if (item.isEmpty() && !warnedMissingPistol) {
			warnedMissingPistol = true;
			BlockSoulsGame.LOGGER.info("No item {} registered; the start kit skips the Starter Pistol", CommonIds.STARTER_PISTOL);
		}
		return item;
	}

	/** Players present at run start: stone pickaxe + pistol. */
	static void giveFullKit(ServerPlayer player, RunData data) {
		give(player, new ItemStack(Items.STONE_PICKAXE));
		pistol().ifPresent(item -> give(player, new ItemStack(item)));
		data.gotKit.add(player.getUUID());
		data.setDirty();
	}

	/** Late joiners: pistol only, and only while the pistols still exist. */
	static void onJoin(ServerPlayer player, RunData data) {
		if (data.pistolsBroken) {
			removePistols(player, true);
			return;
		}
		if (!data.gotKit.contains(player.getUUID()) && data.wave <= RunSystem.config().starterPistolLastWave) {
			pistol().ifPresent(item -> give(player, new ItemStack(item)));
			data.gotKit.add(player.getUUID());
			data.setDirty();
		}
	}

	static void onWaveEnded(MinecraftServer server, RunData data, int wave) {
		if (!data.pistolsBroken && wave >= RunSystem.config().starterPistolLastWave) {
			breakAll(server, data);
		}
	}

	static void breakAll(MinecraftServer server, RunData data) {
		data.pistolsBroken = true;
		data.setDirty();
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			removePistols(player, true);
		}
	}

	/** Removes every Starter Pistol a player holds (inventory, ender chest, cursor, crafting grid). */
	public static int removePistols(ServerPlayer player, boolean announce) {
		Optional<Item> pistol = pistol();
		if (pistol.isEmpty()) {
			return 0;
		}
		Item item = pistol.get();
		int removed = clear(player.getInventory(), item) + clear(player.getEnderChestInventory(), item)
				+ clear(player.inventoryMenu.getCraftSlots(), item);
		if (player.containerMenu.getCarried().is(item)) {
			removed += player.containerMenu.getCarried().getCount();
			player.containerMenu.setCarried(ItemStack.EMPTY);
		}
		if (player.inventoryMenu != player.containerMenu && player.inventoryMenu.getCarried().is(item)) {
			removed += player.inventoryMenu.getCarried().getCount();
			player.inventoryMenu.setCarried(ItemStack.EMPTY);
		}
		if (removed > 0) {
			player.containerMenu.broadcastChanges();
			player.inventoryMenu.broadcastChanges();
			if (announce) {
				ServerLevel level = player.level();
				level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ITEM_BREAK.value(), SoundSource.PLAYERS, 1.0F, 0.6F);
				level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, item), player.getX(), player.getY() + 1.2, player.getZ(), 24, 0.3, 0.3,
						0.3, 0.1);
				player.sendSystemMessage(Component.translatableWithFallback("message.blocksouls_game.pistol_broke",
						"Your Starter Pistol fell apart. It had a good run."));
			}
		}
		return removed;
	}

	private static int clear(Container container, Item item) {
		int removed = 0;
		for (int i = 0; i < container.getContainerSize(); i++) {
			ItemStack stack = container.getItem(i);
			if (stack.is(item)) {
				removed += stack.getCount();
				container.setItem(i, ItemStack.EMPTY);
			}
		}
		return removed;
	}

	private static void give(ServerPlayer player, ItemStack stack) {
		if (!player.getInventory().add(stack) && !stack.isEmpty()) {
			player.spawnAtLocation(player.level(), stack);
		}
	}
}
