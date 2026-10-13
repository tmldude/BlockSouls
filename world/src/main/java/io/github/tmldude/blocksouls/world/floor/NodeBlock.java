package io.github.tmldude.blocksouls.world.floor;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/**
 * A floor resource node (scrap, gunmetal): unbreakable, it takes {@code nodeHits} punches (any tool) and then pops,
 * bursting its loot table several times and firing {@code NODE_POPPED}. Floors are lootable in minutes, not mined.
 */
public class NodeBlock extends Block {
	public static final IntegerProperty HITS = BlockStateProperties.AGE_7;

	public NodeBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(HITS, 0));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(HITS);
	}

	@Override
	protected void attack(BlockState state, Level level, BlockPos pos, Player player) {
		if (level instanceof ServerLevel serverLevel && player instanceof ServerPlayer serverPlayer) {
			hit(serverLevel, pos, serverPlayer);
		}
	}

	/** Hits that pop this node: {@code nodeHits} from the config (1–8). */
	protected int hitsToPop() {
		return Math.max(1, Math.min(8, WorldFloors.config().nodeHits));
	}

	/** Times its loot table is rolled when it pops: {@code nodeBurstRolls} from the config. */
	protected int burstRolls() {
		return WorldFloors.config().nodeBurstRolls;
	}

	/** One hit; pops the node on the last. Returns true when it popped. */
	public boolean hit(ServerLevel level, BlockPos pos, ServerPlayer player) {
		BlockState state = level.getBlockState(pos);
		if (!state.is(this)) {
			return false;
		}
		int hits = state.getValue(HITS) + 1;
		level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), pos.getX() + 0.5, pos.getY() + 0.6, pos.getZ() + 0.5, 8,
				0.3, 0.3, 0.3, 0.05);
		if (hits >= hitsToPop()) {
			WorldFloors.popNode(level, pos, state, player, burstRolls());
			return true;
		}
		level.setBlock(pos, state.setValue(HITS, hits), Block.UPDATE_CLIENTS);
		level.playSound(null, pos, SoundEvents.STONE_HIT, SoundSource.BLOCKS, 1.0F, 0.8F + 0.1F * hits);
		return false;
	}
}
