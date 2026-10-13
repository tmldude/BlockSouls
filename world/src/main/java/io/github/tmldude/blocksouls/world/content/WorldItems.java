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
// floor 4 (The Vault) drinks
import net.minecraft.core.HolderSet;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.Consumables;
import net.minecraft.world.item.consume_effects.ApplyStatusEffectsConsumeEffect;
import net.minecraft.world.item.consume_effects.RemoveStatusEffectsConsumeEffect;

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

	// v2-floor5-blight: the Blighted Surface's rare (blight crystal), its herb (ghostcap) and what they make.
	public static final Item BLIGHT_CRYSTAL = item("blight_crystal", Item::new, new Item.Properties().rarity(Rarity.RARE));
	public static final Item GHOSTCAP = item("ghostcap", Item::new, new Item.Properties().rarity(Rarity.UNCOMMON));
	public static final Item GHOSTCAP_DRAUGHT = item("ghostcap_draught", Item::new, WorldConsumables.draught());
	public static final Item GHOSTCAP_BROTH = item("ghostcap_broth", Item::new, WorldConsumables.broth());
	public static final Item HOLLOW_CROWN = item("hollow_crown", Item::new, WorldConsumables.hollowCrown());

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

	// --- floor 4, The Vault (Stage 6C) ---
	public static final Item DEEP_IRON = item("deep_iron", Item::new, new Item.Properties().rarity(Rarity.UNCOMMON));
	public static final Item CAVE_MOSS = item("cave_moss", Item::new, new Item.Properties());
	/** One of the three keys to the vault room's lock (found in crates in three other rooms). */
	public static final Item VAULT_KEY = item("vault_key", Item::new, new Item.Properties().rarity(Rarity.RARE).stacksTo(3));
	/** Night vision: see the dark vault. */
	public static final Item NIGHT_EYE_TONIC = item("night_eye_tonic", Item::new, drink(Consumables.defaultDrink()
			.onConsume(new ApplyStatusEffectsConsumeEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 3600, 0)))));
	/** Clears poison and wither, then a short regeneration. */
	public static final Item MOSS_ANTIDOTE = item("moss_antidote", Item::new, drink(Consumables.defaultDrink()
			.onConsume(new RemoveStatusEffectsConsumeEffect(HolderSet.direct(MobEffects.POISON, MobEffects.WITHER)))
			.onConsume(new ApplyStatusEffectsConsumeEffect(new MobEffectInstance(MobEffects.REGENERATION, 100, 0)))));
	// --- end floor 4 ---

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

	static Item item(String name, Function<Item.Properties, Item> factory, Item.Properties properties) {
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

	/** A floor drink (floor 4): stacks to 16 and leaves the bottle. */
	private static Item.Properties drink(net.minecraft.world.item.component.Consumable.Builder consumable) {
		return new Item.Properties().stacksTo(16).craftRemainder(Items.GLASS_BOTTLE).usingConvertsTo(Items.GLASS_BOTTLE)
				.component(DataComponents.CONSUMABLE, consumable.build());
	}

	private static Item armor(String name, net.minecraft.world.item.equipment.ArmorMaterial material, ArmorType type, boolean fireResistant) {
		Item.Properties properties = new Item.Properties().humanoidArmor(material, type);
		if (fireResistant) {
			properties = properties.fireResistant();
		}
		return item(name, Item::new, properties);
	}
}
