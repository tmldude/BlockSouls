package io.github.tmldude.blocksouls.world.gen;

import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.Vec3i;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.level.levelgen.structure.placement.AbstractSpreadingStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadType;

import io.github.tmldude.blocksouls.world.BlockSoulsWorld;

/**
 * Vanilla random spread whose spacing / separation come from {@code world.json} ({@code {"type":
 * "blocksouls_world:config_spread", "key": "wreck", "salt": 1}}). Read when the datapack loads, so a change applies on
 * the next world load / {@code /reload}. A spacing of 0 disables the structure. Being a {@link
 * RandomSpreadStructurePlacement}, {@code /locate} searches it like any vanilla structure set.
 */
public class ConfigSpreadPlacement extends RandomSpreadStructurePlacement {
	public static final MapCodec<ConfigSpreadPlacement> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
			Codec.STRING.fieldOf("key").forGetter(ConfigSpreadPlacement::key),
			ExtraCodecs.NON_NEGATIVE_INT.fieldOf("salt").forGetter(p -> p.salt())).apply(i, ConfigSpreadPlacement::create));

	private final String key;

	private ConfigSpreadPlacement(String key, int salt, Spread spread) {
		super(Vec3i.ZERO, AbstractSpreadingStructurePlacement.FrequencyReductionMethod.DEFAULT, spread.enabled() ? 1.0F : 0.0F, salt,
				Optional.empty(), spread.spacing(), spread.separation(), RandomSpreadType.LINEAR);
		this.key = key;
	}

	private static ConfigSpreadPlacement create(String key, int salt) {
		return new ConfigSpreadPlacement(key, salt, Spread.forKey(BlockSoulsWorld.CONFIG.gen, key));
	}

	public String key() {
		return key;
	}

	@Override
	@SuppressWarnings("unchecked")
	public MapCodec<RandomSpreadStructurePlacement> codec() {
		return (MapCodec<RandomSpreadStructurePlacement>) (MapCodec<?>) CODEC;
	}
}
