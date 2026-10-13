package io.github.tmldude.blocksouls.game.test;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;

import net.fabricmc.api.ModInitializer;

import io.github.tmldude.blocksouls.common.CommonIds;

/**
 * Game tests run without the guns mod, so this registers a plain item under the Starter Pistol's id (unless guns is
 * present) to test the start kit and the pistol breaking.
 */
public class RunTestMod implements ModInitializer {
	@Override
	public void onInitialize() {
		placeholder(CommonIds.STARTER_PISTOL, 1);
	}

	private static void placeholder(net.minecraft.resources.Identifier id, int stackSize) {
		if (BuiltInRegistries.ITEM.containsKey(id)) {
			return;
		}
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, id);
		Registry.register(BuiltInRegistries.ITEM, key, new Item(new Item.Properties().setId(key).stacksTo(stackSize)));
	}
}
