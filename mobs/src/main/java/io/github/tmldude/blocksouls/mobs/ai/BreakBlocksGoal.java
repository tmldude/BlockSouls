package io.github.tmldude.blocksouls.mobs.ai;

import java.util.EnumSet;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import io.github.tmldude.blocksouls.common.CommonTags;
import io.github.tmldude.blocksouls.mobs.BlockSoulsMobs;
import io.github.tmldude.blocksouls.mobs.MobsConfig;
import io.github.tmldude.blocksouls.mobs.entity.WaveMob;
import io.github.tmldude.blocksouls.mobs.logic.BreakTime;

/**
 * Breaks a {@code #blocksouls:wave_breakable} block in the way to the target once the mob has been stuck for
 * {@code breaking.stuckTicks} (it isn't getting closer to its target). Shows crack progress over a time that
 * shrinks with the wave number. Respects the {@code mob_griefing} game rule; broken blocks drop as items.
 */
public class BreakBlocksGoal extends Goal {
	private final WaveMob mob;
	private @Nullable BlockPos breaking;
	private int progress;
	private int totalTicks;
	private int lastStage = -1;

	// Stuck detection: the best distance to the target seen recently, and how long it hasn't improved.
	private double bestDistance = Double.MAX_VALUE;
	private int lastProgressTick;
	private @Nullable LivingEntity trackedTarget;

	public BreakBlocksGoal(WaveMob mob) {
		this.mob = mob;
		setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
	}

	private static MobsConfig.Breaking config() {
		return BlockSoulsMobs.CONFIG.breaking;
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return true;
	}

	@Override
	public boolean canUse() {
		if (!updateStuck()) {
			return false;
		}
		BlockPos pos = findBlockInWay();
		if (pos == null) {
			return false;
		}
		breaking = pos;
		return true;
	}

	/** Updates the stuck timer; true once the mob has been stuck long enough. */
	private boolean updateStuck() {
		LivingEntity target = mob.getTarget();
		if (!mob.canBreakBlocks() || target == null || !(mob.level() instanceof ServerLevel level)
				|| !level.getGameRules().get(GameRules.MOB_GRIEFING)) {
			resetStuck(null);
			return false;
		}
		if (target != trackedTarget) {
			resetStuck(target);
		}
		double distance = mob.distanceTo(target);
		// canUse only runs every other tick, so time is measured with tickCount, not by counting calls.
		// Within arm's reach of the target there's nothing to break through.
		if (distance < bestDistance - 0.5 || mob.isWithinMeleeAttackRange(target)) {
			bestDistance = Math.min(bestDistance, distance);
			lastProgressTick = mob.tickCount;
		}
		return mob.tickCount - lastProgressTick >= config().stuckTicks;
	}

	private void resetStuck(@Nullable LivingEntity target) {
		trackedTarget = target;
		bestDistance = Double.MAX_VALUE;
		lastProgressTick = mob.tickCount;
	}

	/**
	 * The breakable block between the mob and its target at foot, body or head height, within reach. Looks
	 * along the line to the target, so diagonals and doors work too.
	 */
	private @Nullable BlockPos findBlockInWay() {
		LivingEntity target = mob.getTarget();
		if (target == null) {
			return null;
		}
		Vec3 toTarget = target.position().subtract(mob.position());
		Vec3 flat = new Vec3(toTarget.x, 0, toTarget.z);
		if (flat.lengthSqr() < 1.0E-4) {
			return null;
		}
		Vec3 dir = flat.normalize();
		double reach = config().reach + mob.getBbWidth() / 2;
		int levels = Math.min(3, Math.max(1, (int) Math.ceil(mob.getBbHeight())));

		for (int i = 0; i < levels; i++) {
			Vec3 from = mob.position().add(0, 0.5 + i, 0);
			Vec3 to = from.add(dir.scale(reach));
			BlockHitResult hit = mob.level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mob));
			if (hit.getType() == HitResult.Type.BLOCK && isBreakable(hit.getBlockPos())) {
				return hit.getBlockPos();
			}
		}
		return null;
	}

	private boolean isBreakable(BlockPos pos) {
		BlockState state = mob.level().getBlockState(pos);
		return !state.isAir() && state.is(CommonTags.WAVE_BREAKABLE) && state.getDestroySpeed(mob.level(), pos) >= 0;
	}

	@Override
	public void start() {
		progress = 0;
		lastStage = -1;
		totalTicks = BreakTime.ticks(mob.wave(), config().slowestSeconds, config().fastestSeconds, config().fastestWave, mob.breakSpeed());
		mob.getNavigation().stop();
	}

	@Override
	public boolean canContinueToUse() {
		return breaking != null && isBreakable(breaking) && mob.getTarget() != null && mob.canBreakBlocks()
				&& mob.level() instanceof ServerLevel level && level.getGameRules().get(GameRules.MOB_GRIEFING)
				&& breaking.distToCenterSqr(mob.position()) < 16;
	}

	@Override
	public void tick() {
		if (breaking == null || !(mob.level() instanceof ServerLevel level)) {
			return;
		}
		mob.getLookControl().setLookAt(breaking.getX() + 0.5, breaking.getY() + 0.5, breaking.getZ() + 0.5);
		progress++;
		if (progress % 10 == 0) {
			mob.swingForAttack(InteractionHand.MAIN_HAND);
			BlockState state = level.getBlockState(breaking);
			level.playSound(null, breaking, state.getSoundType().getHitSound(), SoundSource.HOSTILE, 0.8f, 0.6f);
		}
		int stage = BreakTime.crackStage(progress, totalTicks);
		if (stage != lastStage) {
			level.destroyBlockProgress(mob.getId(), breaking, stage);
			lastStage = stage;
		}
		if (progress >= totalTicks) {
			level.destroyBlockProgress(mob.getId(), breaking, -1);
			level.destroyBlock(breaking, true, mob);
			breaking = null;
			// Give the mob a moment to walk through before it counts as stuck again.
			resetStuck(mob.getTarget());
		}
	}

	@Override
	public void stop() {
		if (breaking != null && mob.level() instanceof ServerLevel level) {
			level.destroyBlockProgress(mob.getId(), breaking, -1);
		}
		breaking = null;
		progress = 0;
	}
}
