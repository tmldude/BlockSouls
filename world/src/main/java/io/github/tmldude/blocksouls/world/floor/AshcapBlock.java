package io.github.tmldude.blocksouls.world.floor;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Floor 2's herb (v2-floor2-infernal): the ashcap, a {@link HerbBlock} that glows and sheds spores and embers so a party
 * spots the two or three on a floor from across a lobe.
 */
public class AshcapBlock extends HerbBlock {
	public AshcapBlock(Properties properties) {
		super(properties);
	}

	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		if (random.nextInt(3) == 0) {
			level.addParticle(ParticleTypes.CRIMSON_SPORE, pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.6, pos.getY() + 0.7,
					pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.6, 0, 0.02, 0);
		}
		if (random.nextInt(8) == 0) {
			level.addParticle(ParticleTypes.SMALL_FLAME, pos.getX() + 0.5, pos.getY() + 0.65, pos.getZ() + 0.5, 0, 0.01, 0);
		}
	}
}
