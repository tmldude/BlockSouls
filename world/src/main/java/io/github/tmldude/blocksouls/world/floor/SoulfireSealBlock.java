package io.github.tmldude.blocksouls.world.floor;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The Infernal fortress's soulfire door (v2-floor2-infernal): an unbreakable wall of soul flame. Pour a water bucket on
 * it and the whole door (every seal block touching it) goes out, opening the vault.
 */
public class SoulfireSealBlock extends Block {
	/** A door is two blocks; the cap only guards against a runaway flood fill. */
	private static final int MAX_BLOCKS = 16;

	public SoulfireSealBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
			InteractionHand hand, BlockHitResult hit) {
		if (!stack.is(Items.WATER_BUCKET)) {
			return InteractionResult.TRY_WITH_EMPTY_HAND;
		}
		if (level instanceof ServerLevel server && douse(server, pos) > 0) {
			player.setItemInHand(hand, ItemUtils.createFilledResult(stack, player, BucketItem.getEmptySuccessItem(stack, player)));
		}
		return InteractionResult.SUCCESS;
	}

	/** Puts out the seal at {@code pos} and every seal block connected to it; returns how many went out. */
	public static int douse(ServerLevel level, BlockPos pos) {
		Set<BlockPos> seen = new HashSet<>();
		Deque<BlockPos> open = new ArrayDeque<>();
		open.add(pos.immutable());
		int doused = 0;
		while (!open.isEmpty() && doused < MAX_BLOCKS) {
			BlockPos here = open.poll();
			if (!seen.add(here) || !level.getBlockState(here).is(FloorBlocks.SOULFIRE_SEAL)) {
				continue;
			}
			level.setBlock(here, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
			level.sendParticles(ParticleTypes.CLOUD, here.getX() + 0.5, here.getY() + 0.5, here.getZ() + 0.5, 8, 0.3, 0.4, 0.3, 0.02);
			level.sendParticles(ParticleTypes.LARGE_SMOKE, here.getX() + 0.5, here.getY() + 0.5, here.getZ() + 0.5, 6, 0.3, 0.4, 0.3, 0.01);
			doused++;
			for (Direction direction : Direction.values()) {
				open.add(here.relative(direction));
			}
		}
		if (doused > 0) {
			level.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 1.0F, 0.8F);
		}
		return doused;
	}

	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		if (random.nextInt(2) == 0) {
			level.addParticle(ParticleTypes.SOUL_FIRE_FLAME, pos.getX() + random.nextDouble(), pos.getY() + random.nextDouble(),
					pos.getZ() + random.nextDouble(), 0, 0.03, 0);
		}
	}
}
