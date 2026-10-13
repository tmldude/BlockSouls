package io.github.tmldude.blocksouls.world.floor;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;

import io.github.tmldude.blocksouls.world.content.WorldItems;

/**
 * The Vault's (floor 4) vault room door: a column of lock blocks that takes {@link #KEYS_TO_OPEN} vault keys, one per
 * use, and then swings away. Every block of the column counts the same keys. Unbreakable, no item.
 */
public class VaultLockBlock extends Block {
	public static final int KEYS_TO_OPEN = 3;
	public static final IntegerProperty KEYS = IntegerProperty.create("keys", 0, KEYS_TO_OPEN);

	public VaultLockBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(KEYS, 0));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(KEYS);
	}

	@Override
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
			BlockHitResult hitResult) {
		if (!stack.is(WorldItems.VAULT_KEY)) {
			return InteractionResult.TRY_WITH_EMPTY_HAND;
		}
		if (!(level instanceof ServerLevel serverLevel) || !(player instanceof ServerPlayer serverPlayer)) {
			return InteractionResult.SUCCESS;
		}
		if (!serverPlayer.getAbilities().instabuild) {
			stack.shrink(1);
		}
		insertKey(serverLevel, pos, serverPlayer);
		return InteractionResult.SUCCESS;
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
		if (player instanceof ServerPlayer serverPlayer) {
			serverPlayer.sendOverlayMessage(Component.translatable("message.blocksouls_world.vault_lock.hint", state.getValue(KEYS), KEYS_TO_OPEN));
		}
		return InteractionResult.SUCCESS;
	}

	/** One key into the lock at {@code pos}: the whole door column counts it; the last one opens the door. Returns the keys now in. */
	public static int insertKey(ServerLevel level, BlockPos pos, ServerPlayer player) {
		List<BlockPos> door = door(level, pos);
		if (door.isEmpty()) {
			return 0;
		}
		int keys = Math.min(KEYS_TO_OPEN, level.getBlockState(door.getFirst()).getValue(KEYS) + 1);
		if (keys >= KEYS_TO_OPEN) {
			for (BlockPos at : door) {
				level.setBlock(at, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
				level.sendParticles(ParticleTypes.CRIT, at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5, 10, 0.3, 0.4, 0.3, 0.1);
			}
			level.playSound(null, pos, SoundEvents.VAULT_OPEN_SHUTTER, SoundSource.BLOCKS, 1.5F, 0.7F);
			level.playSound(null, pos, SoundEvents.IRON_DOOR_OPEN, SoundSource.BLOCKS, 1.5F, 0.5F);
			player.sendOverlayMessage(Component.translatable("message.blocksouls_world.vault_lock.open"));
			return keys;
		}
		for (BlockPos at : door) {
			level.setBlock(at, level.getBlockState(at).setValue(KEYS, keys), Block.UPDATE_CLIENTS);
		}
		level.playSound(null, pos, SoundEvents.VAULT_INSERT_ITEM, SoundSource.BLOCKS, 1.2F, 0.8F);
		player.sendOverlayMessage(Component.translatable("message.blocksouls_world.vault_lock.key", keys, KEYS_TO_OPEN));
		return keys;
	}

	/** The lock column through {@code pos} (lock blocks straight up and down from it). */
	static List<BlockPos> door(Level level, BlockPos pos) {
		List<BlockPos> door = new ArrayList<>();
		if (!(level.getBlockState(pos).getBlock() instanceof VaultLockBlock)) {
			return door;
		}
		BlockPos at = pos;
		while (level.getBlockState(at.below()).getBlock() instanceof VaultLockBlock && door.size() < 8) {
			at = at.below();
			door.add(at);
		}
		door.add(pos);
		at = pos;
		while (level.getBlockState(at.above()).getBlock() instanceof VaultLockBlock && door.size() < 16) {
			at = at.above();
			door.add(at);
		}
		return door;
	}
}
