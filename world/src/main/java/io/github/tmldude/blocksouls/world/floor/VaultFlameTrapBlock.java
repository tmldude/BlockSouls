package io.github.tmldude.blocksouls.world.floor;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/**
 * The trapped hall's (floor 4, The Vault) flame grate: a floor block that cycles rest → smoke (the tell) → flame on
 * scheduled ticks ({@code trapRestTicks}, {@code trapSmokeTicks}, {@code trapFlameTicks}); whoever stands on it while it
 * burns catches fire. The event lays rows of them out of step so a wave of flame runs down the hall. Unbreakable.
 */
public class VaultFlameTrapBlock extends Block {
	public static final int REST = 0;
	public static final int SMOKE = 1;
	public static final int FLAME = 2;
	public static final IntegerProperty PHASE = IntegerProperty.create("phase", REST, FLAME);

	public VaultFlameTrapBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(PHASE, REST));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(PHASE);
	}

	/** How long a phase lasts (ticks, at least one). */
	static int duration(int phase) {
		VaultConfig config = VaultHazards.config();
		return Math.max(1, switch (phase) {
			case SMOKE -> config.trapSmokeTicks;
			case FLAME -> config.trapFlameTicks;
			default -> config.trapRestTicks;
		});
	}

	/** Starts a trap's cycle: it rests for {@code delay} ticks first (so a row can run behind the one before it). */
	public static void start(ServerLevel level, BlockPos pos, int delay) {
		BlockState state = level.getBlockState(pos);
		if (state.getBlock() instanceof VaultFlameTrapBlock trap) {
			level.scheduleTick(pos, trap, Math.max(1, delay));
		}
	}

	@Override
	protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		int next = (state.getValue(PHASE) + 1) % (FLAME + 1);
		level.setBlock(pos, state.setValue(PHASE, next), Block.UPDATE_CLIENTS);
		if (next == FLAME) {
			level.playSound(null, pos, SoundEvents.BLAZE_SHOOT, SoundSource.BLOCKS, 0.6F, 0.7F);
		}
		level.scheduleTick(pos, this, duration(next));
	}

	@Override
	public void stepOn(Level level, BlockPos pos, BlockState state, Entity entity) {
		if (state.getValue(PHASE) == FLAME && level instanceof ServerLevel serverLevel && entity instanceof LivingEntity living
				&& !living.fireImmune() && living.tickCount % 10 == 0) {
			VaultConfig config = VaultHazards.config();
			living.igniteForSeconds(Math.max(0, config.trapBurnSeconds));
			living.hurtServer(serverLevel, serverLevel.damageSources().hotFloor(), Math.max(0, config.trapDamage));
		}
		super.stepOn(level, pos, state, entity);
	}

	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		int phase = state.getValue(PHASE);
		if (phase == REST) {
			return;
		}
		for (int i = 0; i < (phase == FLAME ? 3 : 1); i++) {
			double x = pos.getX() + 0.15 + random.nextDouble() * 0.7;
			double z = pos.getZ() + 0.15 + random.nextDouble() * 0.7;
			level.addParticle(phase == FLAME ? ParticleTypes.FLAME : ParticleTypes.SMOKE, x, pos.getY() + 1.02, z, 0.0, phase == FLAME ? 0.08 : 0.03, 0.0);
		}
	}
}
