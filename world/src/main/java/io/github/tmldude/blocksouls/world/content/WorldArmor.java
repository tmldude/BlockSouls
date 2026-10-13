package io.github.tmldude.blocksouls.world.content;

import java.util.EnumMap;
import java.util.Map;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.EquipmentAssets;

import io.github.tmldude.blocksouls.world.BlockSoulsWorld;

/** The armor sets (and Skyrift's featherboots). Set bonuses live in {@code armor.SetBonuses}. */
public final class WorldArmor {
	public static final TagKey<Item> REPAIRS_SCRAP_PLATE = TagKey.create(Registries.ITEM, BlockSoulsWorld.id("repairs_scrap_plate_armor"));
	public static final TagKey<Item> REPAIRS_BLIGHT_FORGED = TagKey.create(Registries.ITEM, BlockSoulsWorld.id("repairs_blight_forged_armor"));

	public static final ResourceKey<EquipmentAsset> SCRAP_PLATE_ASSET = ResourceKey.create(EquipmentAssets.ROOT_ID, BlockSoulsWorld.id("scrap_plate"));
	public static final ResourceKey<EquipmentAsset> BLIGHT_FORGED_ASSET = ResourceKey.create(EquipmentAssets.ROOT_ID, BlockSoulsWorld.id("blight_forged"));

	/** About iron: same defense, a bit tougher. */
	public static final ArmorMaterial SCRAP_PLATE = new ArmorMaterial(16, defense(2, 5, 6, 2), 9,
			SoundEvents.ARMOR_EQUIP_IRON, 0.5F, 0.0F, REPAIRS_SCRAP_PLATE, SCRAP_PLATE_ASSET);
	/** Between diamond (33, toughness 2) and netherite (37, toughness 3, 0.1 knockback). */
	public static final ArmorMaterial BLIGHT_FORGED = new ArmorMaterial(35, defense(3, 6, 8, 3), 14,
			SoundEvents.ARMOR_EQUIP_NETHERITE, 2.5F, 0.05F, REPAIRS_BLIGHT_FORGED, BLIGHT_FORGED_ASSET);

	// --- floor 3, Skyrift (Stage 6C) ---
	public static final TagKey<Item> REPAIRS_SKYSTEEL = TagKey.create(Registries.ITEM, BlockSoulsWorld.id("repairs_skysteel_armor"));
	public static final ResourceKey<EquipmentAsset> SKYSTEEL_ASSET = ResourceKey.create(EquipmentAssets.ROOT_ID, BlockSoulsWorld.id("skysteel"));
	/** Light (chain-ish) but tough to wear out; only the featherboots use it. */
	public static final ArmorMaterial SKYSTEEL = new ArmorMaterial(22, defense(2, 4, 5, 2), 15,
			SoundEvents.ARMOR_EQUIP_CHAIN, 0.5F, 0.0F, REPAIRS_SKYSTEEL, SKYSTEEL_ASSET);
	private static final Identifier FEATHER = BlockSoulsWorld.id("skysteel_featherboots");

	/** The boots' armor plus the feather: +4 safe fall distance and a third less fall damage. */
	public static ItemAttributeModifiers featherboots() {
		EquipmentSlotGroup feet = EquipmentSlotGroup.FEET;
		return SKYSTEEL.createAttributes(ArmorType.BOOTS)
				.withModifierAdded(Attributes.SAFE_FALL_DISTANCE, new AttributeModifier(FEATHER, 4.0, AttributeModifier.Operation.ADD_VALUE), feet)
				.withModifierAdded(Attributes.FALL_DAMAGE_MULTIPLIER,
						new AttributeModifier(FEATHER, -0.33, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL), feet);
	}
	// --- end floor 3 ---

	private WorldArmor() {
	}

	private static Map<ArmorType, Integer> defense(int boots, int legs, int chest, int helmet) {
		Map<ArmorType, Integer> map = new EnumMap<>(ArmorType.class);
		map.put(ArmorType.BOOTS, boots);
		map.put(ArmorType.LEGGINGS, legs);
		map.put(ArmorType.CHESTPLATE, chest);
		map.put(ArmorType.HELMET, helmet);
		map.put(ArmorType.BODY, chest);
		return map;
	}
}
