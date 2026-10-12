package io.github.tmldude.blocksouls.guns.item;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.component.UseEffects;

import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;

import io.github.tmldude.blocksouls.common.CommonIds;
import io.github.tmldude.blocksouls.guns.BlockSoulsGuns;
import io.github.tmldude.blocksouls.guns.logic.GunRarity;
import io.github.tmldude.blocksouls.guns.weapon.Weapons;

public final class GunsItems {
	public static GunItem STARTER_PISTOL;
	public static GunItem HAND_CANNON;
	public static GunItem AUTO_RIFLE;
	public static GunItem SHOTGUN;
	public static GunItem SNIPER_RIFLE;

	public static ExoticGunItem STRAY_VERDICT;
	public static ExoticGunItem LANTERN_EYE;
	public static ExoticGunItem BACKDRAFT;
	public static ExoticGunItem KINDLING_ENGINE;
	public static ExoticGunItem TETHER_LANCE;
	/** Soul-infused smithing template: catalyst + a gun → its exotic. */
	public static Item EXOTIC_CATALYST;

	public static Item PRIMARY_AMMO;
	public static Item SPECIAL_AMMO;

	public static Item GUN_BARREL;
	public static Item GUN_RECEIVER;
	public static Item GUN_STOCK;

	private GunsItems() {
	}

	public static void register() {
		GunComponents.init();

		STARTER_PISTOL = (GunItem) register(CommonIds.STARTER_PISTOL.getPath(), props -> new GunItem(gun(props)
				.component(GunComponents.RARITY, GunRarity.COMMON)
				.component(GunComponents.PERKS, List.of()), Weapons.STARTER_PISTOL));
		HAND_CANNON = gun(Weapons.HAND_CANNON);
		AUTO_RIFLE = gun(Weapons.AUTO_RIFLE);
		SHOTGUN = gun(Weapons.SHOTGUN);
		SNIPER_RIFLE = gun(Weapons.SNIPER_RIFLE);
		STRAY_VERDICT = exotic(Weapons.STRAY_VERDICT);
		LANTERN_EYE = exotic(Weapons.LANTERN_EYE);
		BACKDRAFT = exotic(Weapons.BACKDRAFT);
		KINDLING_ENGINE = exotic(Weapons.KINDLING_ENGINE);
		TETHER_LANCE = exotic(Weapons.TETHER_LANCE);
		EXOTIC_CATALYST = register("exotic_catalyst", props -> new Item(props.stacksTo(16)) {
			@Override
			public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
				builder.accept(Component.translatable("tooltip." + BlockSoulsGuns.MOD_ID + ".exotic_catalyst").withStyle(ChatFormatting.GOLD));
			}
		});

		PRIMARY_AMMO = register("primary_ammo", props -> new Item(props.stacksTo(64)));
		SPECIAL_AMMO = register("special_ammo", props -> new Item(props.stacksTo(32)));

		GUN_BARREL = register("gun_barrel", Item::new);
		GUN_RECEIVER = register("gun_receiver", Item::new);
		GUN_STOCK = register("gun_stock", Item::new);

		CreativeModeTabEvents.modifyOutputEvent(tab("combat")).register(output -> {
			output.insertAfter(Items.CROSSBOW, STARTER_PISTOL, HAND_CANNON, AUTO_RIFLE, SHOTGUN, SNIPER_RIFLE, STRAY_VERDICT, LANTERN_EYE,
					BACKDRAFT, KINDLING_ENGINE, TETHER_LANCE, PRIMARY_AMMO, SPECIAL_AMMO);
		});
		CreativeModeTabEvents.modifyOutputEvent(tab("ingredients")).register(output -> {
			output.insertAfter(Items.GUNPOWDER, GUN_BARREL, GUN_RECEIVER, GUN_STOCK, EXOTIC_CATALYST);
		});
	}

	public static List<GunItem> guns() {
		return List.of(STARTER_PISTOL, HAND_CANNON, AUTO_RIFLE, SHOTGUN, SNIPER_RIFLE, STRAY_VERDICT, LANTERN_EYE, BACKDRAFT, KINDLING_ENGINE,
				TETHER_LANCE);
	}

	public static List<ExoticGunItem> exotics() {
		return List.of(STRAY_VERDICT, LANTERN_EYE, BACKDRAFT, KINDLING_ENGINE, TETHER_LANCE);
	}

	private static ExoticGunItem exotic(String weaponId) {
		return (ExoticGunItem) register(weaponId, props -> new ExoticGunItem(gun(props), weaponId));
	}

	/** Ammo item a gun reloads from; {@code null} for infinite ammo. */
	public static Item ammoFor(String ammo) {
		return switch (ammo) {
			case "primary" -> PRIMARY_AMMO;
			case "special" -> SPECIAL_AMMO;
			default -> null;
		};
	}

	private static GunItem gun(String weaponId) {
		return (GunItem) register(weaponId, props -> new GunItem(gun(props), weaponId));
	}

	private static Item.Properties gun(Item.Properties props) {
		return props.stacksTo(1)
				.enchantable(10)
				.component(DataComponents.USE_EFFECTS, new UseEffects(false, false, (float) BlockSoulsGuns.CONFIG.shooting.adsMoveSpeed));
	}

	private static ResourceKey<CreativeModeTab> tab(String vanillaId) {
		return ResourceKey.create(Registries.CREATIVE_MODE_TAB, Identifier.withDefaultNamespace(vanillaId));
	}

	private static Item register(String path, Function<Item.Properties, Item> factory) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(BlockSoulsGuns.MOD_ID, path));
		return Registry.register(BuiltInRegistries.ITEM, key, factory.apply(new Item.Properties().setId(key)));
	}
}
