package io.github.tmldude.blocksouls.world.content;

import java.util.EnumMap;
import java.util.Map;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.EquipmentAssets;

import io.github.tmldude.blocksouls.world.BlockSoulsWorld;

/** The two armor sets. Set bonuses live in {@code armor.SetBonuses}. */
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
