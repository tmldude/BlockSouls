package io.github.tmldude.blocksouls.game.player.classes;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;

import io.github.tmldude.blocksouls.game.BlockSoulsGame;

/** Hands a class's kit over: plain items, or a loot table rolled once (gear with rolled perks). */
final class ClassKits {
	private ClassKits() {
	}

	static void give(ServerPlayer player, PlayerClass playerClass) {
		for (PlayerClass.KitEntry entry : playerClass.kit()) {
			if (entry.lootTable().isPresent()) {
				rollTable(player, entry.lootTable().get());
			} else if (entry.item().isPresent()) {
				Item item = BuiltInRegistries.ITEM.getValue(Identifier.parse(entry.item().get()));
				if (item == null || item == net.minecraft.world.item.Items.AIR) {
					BlockSoulsGame.LOGGER.info("PlayerClass {}: kit item {} isn't registered, skipped", playerClass.id(), entry.item().get());
					continue;
				}
				give(player, new ItemStack(item, Math.max(1, entry.count())));
			}
		}
	}

	private static void rollTable(ServerPlayer player, String tableId) {
		ServerLevel level = player.level();
		ResourceKey<LootTable> key = ResourceKey.create(Registries.LOOT_TABLE, Identifier.parse(tableId));
		LootTable table = level.getServer().reloadableRegistries().getLootTable(key);
		if (table == LootTable.EMPTY) {
			BlockSoulsGame.LOGGER.info("PlayerClass kit loot table {} is empty or missing, skipped", tableId);
			return;
		}
		LootParams params = new LootParams.Builder(level)
				.withParameter(LootContextParams.ORIGIN, player.position())
				.withParameter(LootContextParams.THIS_ENTITY, player)
				.create(LootContextParamSets.GIFT);
		table.getRandomItems(params, stack -> give(player, stack));
	}

	private static void give(ServerPlayer player, ItemStack stack) {
		if (!player.addItem(stack) && !stack.isEmpty()) {
			player.spawnAtLocation(player.level(), stack);
		}
	}
}
