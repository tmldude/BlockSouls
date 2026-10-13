package io.github.tmldude.blocksouls.guns.item;

import java.util.List;

import com.mojang.serialization.Codec;

import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;

import io.github.tmldude.blocksouls.guns.BlockSoulsGuns;
import io.github.tmldude.blocksouls.guns.exotic.KindlingState;
import io.github.tmldude.blocksouls.guns.logic.GunRarity;

/** Item data components on guns. Changing them (e.g. firing) doesn't replay the re-equip animation. */
public final class GunComponents {
	/** Rolled rarity. Absent until rolled (first inventory tick for crafted / given guns, or a loot roll). */
	public static final DataComponentType<GunRarity> RARITY = register("rarity", Codec.STRING.xmap(GunRarity::byId, GunRarity::id),
			ByteBufCodecs.idMapper(i -> GunRarity.values()[i], GunRarity::ordinal));
	/** Perk ids ({@code blocksouls_guns:<perk>}), in roll order. */
	public static final DataComponentType<List<Identifier>> PERKS = register("perks", Identifier.CODEC.listOf(),
			Identifier.STREAM_CODEC.apply(ByteBufCodecs.list()));
	/** Rounds in the magazine. Absent = full. */
	public static final DataComponentType<Integer> MAG = register("mag", Codec.INT, ByteBufCodecs.VAR_INT);
	/** Kindling Engine's heat (exotic). Absent = cold. */
	public static final DataComponentType<KindlingState> KINDLING = register("kindling", KindlingState.CODEC, KindlingState.STREAM_CODEC);

	private GunComponents() {
	}

	private static <T> DataComponentType<T> register(String path, Codec<T> codec, StreamCodec<? super RegistryFriendlyByteBuf, T> streamCodec) {
		return Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, Identifier.fromNamespaceAndPath(BlockSoulsGuns.MOD_ID, path),
				DataComponentType.<T>builder().persistent(codec).networkSynchronized(streamCodec).ignoreSwapAnimation().build());
	}

	/** Forces class loading so the components register before items reference them. */
	public static void init() {
	}
}
