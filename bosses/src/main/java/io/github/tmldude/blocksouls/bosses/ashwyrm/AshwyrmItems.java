package io.github.tmldude.blocksouls.bosses.ashwyrm;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;

import io.github.tmldude.blocksouls.common.CommonIds;

/**
 * The Ashwyrm's drops that the bosses module owns: {@code ashwyrm_scale} (a material) and {@code ember_barrel} (a gun
 * part). Other modules use them by id ({@code CommonIds.ASHWYRM_SCALE}, {@code CommonIds.EMBER_BARREL}).
 */
public final class AshwyrmItems {
	public static Item ASHWYRM_SCALE;
	public static Item EMBER_BARREL;

	private AshwyrmItems() {
	}

	static void register() {
		ASHWYRM_SCALE = register(CommonIds.ASHWYRM_SCALE, new Item.Properties().rarity(Rarity.UNCOMMON));
		EMBER_BARREL = register(CommonIds.EMBER_BARREL, new Item.Properties().stacksTo(16).rarity(Rarity.RARE).fireResistant());
		ResourceKey<CreativeModeTab> ingredients = ResourceKey.create(Registries.CREATIVE_MODE_TAB, Identifier.withDefaultNamespace("ingredients"));
		net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents.modifyOutputEvent(ingredients).register(output ->
				output.insertAfter(Items.BLAZE_ROD, ASHWYRM_SCALE, EMBER_BARREL));
	}

	private static Item register(Identifier id, Item.Properties properties) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, id);
		return Registry.register(BuiltInRegistries.ITEM, key, new Item(properties.setId(key)));
	}
}
