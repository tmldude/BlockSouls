package io.github.tmldude.blocksouls.world.floor;

import java.util.Locale;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/**
 * A vault wall (floor 4): unbreakable by mining, it takes {@code wallHits} punches and then bursts, like a node with no
 * loot. A wall can hide something ({@link Content}): a shortcut plug, a mob closet's door, or the secret's hollow wall,
 * which sounds hollow when hit and drips. Bursting one block of a plug collapses the whole plug.
 */
public class VaultWallBlock extends Block {
	public static final IntegerProperty HITS = BlockStateProperties.AGE_3;
	public static final EnumProperty<Content> CONTENT = EnumProperty.create("content", Content.class);

	/** What the wall hides. */
	public enum Content implements StringRepresentable {
		NONE, SHORTCUT, CLOSET, HOLLOW;

		@Override
		public String getSerializedName() {
			return name().toLowerCase(Locale.ROOT);
		}

		public static Content of(BoxLayout.Plug plug) {
			return plug == null ? NONE : switch (plug) {
				case SHORTCUT -> SHORTCUT;
				case CLOSET -> CLOSET;
				case HOLLOW -> HOLLOW;
			};
		}
	}

	public VaultWallBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(HITS, 0).setValue(CONTENT, Content.NONE));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(HITS, CONTENT);
	}

	@Override
	protected void attack(BlockState state, Level level, BlockPos pos, Player player) {
		if (level instanceof ServerLevel serverLevel && player instanceof ServerPlayer serverPlayer) {
			hit(serverLevel, pos, serverPlayer);
		}
	}

	/** One hit; a plug wall bursts on the last (plain walls never do). Returns true when it burst. */
	public boolean hit(ServerLevel level, BlockPos pos, ServerPlayer player) {
		BlockState state = level.getBlockState(pos);
		if (!state.is(this)) {
			return false;
		}
		Content content = state.getValue(CONTENT);
		int hits = state.getValue(HITS) + 1;
		level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 6,
				0.3, 0.3, 0.3, 0.05);
		if (content == Content.NONE) {
			// plain vault wall: it rings and holds, so the corridors stay the only way through
			level.playSound(null, pos, SoundEvents.STONE_HIT, SoundSource.BLOCKS, 1.0F, 0.5F);
			return false;
		}
		if (content == Content.HOLLOW) {
			level.playSound(null, pos, SoundEvents.DECORATED_POT_HIT, SoundSource.BLOCKS, 1.2F, 0.6F);
			level.sendParticles(ParticleTypes.DRIPPING_DRIPSTONE_WATER, pos.getX() + 0.5, pos.getY() + 0.1, pos.getZ() + 0.5, 6, 0.4, 0.1, 0.4, 0.0);
		}
		if (hits >= Math.max(1, Math.min(3, VaultHazards.config().wallHits))) {
			VaultHazards.burst(level, pos, state, player);
			return true;
		}
		level.setBlock(pos, state.setValue(HITS, hits), Block.UPDATE_CLIENTS);
		if (content != Content.HOLLOW) {
			level.playSound(null, pos, SoundEvents.STONE_HIT, SoundSource.BLOCKS, 1.0F, 0.7F + 0.1F * hits);
		}
		return false;
	}

	/** The hollow wall's tell: water drips off the faces that open onto air. */
	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		if (state.getValue(CONTENT) != Content.HOLLOW || random.nextInt(3) != 0) {
			return;
		}
		Direction side = Direction.Plane.HORIZONTAL.getRandomDirection(random);
		if (!level.getBlockState(pos.relative(side)).isAir()) {
			return;
		}
		double x = pos.getX() + 0.5 + side.getStepX() * 0.52 + (side.getStepX() == 0 ? random.nextDouble() - 0.5 : 0);
		double z = pos.getZ() + 0.5 + side.getStepZ() * 0.52 + (side.getStepZ() == 0 ? random.nextDouble() - 0.5 : 0);
		level.addParticle(ParticleTypes.DRIPPING_DRIPSTONE_WATER, x, pos.getY() + random.nextDouble() * 0.6, z, 0, 0, 0);
	}
}
