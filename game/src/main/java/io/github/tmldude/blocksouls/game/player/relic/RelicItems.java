package io.github.tmldude.blocksouls.game.player.relic;

import java.util.EnumMap;
import java.util.Map;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;

import io.github.tmldude.blocksouls.game.BlockSoulsGame;
import io.github.tmldude.blocksouls.game.player.boon.BoonTier;

/** {@code blocksouls_game:relic_common}, {@code relic_rare}, {@code relic_legendary}. */
public final class RelicItems {
	private static final Map<BoonTier, RelicItem> RELICS = new EnumMap<>(BoonTier.class);

	private RelicItems() {
	}

	public static void register() {
		for (BoonTier tier : BoonTier.values()) {
			Identifier id = Identifier.fromNamespaceAndPath(BlockSoulsGame.MOD_ID, "relic_" + tier.id());
			ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, id);
			Rarity rarity = switch (tier) {
				case COMMON -> Rarity.UNCOMMON;
				case RARE -> Rarity.RARE;
				case LEGENDARY -> Rarity.EPIC;
			};
			RELICS.put(tier, Registry.register(BuiltInRegistries.ITEM, key,
					new RelicItem(tier, new Item.Properties().setId(key).stacksTo(16).rarity(rarity))));
		}
	}

	public static RelicItem get(BoonTier tier) {
		return RELICS.get(tier);
	}
}
