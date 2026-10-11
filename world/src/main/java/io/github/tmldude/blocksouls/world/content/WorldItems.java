package io.github.tmldude.blocksouls.world.content;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.level.block.Block;

import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;

import io.github.tmldude.blocksouls.world.BlockSoulsWorld;

public final class WorldItems {
	/** Everything this mod adds, in creative-tab order. */
	private static final List<Item> TAB_ORDER = new ArrayList<>();

	public static final Item SCRAP = item("scrap", Item::new, new Item.Properties());
	public static final Item RAW_GUNMETAL = item("raw_gunmetal", Item::new, new Item.Properties());
	public static final Item GUNMETAL_INGOT = item("gunmetal_ingot", Item::new, new Item.Properties());
	public static final Item GUNMETAL_NUGGET = item("gunmetal_nugget", Item::new, new Item.Properties());
	public static final Item BLIGHT_CORE = item("blight_core", Item::new, new Item.Properties().rarity(Rarity.UNCOMMON).fireResistant());

	public static final Item GUNMETAL_ORE = block(WorldBlocks.GUNMETAL_ORE);
	public static final Item DEEPSLATE_GUNMETAL_ORE = block(WorldBlocks.DEEPSLATE_GUNMETAL_ORE);
	public static final Item RAW_GUNMETAL_BLOCK = block(WorldBlocks.RAW_GUNMETAL_BLOCK);
	public static final Item GUNMETAL_BLOCK = block(WorldBlocks.GUNMETAL_BLOCK);
	public static final Item SCRAP_PILE = block(WorldBlocks.SCRAP_PILE);
	public static final Item BLIGHTED_NETHERRACK = block(WorldBlocks.BLIGHTED_NETHERRACK);
	public static final Item BLIGHT_GROWTH = block(WorldBlocks.BLIGHT_GROWTH);
	public static final Item BLIGHT_VEINS = block(WorldBlocks.BLIGHT_VEINS);
	public static final Item BLIGHT_HEART = block(WorldBlocks.BLIGHT_HEART);

	public static final Item SCRAP_PLATE_HELMET = armor("scrap_plate_helmet", WorldArmor.SCRAP_PLATE, ArmorType.HELMET, false);
	public static final Item SCRAP_PLATE_CHESTPLATE = armor("scrap_plate_chestplate", WorldArmor.SCRAP_PLATE, ArmorType.CHESTPLATE, false);
	public static final Item SCRAP_PLATE_LEGGINGS = armor("scrap_plate_leggings", WorldArmor.SCRAP_PLATE, ArmorType.LEGGINGS, false);
	public static final Item SCRAP_PLATE_BOOTS = armor("scrap_plate_boots", WorldArmor.SCRAP_PLATE, ArmorType.BOOTS, false);
	public static final Item BLIGHT_FORGED_HELMET = armor("blight_forged_helmet", WorldArmor.BLIGHT_FORGED, ArmorType.HELMET, true);
	public static final Item BLIGHT_FORGED_CHESTPLATE = armor("blight_forged_chestplate", WorldArmor.BLIGHT_FORGED, ArmorType.CHESTPLATE, true);
	public static final Item BLIGHT_FORGED_LEGGINGS = armor("blight_forged_leggings", WorldArmor.BLIGHT_FORGED, ArmorType.LEGGINGS, true);
	public static final Item BLIGHT_FORGED_BOOTS = armor("blight_forged_boots", WorldArmor.BLIGHT_FORGED, ArmorType.BOOTS, true);

	public static final ResourceKey<CreativeModeTab> TAB = ResourceKey.create(Registries.CREATIVE_MODE_TAB, BlockSoulsWorld.id("world"));

	private WorldItems() {
	}

	public static void init() {
		Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, TAB, FabricCreativeModeTab.builder()
				.title(Component.translatable("itemGroup.blocksouls_world.world"))
				.icon(() -> new ItemStack(GUNMETAL_INGOT))
				// Output is made public by Fabric's access widener; the offline typecheck flags it, the real build doesn't.
				.displayItems((parameters, output) -> TAB_ORDER.forEach(output::accept))
				.build());
	}

	private static Item item(String name, Function<Item.Properties, Item> factory, Item.Properties properties) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, BlockSoulsWorld.id(name));
		Item item = Registry.register(BuiltInRegistries.ITEM, key, factory.apply(properties.setId(key)));
		TAB_ORDER.add(item);
		return item;
	}

	private static Item block(Block block) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, BuiltInRegistries.BLOCK.getKey(block));
		Item item = new BlockItem(block, new Item.Properties().setId(key).useBlockDescriptionPrefix());
		item = Registry.register(BuiltInRegistries.ITEM, key, item);
		Item.BY_BLOCK.put(block, item);
		TAB_ORDER.add(item);
		return item;
	}

	private static Item armor(String name, net.minecraft.world.item.equipment.ArmorMaterial material, ArmorType type, boolean fireResistant) {
		Item.Properties properties = new Item.Properties().humanoidArmor(material, type);
		if (fireResistant) {
			properties = properties.fireResistant();
		}
		return item(name, Item::new, properties);
	}
}
